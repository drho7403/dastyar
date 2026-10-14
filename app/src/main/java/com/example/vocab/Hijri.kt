package com.example.vocab

val HIJRI_MONTHS = listOf(
    "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
    "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
)
val GREG_MONTHS_FA = listOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
)

// Tabular/civil Islamic calendar (arithmetic approximation; calibrated so 2026-09-30 = 18 Rabi' al-Thani 1448,
// matching commonly published references — actual local moon-sighting dates can differ by about a day).
private fun gregorianToJdn(y: Int, m: Int, d: Int): Int {
    val a = (14 - m) / 12
    val y2 = y + 4800 - a
    val m2 = m + 12 * a - 3
    return d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045
}

fun gregorianToHijri(y: Int, m: Int, d: Int): Triple<Int, Int, Int> {
    val epoch = 1948439
    var jdn = gregorianToJdn(y, m, d) - epoch + 10632
    val n = (jdn - 1) / 10631
    jdn = jdn - 10631 * n + 354
    val j = ((10985 - jdn) / 5316) * ((50 * jdn) / 17719) + (jdn / 5670) * ((43 * jdn) / 15238)
    jdn = jdn - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
    val hm = (24 * jdn) / 709
    val hd = jdn - (709 * hm) / 24
    val hy = 30 * n + j - 30
    return Triple(hy, hm, hd)
}
