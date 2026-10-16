/**
 * Minimal ZIP reader, the stand-in for `package:archive`'s `ZipDecoder` used
 * by the sticker uploader. Pure TypeScript (no `DecompressionStream`, no
 * Node `zlib`) so the same code runs in Electron, Node tests and Hermes.
 *
 * Supports stored (0) and deflated (8) entries, ZIP64 sizes/offsets, and
 * reads entry contents lazily like `ArchiveFile.readBytes()`. Encrypted or
 * otherwise-compressed entries read as `null`.
 */

export class ZipFormatException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'FormatException';
  }

  override toString(): string {
    return `FormatException: ${this.message}`;
  }
}

export interface ZipArchiveFile {
  /** Path inside the archive, `/`-separated as stored. */
  readonly name: string;
  readonly isFile: boolean;
  /** Uncompressed size in bytes. */
  readonly size: number;
  readonly compressionMethod: number;
  /** Decompresses the entry; `null` for unsupported (encrypted / exotic) entries. */
  readBytes(): Uint8Array | null;
}

export interface ZipArchive {
  readonly files: ZipArchiveFile[];
}

const EOCD_SIGNATURE = 0x06054b50;
const ZIP64_EOCD_LOCATOR_SIGNATURE = 0x07064b50;
const ZIP64_EOCD_SIGNATURE = 0x06064b50;
const CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
const LOCAL_HEADER_SIGNATURE = 0x04034b50;

const utf8 = new TextDecoder('utf-8');

export class ZipDecoder {
  decodeBytes(bytes: Uint8Array): ZipArchive {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const eocd = findEndOfCentralDirectory(view);
    let entryCount = view.getUint16(eocd + 10, true);
    let directoryOffset = view.getUint32(eocd + 16, true);

    if (entryCount === 0xffff || directoryOffset === 0xffffffff) {
      const locator = eocd - 20;
      if (locator >= 0 && view.getUint32(locator, true) === ZIP64_EOCD_LOCATOR_SIGNATURE) {
        const zip64Offset = readUint64(view, locator + 8);
        if (zip64Offset + 56 > bytes.length || view.getUint32(zip64Offset, true) !== ZIP64_EOCD_SIGNATURE) {
          throw new ZipFormatException('Invalid ZIP64 end of central directory');
        }
        entryCount = readUint64(view, zip64Offset + 32);
        directoryOffset = readUint64(view, zip64Offset + 48);
      }
    }

    const files: ZipArchiveFile[] = [];
    let offset = directoryOffset;
    for (let index = 0; index < entryCount; index += 1) {
      if (offset + 46 > bytes.length || view.getUint32(offset, true) !== CENTRAL_DIRECTORY_SIGNATURE) {
        throw new ZipFormatException('Invalid central directory entry');
      }
      const versionMadeBy = view.getUint16(offset + 4, true);
      const flags = view.getUint16(offset + 8, true);
      const method = view.getUint16(offset + 10, true);
      let compressedSize = view.getUint32(offset + 20, true);
      let uncompressedSize = view.getUint32(offset + 24, true);
      const nameLength = view.getUint16(offset + 28, true);
      const extraLength = view.getUint16(offset + 30, true);
      const commentLength = view.getUint16(offset + 32, true);
      const externalAttributes = view.getUint32(offset + 38, true);
      let localHeaderOffset = view.getUint32(offset + 42, true);
      const nameStart = offset + 46;
      const name = utf8.decode(bytes.subarray(nameStart, nameStart + nameLength));

      // ZIP64 extended information: only the fields saturated in the header
      // are present, in this fixed order.
      let extra = nameStart + nameLength;
      const extraEnd = extra + extraLength;
      while (extra + 4 <= extraEnd) {
        const headerId = view.getUint16(extra, true);
        const size = view.getUint16(extra + 2, true);
        if (headerId === 0x0001) {
          let field = extra + 4;
          if (uncompressedSize === 0xffffffff && field + 8 <= extra + 4 + size) {
            uncompressedSize = readUint64(view, field);
            field += 8;
          }
          if (compressedSize === 0xffffffff && field + 8 <= extra + 4 + size) {
            compressedSize = readUint64(view, field);
            field += 8;
          }
          if (localHeaderOffset === 0xffffffff && field + 8 <= extra + 4 + size) {
            localHeaderOffset = readUint64(view, field);
          }
        }
        extra += 4 + size;
      }

      // Same rule as package:archive: Unix-made entries use the mode bits,
      // everything else treats a trailing slash as a directory.
      let isFile: boolean;
      if (versionMadeBy >> 8 === 3) {
        const fileType = (externalAttributes >>> 16) & 0xf000;
        isFile = fileType === 0x8000 || fileType === 0x0000;
        if (name.endsWith('/')) isFile = false;
      } else {
        isFile = !name.endsWith('/');
      }

      const encrypted = (flags & 0x1) !== 0;
      const entrySize = uncompressedSize;
      const entryCompressedSize = compressedSize;
      const entryLocalOffset = localHeaderOffset;
      files.push({
        name,
        isFile,
        size: entrySize,
        compressionMethod: method,
        readBytes: () => {
          if (encrypted) return null;
          if (method !== 0 && method !== 8) return null;
          const data = localEntryData(bytes, view, entryLocalOffset, entryCompressedSize);
          if (method === 0) return data.slice();
          return inflateRaw(data, entrySize);
        },
      });

      offset = extraEnd + commentLength;
    }
    return { files };
  }
}

