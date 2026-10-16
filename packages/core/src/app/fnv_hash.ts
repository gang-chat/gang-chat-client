const _mask = (1n << 64n) - 1n;
const _prime = 0x100000001b3n;
const _offsetBasis = 0xcbf29ce484222325n;

/**
 * 64-bit FNV-1a over UTF-16 code units, as an unsigned value.
 *
 * Uses `bigint` because JS numbers can't hold 64-bit integers exactly. Cache
 * keys derived from it stay byte-for-byte identical to the earlier native
 * `int` implementation, so existing on-disk caches remain valid.
 */
export function fnv1a64(value: string): bigint {
  let hash = _offsetBasis;
  for (let index = 0; index < value.length; index++) {
    hash = ((hash ^ BigInt(value.charCodeAt(index))) * _prime) & _mask;
  }
  return hash;
}

/**
 * [fnv1a64] read as a signed 64-bit integer, the way a native Dart `int`
 * holds it.
 */
export function fnv1a64Signed(value: string): bigint {
  return BigInt.asIntN(64, fnv1a64(value));
}
