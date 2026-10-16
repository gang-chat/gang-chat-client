package com.gangchat.client.foundation

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * Dart's `DateTime`: an instant (microseconds since the epoch) plus an
 * `isUtc` flag that decides whether the calendar getters ([year], [hour], ...)
 * read UTC or the device's local time zone.
 *
 * Semantics follow Dart exactly, so ported date math and formatting match:
 * - `DateTime(2024, 13, 1)` normalizes overflowing fields (Dart constructor).
 * - `==` compares the instant *and* [isUtc]; use [isAtSameMomentAs] for the
 *   instant only.
 * - [toString] / [toIso8601String] / [parse] use Dart's formats.
 */
class DateTime private constructor(
    val microsecondsSinceEpoch: Long,
    val isUtc: Boolean,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : Comparable<DateTime> {
    /** Dart `DateTime(year, [month, day, hour, minute, second, millisecond, microsecond])`, local time. */
    constructor(
        year: Int,
        month: Int = 1,
        day: Int = 1,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millisecond: Int = 0,
        microsecond: Int = 0,
    ) : this(
        fieldsToMicros(year, month, day, hour, minute, second, millisecond, microsecond, utc = false),
        false,
        Unit,
    )

    private val zoned: ZonedDateTime by lazy(LazyThreadSafetyMode.NONE) {
        val seconds = Math.floorDiv(microsecondsSinceEpoch, 1_000_000L)
        val micros = Math.floorMod(microsecondsSinceEpoch, 1_000_000L)
        Instant.ofEpochSecond(seconds, micros * 1000L).atZone(if (isUtc) ZoneOffset.UTC else ZoneId.systemDefault())
    }

    val millisecondsSinceEpoch: Long get() = Math.floorDiv(microsecondsSinceEpoch, 1000L)

    val year: Int get() = zoned.year

    val month: Int get() = zoned.monthValue

    val day: Int get() = zoned.dayOfMonth

    val hour: Int get() = zoned.hour

    val minute: Int get() = zoned.minute

    val second: Int get() = zoned.second

    val millisecond: Int get() = zoned.nano / 1_000_000

    val microsecond: Int get() = (zoned.nano / 1000) % 1000

    /** 1 = Monday ... 7 = Sunday, like Dart. */
    val weekday: Int get() = zoned.dayOfWeek.value

    val timeZoneOffset: Duration get() = (zoned.offset.totalSeconds.toLong() * 1_000_000L).microseconds

    val timeZoneName: String
        get() {
            if (isUtc) return "UTC"
            val zone = java.util.TimeZone.getTimeZone(zoned.zone)
            val daylight = zone.inDaylightTime(java.util.Date(millisecondsSinceEpoch))
            return zone.getDisplayName(daylight, java.util.TimeZone.SHORT)
        }

    fun toLocal(): DateTime = if (!isUtc) this else DateTime(microsecondsSinceEpoch, false, Unit)

    fun toUtc(): DateTime = if (isUtc) this else DateTime(microsecondsSinceEpoch, true, Unit)

    fun add(duration: Duration): DateTime = DateTime(microsecondsSinceEpoch + duration.inWholeMicroseconds, isUtc, Unit)

    fun subtract(duration: Duration): DateTime = DateTime(microsecondsSinceEpoch - duration.inWholeMicroseconds, isUtc, Unit)

    /** Dart `difference(other)`: `this - other`. */
    fun difference(other: DateTime): Duration = (microsecondsSinceEpoch - other.microsecondsSinceEpoch).microseconds

    fun isBefore(other: DateTime): Boolean = microsecondsSinceEpoch < other.microsecondsSinceEpoch

    fun isAfter(other: DateTime): Boolean = microsecondsSinceEpoch > other.microsecondsSinceEpoch

    fun isAtSameMomentAs(other: DateTime): Boolean = microsecondsSinceEpoch == other.microsecondsSinceEpoch

    override fun compareTo(other: DateTime): Int = microsecondsSinceEpoch.compareTo(other.microsecondsSinceEpoch)

    /** Dart 3's `DateTime.copyWith` (keeps [isUtc] unless given). */
    fun copyWith(
        year: Int? = null,
        month: Int? = null,
        day: Int? = null,
        hour: Int? = null,
        minute: Int? = null,
        second: Int? = null,
        millisecond: Int? = null,
        microsecond: Int? = null,
        isUtc: Boolean? = null,
    ): DateTime {
        val utc = isUtc ?: this.isUtc
        val micros =
            fieldsToMicros(
                year ?: this.year,
                month ?: this.month,
                day ?: this.day,
                hour ?: this.hour,
                minute ?: this.minute,
                second ?: this.second,
                millisecond ?: this.millisecond,
                microsecond ?: this.microsecond,
                utc,
            )
        return DateTime(micros, utc, Unit)
    }

    override fun equals(other: Any?): Boolean =
        other is DateTime && other.microsecondsSinceEpoch == microsecondsSinceEpoch && other.isUtc == isUtc

    override fun hashCode(): Int = (microsecondsSinceEpoch xor (microsecondsSinceEpoch ushr 30)).toInt()

    /** Dart format: `2024-01-02 03:04:05.000` (+ `Z` when UTC). */
    override fun toString(): String = format(separator = ' ')

    /** Dart format: `2024-01-02T03:04:05.000` (+ `Z` when UTC). */
    fun toIso8601String(): String = format(separator = 'T')

    private fun format(separator: Char): String {
        val y = year
        val yearText =
            when {
                y in 0..9999 -> y.toString().padStart(4, '0')
                separator == 'T' -> (if (y < 0) "-" else "+") + kotlin.math.abs(y).toString().padStart(6, '0')
                else -> (if (y < 0) "-" else "") + kotlin.math.abs(y).toString().padStart(4, '0')
            }
        val builder = StringBuilder()
        builder.append(yearText).append('-')
            .append(two(month)).append('-')
            .append(two(day)).append(separator)
            .append(two(hour)).append(':')
            .append(two(minute)).append(':')
            .append(two(second)).append('.')
            .append(millisecond.toString().padStart(3, '0'))
        if (microsecond != 0) builder.append(microsecond.toString().padStart(3, '0'))
        if (isUtc) builder.append('Z')
        return builder.toString()
    }

    companion object {
        /** Overridable clock for tests; returns microseconds since the epoch. */
        @Volatile
        var clock: () -> Long = {
            val now = Instant.now()
            now.epochSecond * 1_000_000L + now.nano / 1000L
        }

        /** Dart `DateTime.now()` (local). */
        fun now(): DateTime = DateTime(clock(), false, Unit)

        /** Dart `DateTime.timestamp()` (UTC now). */
        fun timestamp(): DateTime = DateTime(clock(), true, Unit)

        /** Dart `DateTime.utc(year, ...)`. */
        fun utc(
            year: Int,
            month: Int = 1,
            day: Int = 1,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            millisecond: Int = 0,
            microsecond: Int = 0,
        ): DateTime = DateTime(fieldsToMicros(year, month, day, hour, minute, second, millisecond, microsecond, utc = true), true, Unit)

        fun fromMillisecondsSinceEpoch(millisecondsSinceEpoch: Long, isUtc: Boolean = false): DateTime =
            DateTime(millisecondsSinceEpoch * 1000L, isUtc, Unit)

        fun fromMicrosecondsSinceEpoch(microsecondsSinceEpoch: Long, isUtc: Boolean = false): DateTime =
            DateTime(microsecondsSinceEpoch, isUtc, Unit)

        /** Dart `DateTime.parse(formattedString)`; throws [FormatException]. */
        fun parse(formattedString: String): DateTime =
            tryParse(formattedString) ?: throw FormatException("Invalid date format", formattedString)

        /** Dart `DateTime.tryParse(formattedString)`. */
        fun tryParse(formattedString: String): DateTime? {
            val match = parseFormat.matchEntire(formattedString) ?: return null
            val g = match.groupValues
            fun part(index: Int): Int = if (g[index].isEmpty()) 0 else g[index].toInt()
            val years = g[1].toInt()
            val month = g[2].toInt()
            val day = g[3].toInt()
            val hour = part(4)
            val minute = part(5)
            val second = part(6)
            var milli = 0
            var micro = 0
            val fraction = g[7]
            if (fraction.isNotEmpty()) {
                val digits = (fraction + "000000").substring(0, 6)
                var value = digits.toInt()
                if (fraction.length > 6 && fraction[6] >= '5') value += 1
                milli = value / 1000
                micro = value % 1000
            }
            val hasZone = g[8].isNotEmpty()
            if (!hasZone) {
                return DateTime(fieldsToMicros(years, month, day, hour, minute, second, milli, micro, utc = false), false, Unit)
            }
            var micros = fieldsToMicros(years, month, day, hour, minute, second, milli, micro, utc = true)
            val sign = g[9]
            if (sign.isNotEmpty()) {
                val offsetMinutes = g[10].toInt() * 60 + (if (g[11].isEmpty()) 0 else g[11].toInt())
                val signed = if (sign == "-") -offsetMinutes else offsetMinutes
                micros -= signed * 60_000_000L
            }
            return DateTime(micros, true, Unit)
        }

        // Dart's grammar from `DateTime.parse` (sdk/lib/core/date_time.dart).
        private val parseFormat =
            Regex(
                "([+-]?\\d{4,6})-?(\\d\\d)-?(\\d\\d)" +
                    "(?:[ T](\\d\\d)(?::?(\\d\\d)(?::?(\\d\\d)(?:[.,](\\d+))?)?)?" +
                    "( ?[zZ]| ?([-+])(\\d\\d)(?::?(\\d\\d))?)?)?",
            )

        private fun two(value: Int): String = value.toString().padStart(2, '0')

        private fun fieldsToMicros(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int,
            millisecond: Int,
            microsecond: Int,
            utc: Boolean,
        ): Long {
            // Normalize overflowing fields the way Dart does (each field may be
            // out of range and carries into the larger ones).
            val local =
                LocalDateTime.of(year, 1, 1, 0, 0)
                    .plusMonths((month - 1).toLong())
                    .plusDays((day - 1).toLong())
                    .plusHours(hour.toLong())
                    .plusMinutes(minute.toLong())
                    .plusSeconds(second.toLong())
            val zone: ZoneId = if (utc) ZoneOffset.UTC else ZoneId.systemDefault()
            val instant = local.atZone(zone).toInstant()
            return instant.epochSecond * 1_000_000L + instant.nano / 1000L + millisecond * 1000L + microsecond
        }
    }

    private fun two(value: Int): String = Companion.two(value)
}

/** `dateTime + duration` / `dateTime - duration`, Dart's `add` / `subtract`. */
operator fun DateTime.plus(duration: Duration): DateTime = add(duration)

operator fun DateTime.minus(duration: Duration): DateTime = subtract(duration)
