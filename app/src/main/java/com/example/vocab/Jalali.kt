package com.example.vocab

import java.time.LocalDate

val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
val WEEK = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
val DAYS = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

fun fa(v: Any): String = v.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

data class JD(val y: Int, val m: Int, val d: Int, val dow: Int) // dow: 0 = Saturday

// ---- Gregorian -> Jalali (Borkowski/Birashk arithmetic algorithm; no hidden/ICU APIs) ----
private val G_D_M = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

private fun g2j(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    var jy: Int; var gy2: Int
    if (gy > 1600) { jy = 979; gy2 = gy - 1600 } else { jy = 0; gy2 = gy - 621 }
    val gyAdj = if (gm > 2) gy2 + 1 else gy2
    var days = 365 * gy2 + (gyAdj + 3) / 4 - (gyAdj + 99) / 100 + (gyAdj + 399) / 400 - 80 + gd + G_D_M[gm - 1]
    jy += 33 * (days / 12053); days %= 12053
    jy += 4 * (days / 1461); days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    val jm: Int; val jd: Int
    if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 } else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
    return Triple(jy, jm, jd)
}

private fun cmp3(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
    if (a.first != b.first) return a.first - b.first
    if (a.second != b.second) return a.second - b.second
    return a.third - b.third
}

/** Epoch day (days since 1970-01-01) of the given Jalali date, found by binary search against g2j (which is monotonic). */
private fun jalaaliToEpochDay(jy: Int, jm: Int, jd: Int): Long {
    var lo = -800000L; var hi = 800000L
    val target = Triple(jy, jm, jd)
    while (lo < hi) {
        val mid = (lo + hi) / 2
        val g = LocalDate.ofEpochDay(mid)
        val got = g2j(g.year, g.monthValue, g.dayOfMonth)
        if (cmp3(got, target) < 0) lo = mid + 1 else hi = mid
    }
    return lo
}

fun jal(day: Long): JD {
    val g = LocalDate.ofEpochDay(day)
    val (jy, jm, jd) = g2j(g.year, g.monthValue, g.dayOfMonth)
    val dow = Math.floorMod(day + 5, 7L).toInt() // epoch day 0 (1970-01-01) was a Thursday = index 5
    return JD(jy, jm, jd, dow)
}

/** first epoch day of the month, its length in days, weekday column (0 = Saturday) of the 1st */
fun jmonth(y: Int, m: Int): Triple<Long, Int, Int> {
    val first = jalaaliToEpochDay(y, m, 1)
    val (ny, nm) = if (m == 12) (y + 1) to 1 else y to (m + 1)
    val next = jalaaliToEpochDay(ny, nm, 1)
    val dow = Math.floorMod(first + 5, 7L).toInt()
    return Triple(first, (next - first).toInt(), dow)
}
