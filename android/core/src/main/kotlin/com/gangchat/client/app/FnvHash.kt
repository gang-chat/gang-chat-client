package com.gangchat.client.app

import java.math.BigInteger

/**
 * 64-bit FNV-1a over UTF-16 code units, as an unsigned value.
 *
 * Uses [BigInteger] (Dart `BigInt`) because web numbers can't hold 64-bit
 * integers exactly. Cache keys derived from it stay byte-for-byte identical to
 * the earlier native `int` implementation, so existing on-disk caches remain
 * valid.
 */
fun fnv1a64(value: String): BigInteger {
    return BigInteger.valueOf(_fnv1a64Bits(value)).and(_mask)
}

/**
 * [fnv1a64] read as a signed 64-bit integer, the way a native Dart `int`
 * holds it.
 */
fun fnv1a64Signed(value: String): BigInteger {
    return BigInteger.valueOf(_fnv1a64Bits(value))
}

private val _mask: BigInteger = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)

// 0xcbf29ce484222325 as a signed 64-bit value.
private const val _offsetBasis: Long = -0x340d631b7bdddcdbL
private const val _prime: Long = 0x100000001b3L

/** The hash in a [Long]; its wrapping multiply is the Dart `& mask`. */
private fun _fnv1a64Bits(value: String): Long {
    var hash = _offsetBasis
    for (unit in value) {
        hash = (hash xor unit.code.toLong()) * _prime
    }
    return hash
}
