package com.theeclecticwitch.powertothepeople.ui

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

/** Formatting that works the same on every platform - common Kotlin has no String.format. */
object Format {
    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** 40102185696865 -> "40,102,185,696,865" */
    fun commas(value: Long): String {
        val digits = abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    /** Whole dollars with commas: "$40,102,185,696,865". */
    fun dollars(value: Double): String {
        val rounded = value.roundToLong()
        return (if (rounded < 0) "-$" else "$") + commas(abs(rounded))
    }

    /** 1965591017473.53 -> "$1.97 trillion"; smaller amounts in billions or millions. */
    fun dollarsShort(value: Double): String {
        val a = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            a >= 1e12 -> sign + "$" + decimals(a / 1e12, 2) + " trillion"
            a >= 1e9 -> sign + "$" + decimals(a / 1e9, 1) + " billion"
            a >= 1e6 -> sign + "$" + decimals(a / 1e6, 1) + " million"
            else -> dollars(value)
        }
    }

    fun decimals(value: Double, places: Int): String {
        var factor = 1L
        repeat(places) { factor *= 10 }
        val scaled = (value * factor).roundToLong()
        val whole = scaled / factor
        val frac = abs(scaled % factor).toString().padStart(places, '0')
        val sign = if (scaled < 0 && whole == 0L) "-" else ""
        return if (places == 0) whole.toString() else "$sign$whole.$frac"
    }

    /** "2026-09-28" -> "September 28, 2026". Anything unparseable is returned as it came. */
    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            date(LocalDate.parse(iso.take(10)))
        } catch (e: Exception) {
            iso
        }
    }

    fun date(d: LocalDate): String = "${months[d.month.ordinal]} ${d.day}, ${d.year}"

    /** "2026-09-28" -> "Sep 28" */
    fun shortDate(d: LocalDate): String = "${months[d.month.ordinal].take(3)} ${d.day}"

    fun monthName(month: Int): String = months[month - 1]

    /** 1 -> "1st", 2 -> "2nd", 11 -> "11th", 23 -> "23rd". */
    fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