function findEndOfCentralDirectory(view: DataView): number {
  const minimum = Math.max(0, view.byteLength - 22 - 0xffff);
  for (let offset = view.byteLength - 22; offset >= minimum; offset -= 1) {
    if (view.getUint32(offset, true) === EOCD_SIGNATURE) return offset;
  }
  throw new ZipFormatException('Could not find End of Central Directory Record');
}

function localEntryData(bytes: Uint8Array, view: DataView, offset: number, compressedSize: number): Uint8Array {
  if (offset + 30 > bytes.length || view.getUint32(offset, true) !== LOCAL_HEADER_SIGNATURE) {
    throw new ZipFormatException('Invalid local file header');
  }
  const nameLength = view.getUint16(offset + 26, true);
  const extraLength = view.getUint16(offset + 28, true);
  const start = offset + 30 + nameLength + extraLength;
  const end = start + compressedSize;
  if (end > bytes.length) throw new ZipFormatException('Truncated ZIP entry');
  return bytes.subarray(start, end);
}

function readUint64(view: DataView, offset: number): number {
  const low = view.getUint32(offset, true);
  const high = view.getUint32(offset + 4, true);
  return high * 0x100000000 + low;
}

// ---------------------------------------------------------------------------
// Raw DEFLATE (RFC 1951) decoder.

const LENGTH_BASE = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258];
const LENGTH_EXTRA = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0];
const DISTANCE_BASE = [
  1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
  8193, 12289, 16385, 24577,
];
const DISTANCE_EXTRA = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13];
const CODE_LENGTH_ORDER = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];

interface HuffmanTable {
  /** Indexed by the next `maxLength` (LSB-first) bits: `(symbol << 4) | length`, 0 = invalid. */
  readonly table: Int32Array;
  readonly maxLength: number;
}

function buildHuffman(lengths: ArrayLike<number>, count: number): HuffmanTable {
  const lengthCount = new Uint16Array(16);
  let maxLength = 0;
  for (let symbol = 0; symbol < count; symbol += 1) {
    const length = lengths[symbol]!;
    if (length === 0) continue;
    lengthCount[length] += 1;
    if (length > maxLength) maxLength = length;
  }
  const nextCode = new Uint16Array(16);
  let code = 0;
  for (let bits = 1; bits <= 15; bits += 1) {
    code = (code + lengthCount[bits - 1]!) << 1;
    nextCode[bits] = code;
  }
  if (maxLength === 0) maxLength = 1;
  const size = 1 << maxLength;
  const table = new Int32Array(size);
  for (let symbol = 0; symbol < count; symbol += 1) {
    const length = lengths[symbol]!;
    if (length === 0) continue;
    let value = nextCode[length]!;
    nextCode[length] = value + 1;
    let reversed = 0;
    for (let i = 0; i < length; i += 1) {
      reversed = (reversed << 1) | (value & 1);
      value >>= 1;
    }
    const entry = (symbol << 4) | length;
    for (let index = reversed; index < size; index += 1 << length) table[index] = entry;
  }
  return { table, maxLength };
}

let fixedLiteralTable: HuffmanTable | null = null;
let fixedDistanceTable: HuffmanTable | null = null;

function fixedTables(): [HuffmanTable, HuffmanTable] {
  if (fixedLiteralTable == null || fixedDistanceTable == null) {
    const literal = new Uint8Array(288);
    literal.fill(8, 0, 144);
    literal.fill(9, 144, 256);
    literal.fill(7, 256, 280);
    literal.fill(8, 280, 288);
    fixedLiteralTable = buildHuffman(literal, 288);
    fixedDistanceTable = buildHuffman(new Uint8Array(30).fill(5), 30);
  }
  return [fixedLiteralTable, fixedDistanceTable];
}

class InflateState {
  private position = 0;
  private bitBuffer = 0;
  private bitCount = 0;
  output: Uint8Array;
  outputLength = 0;

  constructor(
    private readonly input: Uint8Array,
    expectedSize: number,
  ) {
    this.output = new Uint8Array(Math.max(expectedSize, 1024));
  }

  private fill(bits: number): void {
    while (this.bitCount < bits) {
      if (this.position >= this.input.length + 4) {
        throw new ZipFormatException('Unexpected end of deflate stream');
      }
      // Bits past the end read as zero so a final short code can be peeked.
      const byte = this.position < this.input.length ? this.input[this.position]! : 0;
      this.position += 1;
      this.bitBuffer |= byte << this.bitCount;
      this.bitCount += 8;
    }
  }

  bits(count: number): number {
    if (count === 0) return 0;
    this.fill(count);
    const value = this.bitBuffer & ((1 << count) - 1);
    this.bitBuffer >>>= count;
    this.bitCount -= count;
    return value;
  }

