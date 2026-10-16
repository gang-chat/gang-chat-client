package com.gangchat.client.foundation

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeTest {
    @Test
    fun parsesUtcAndOffsetsLikeDart() {
        val utc = DateTime.parse("2024-03-05T06:07:08.123456Z")
        assertTrue(utc.isUtc)
        assertEquals(2024, utc.year)
        assertEquals(3, utc.month)
        assertEquals(5, utc.day)
        assertEquals(123, utc.millisecond)
        assertEquals(456, utc.microsecond)
        assertEquals("2024-03-05T06:07:08.123456Z", utc.toIso8601String())

        val offset = DateTime.parse("2024-03-05T08:07:08+02:00")
        assertTrue(offset.isUtc)
        assertEquals(6, offset.hour)
        assertEquals("2024-03-05 06:07:08.000Z", offset.toString())

        val compact = DateTime.parse("20240305T060708Z")
        assertTrue(compact.isAtSameMomentAs(DateTime.utc(2024, 3, 5, 6, 7, 8)))
    }

    @Test
    fun parsesLocalWithoutZone() {
        val local = DateTime.parse("2024-03-05 06:07:08")
        assertFalse(local.isUtc)
        assertEquals(6, local.hour)
        assertEquals("2024-03-05 06:07:08.000", local.toString())
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(DateTime.tryParse("not a date"))
        assertNull(DateTime.tryParse(""))
    }

    @Test
    fun constructorNormalizesOverflowLikeDart() {
        assertEquals(DateTime(2025, 1, 1), DateTime(2024, 13, 1))
        assertEquals(DateTime(2024, 3, 1), DateTime(2024, 2, 30))
        assertEquals(DateTime(2023, 12, 31), DateTime(2024, 1, 0))
    }

    @Test
    fun equalityIncludesUtcFlag() {
        val utc = DateTime.utc(2024, 1, 1)
        val local = utc.toLocal()
        assertTrue(utc.isAtSameMomentAs(local))
        assertFalse(utc == local)
        assertEquals(utc, local.toUtc())
    }

    @Test
    fun arithmeticAndComparisons() {
        val start = DateTime.utc(2024, 1, 31, 23)
        val later = start.add(2.hours)
        assertEquals(DateTime.utc(2024, 2, 1, 1), later)
        assertEquals(2.hours, later.difference(start))
        assertTrue(start.isBefore(later))
        assertEquals(DateTime.utc(2024, 1, 30, 23), start.subtract(1.days))
        assertEquals(3, DateTime.utc(2024, 1, 31).weekday)
    }

    @Test
    fun millisecondsRoundTrip() {
        val value = DateTime.fromMillisecondsSinceEpoch(1_700_000_000_123, isUtc = true)
        assertEquals(1_700_000_000_123, value.millisecondsSinceEpoch)
        assertEquals("2023-11-14T22:13:20.123Z", value.toIso8601String())
    }
}
