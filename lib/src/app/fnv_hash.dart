/// 64-bit FNV-1a over UTF-16 code units, as an unsigned value.
///
/// Uses [BigInt] because web numbers can't hold 64-bit integers exactly. Cache
/// keys derived from it stay byte-for-byte identical to the earlier native
/// `int` implementation, so existing on-disk caches remain valid.
BigInt fnv1a64(String value) {
  final mask = (BigInt.one << 64) - BigInt.one;
  final prime = BigInt.parse('100000001b3', radix: 16);
  var hash = BigInt.parse('cbf29ce484222325', radix: 16);
  for (final unit in value.codeUnits) {
    hash = ((hash ^ BigInt.from(unit)) * prime) & mask;
  }
  return hash;
}

/// [fnv1a64] read as a signed 64-bit integer, the way a native Dart `int`
/// holds it.
BigInt fnv1a64Signed(String value) {
  final hash = fnv1a64(value);
  return hash.bitLength == 64 ? hash - (BigInt.one << 64) : hash;
}