  decode(huffman: HuffmanTable): number {
    this.fill(huffman.maxLength);
    const entry = huffman.table[this.bitBuffer & ((1 << huffman.maxLength) - 1)]!;
    const length = entry & 15;
    if (length === 0) throw new ZipFormatException('Invalid deflate Huffman code');
    this.bitBuffer >>>= length;
    this.bitCount -= length;
    return entry >> 4;
  }

  private ensure(extra: number): void {
    const needed = this.outputLength + extra;
    if (needed <= this.output.length) return;
    let capacity = this.output.length * 2;
    while (capacity < needed) capacity *= 2;
    const grown = new Uint8Array(capacity);
    grown.set(this.output.subarray(0, this.outputLength));
    this.output = grown;
  }

  stored(): void {
    // Skip to the byte boundary, then read LEN / NLEN.
    const skip = this.bitCount & 7;
    this.bitBuffer >>>= skip;
    this.bitCount -= skip;
    const length = this.bits(16);
    const inverse = this.bits(16);
    if ((length ^ 0xffff) !== inverse) throw new ZipFormatException('Invalid stored block length');
    this.ensure(length);
    let remaining = length;
    while (remaining > 0 && this.bitCount >= 8) {
      this.output[this.outputLength++] = this.bits(8);
      remaining -= 1;
    }
    if (this.position + remaining > this.input.length) throw new ZipFormatException('Truncated stored block');
    this.output.set(this.input.subarray(this.position, this.position + remaining), this.outputLength);
    this.outputLength += remaining;
    this.position += remaining;
  }

  codes(literal: HuffmanTable, distance: HuffmanTable): void {
    for (;;) {
      const symbol = this.decode(literal);
      if (symbol < 256) {
        this.ensure(1);
        this.output[this.outputLength++] = symbol;
        continue;
      }
      if (symbol === 256) return;
      const lengthIndex = symbol - 257;
      if (lengthIndex >= 29) throw new ZipFormatException('Invalid deflate length code');
      const length = LENGTH_BASE[lengthIndex]! + this.bits(LENGTH_EXTRA[lengthIndex]!);
      const distanceSymbol = this.decode(distance);
      if (distanceSymbol >= 30) throw new ZipFormatException('Invalid deflate distance code');
      const back = DISTANCE_BASE[distanceSymbol]! + this.bits(DISTANCE_EXTRA[distanceSymbol]!);
      if (back > this.outputLength) throw new ZipFormatException('Invalid deflate distance');
      this.ensure(length);
      const output = this.output;
      let from = this.outputLength - back;
      for (let i = 0; i < length; i += 1) output[this.outputLength++] = output[from++]!;
    }
  }

  dynamicTables(): [HuffmanTable, HuffmanTable] {
    const literalCount = this.bits(5) + 257;
    const distanceCount = this.bits(5) + 1;
    const codeLengthCount = this.bits(4) + 4;
    const codeLengthLengths = new Uint8Array(19);
    for (let i = 0; i < codeLengthCount; i += 1) {
      codeLengthLengths[CODE_LENGTH_ORDER[i]!] = this.bits(3);
    }
    const codeLengthTable = buildHuffman(codeLengthLengths, 19);
    const lengths = new Uint8Array(literalCount + distanceCount);
    let index = 0;
    while (index < lengths.length) {
      const symbol = this.decode(codeLengthTable);
      if (symbol < 16) {
        lengths[index++] = symbol;
        continue;
      }
      let repeat: number;
      let value = 0;
      if (symbol === 16) {
        if (index === 0) throw new ZipFormatException('Invalid deflate repeat');
        value = lengths[index - 1]!;
        repeat = 3 + this.bits(2);
      } else if (symbol === 17) {
        repeat = 3 + this.bits(3);
      } else {
        repeat = 11 + this.bits(7);
      }
      if (index + repeat > lengths.length) throw new ZipFormatException('Invalid deflate code lengths');
      lengths.fill(value, index, index + repeat);
      index += repeat;
    }
    if (lengths[256] === 0) throw new ZipFormatException('Missing deflate end-of-block code');
    return [
      buildHuffman(lengths.subarray(0, literalCount), literalCount),
      buildHuffman(lengths.subarray(literalCount), distanceCount),
    ];
  }
}

/** Decompresses a raw DEFLATE stream (no zlib/gzip wrapper). */
export function inflateRaw(input: Uint8Array, expectedSize = 0): Uint8Array {
  const state = new InflateState(input, expectedSize);
  let last = false;
  while (!last) {
    last = state.bits(1) === 1;
    const type = state.bits(2);
    if (type === 0) {
      state.stored();
    } else if (type === 1) {
      const [literal, distance] = fixedTables();
      state.codes(literal, distance);
    } else if (type === 2) {
      const [literal, distance] = state.dynamicTables();
      state.codes(literal, distance);
    } else {
      throw new ZipFormatException('Invalid deflate block type');
    }
  }
  return state.output.slice(0, state.outputLength);
}
