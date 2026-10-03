package com.example.vocab

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun today() = LocalDate.now().toEpochDay()

// k = number of successful reviews so far; due = epoch day of next review
data class Word(
    val id: String, val text: String, val meaning: String, val audio: String?, val image: String?,
    val k: Int = 0, val due: Long, val failed: Boolean = false, val streak: Int = 0, val lastOk: Long = -1
)
data class Routine(val id: String, val name: String)
data class Task(val id: String, val title: String, val day: Long, val done: Boolean = false)
data class Bill(val id: String, val amount: Long, val day: Long, val seriesId: String? = null, val done: Boolean = false, val kind: String = "قسط", val title: String = "")
data class State(
    val words: List<Word>, val routines: List<Routine>, val ticks: Map<String, Set<Long>>, val tasks: List<Task> = emptyList(),
    val shifts: Map<Long, Set<String>> = emptyMap(), val shiftImages: Map<String, String> = emptyMap(), val bills: List<Bill> = emptyList()
)

fun monthKey(y: Int, m: Int) = "$y-$m"

fun shiftLabel(code: String) = when (code) { "1" -> "تایم صبح (۸ تا ۲۰)"; "3" -> "تایم عصر (۱۴ تا ۲)"; else -> "تایم شب (۲۰ تا ۸)" }
fun shiftShort(code: String) = when (code) { "1" -> "صبح"; "3" -> "عصر"; else -> "شب" }
fun addMonths(day: Long, months: Int): Long = LocalDate.ofEpochDay(day).plusMonths(months.toLong()).toEpochDay()

fun Word.answer(ok: Boolean, t: Long): Word {
    if (!ok) return copy(failed = true, streak = 0, due = t + 1)
    // after a ✗ two consecutive ✓ days are needed; otherwise one ✓ advances
    val s = if (failed) (if (lastOk == t - 1) streak + 1 else 1) else 2
    return if (s >= 2) copy(k = k + 1, failed = false, streak = 0, lastOk = t, due = t + (1L shl minOf(k + 1, 20)))
    else copy(streak = s, lastOk = t, due = t + 1)
}

fun streak(days: Set<Long>, t: Long): Int {
    var d = if (t in days) t else t - 1
    var n = 0
    while (d in days) { n++; d-- }
    return n
}

object Store {
    fun sp(c: Context) = c.getSharedPreferences("vocab", 0)
    fun file(c: Context) = File(c.filesDir, "data.json")
    fun load(c: Context): State {
        val f = file(c)
        val o = if (f.exists()) JSONObject(f.readText()) else JSONObject()
        val wa = o.optJSONArray("w") ?: JSONArray()
        val words = List(wa.length()) {
            val x = wa.getJSONObject(it)
            Word(x.getString("id"), x.getString("t"), x.getString("m"),
                x.optString("a").ifEmpty { null }, x.optString("i").ifEmpty { null },
                x.optInt("k"), x.getLong("d"), x.optBoolean("f"), x.optInt("s"), x.optLong("o", -1))
        }
        val ra = o.optJSONArray("r") ?: JSONArray()
        var routines = List(ra.length()) { Routine(ra.getJSONObject(it).getString("id"), ra.getJSONObject(it).getString("n")) }
        if (routines.none { it.id == "review" }) routines = listOf(Routine("review", "مرور کلمات")) + routines
        val tk = o.optJSONObject("k") ?: JSONObject()
        val ticks = tk.keys().asSequence().toList().associateWith { key ->
            val a = tk.getJSONArray(key); (0 until a.length()).map { a.getLong(it) }.toSet()
        }
        val ta = o.optJSONArray("tasks") ?: JSONArray()
        val tasks = List(ta.length()) { val x = ta.getJSONObject(it); Task(x.getString("id"), x.getString("t"), x.getLong("d"), x.optBoolean("x")) }
        val sh = o.optJSONObject("shifts") ?: JSONObject()
        val shifts = sh.keys().asSequence().toList().associate { key ->
            val a = sh.getJSONArray(key); key.toLong() to (0 until a.length()).map { a.getString(it) }.toSet()
        }
        val shiftImages = (o.optJSONObject("shiftImages") ?: JSONObject()).let { so ->
            so.keys().asSequence().toList().associateWith { k -> so.getString(k) }
        }
        val ba = o.optJSONArray("bills") ?: JSONArray()
        val bills = List(ba.length()) {
            val x = ba.getJSONObject(it)
            Bill(x.getString("id"), x.getLong("am"), x.getLong("d"), x.optString("sid").ifEmpty { null }, x.optBoolean("x"),
                x.optString("kind").ifEmpty { "قسط" }, x.optString("title"))
        }
        return State(words, routines, ticks, tasks, shifts, shiftImages, bills)
    }
    fun save(c: Context, s: State) {
        val o = JSONObject()
        o.put("w", JSONArray().apply {
            s.words.forEach {
                put(JSONObject().put("id", it.id).put("t", it.text).put("m", it.meaning).put("a", it.audio ?: "")
                    .put("i", it.image ?: "").put("k", it.k).put("d", it.due).put("f", it.failed)
                    .put("s", it.streak).put("o", it.lastOk))
            }
        })
        o.put("r", JSONArray().apply { s.routines.forEach { put(JSONObject().put("id", it.id).put("n", it.name)) } })
        o.put("k", JSONObject().apply { s.ticks.forEach { (k, v) -> put(k, JSONArray(v.toList())) } })
        o.put("tasks", JSONArray().apply { s.tasks.forEach { put(JSONObject().put("id", it.id).put("t", it.title).put("d", it.day).put("x", it.done)) } })
        o.put("shifts", JSONObject().apply { s.shifts.forEach { (k, v) -> put(k.toString(), JSONArray(v.toList())) } })
        o.put("shiftImages", JSONObject().apply { s.shiftImages.forEach { (k, v) -> put(k, v) } })
        o.put("bills", JSONArray().apply { s.bills.forEach { put(JSONObject().put("id", it.id).put("am", it.amount).put("d", it.day).put("sid", it.seriesId ?: "").put("x", it.done).put("kind", it.kind).put("title", it.title)) } })
        file(c).writeText(o.toString())
        updateWidgets(c)
    }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { updateWidgets(c); checkTomorrowReminders(c) }
}

fun checkTomorrowReminders(c: Context) {
    val st = Store.load(c); val tmr = today() + 1
    val nm = c.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("r", "یادآوری", NotificationManager.IMPORTANCE_DEFAULT))
    val shiftCodes = st.shifts[tmr] ?: emptySet()
    if (shiftCodes.isNotEmpty()) {
        val txt = shiftCodes.joinToString("، ") { shiftLabel(it) }
        nm.notify(6, Notification.Builder(c, "r").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("فردا شیفت داری").setContentText(txt)
            .setContentIntent(PendingIntent.getActivity(c, 6, Intent(c, MainActivity::class.java).putExtra("tab", 3),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true).build())
    }
    val dueBills = st.bills.filter { it.day == tmr && !it.done }
    if (dueBills.isNotEmpty()) {
        val txt = dueBills.joinToString("، ") { "${it.kind}${if (it.title.isNotBlank()) " " + it.title else ""} ${fa(it.amount)} تومان" }
        nm.notify(7, Notification.Builder(c, "r").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("فردا چک یا قسط داری").setContentText(txt)
            .setContentIntent(PendingIntent.getActivity(c, 7, Intent(c, MainActivity::class.java).putExtra("tab", 4),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true).build())
    }
    val dueTasks = st.tasks.filter { it.day == tmr && !it.done }
    if (dueTasks.isNotEmpty()) {
        val txt = dueTasks.joinToString("، ") { it.title }
        nm.notify(8, Notification.Builder(c, "r").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("فردا تسک داری").setContentText(txt)
            .setContentIntent(PendingIntent.getActivity(c, 8, Intent(c, MainActivity::class.java).putExtra("tab", 2),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true).build())
    }
}

private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

fun ringBitmap(c: Context, progress: Float, sizeDp: Int, track: Int, accent: Int): Bitmap {
    val size = dp(c, sizeDp)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val stroke = size * 0.1f
    val rect = android.graphics.RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
    val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke; color = track; strokeCap = android.graphics.Paint.Cap.ROUND
    }
    cv.drawArc(rect, 0f, 360f, false, trackPaint)
    val accentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke; color = accent; strokeCap = android.graphics.Paint.Cap.ROUND
    }
    cv.drawArc(rect, -90f, 360f * progress.coerceIn(0.02f, 1f), false, accentPaint)
    return bmp
}

private fun dateStrings(day: Long): Pair<String, String> {
    val j = jal(day); val g = LocalDate.ofEpochDay(day)
    val (hy, hm, hd) = gregorianToHijri(g.year, g.monthValue, g.dayOfMonth)
    val weekday = "${DAYS[j.dow]} ${fa(j.d)} ${MONTHS[j.m - 1]} ${fa(j.y)}"
    val sub = "${fa(hd)} ${HIJRI_MONTHS[hm - 1]} ${fa(hy)} - ${fa(g.dayOfMonth)} ${GREG_MONTHS_FA[g.monthValue - 1]} ${fa(g.year)}"
    return weekday to sub
}

fun updateDateNotification(c: Context) {
    try {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("date", "تاریخ روز", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val t = today(); val j = jal(t)
        val (_, monthLen, _) = jmonth(j.y, j.m)
        val (weekday, sub) = dateStrings(t)
        val rv = RemoteViews(c.packageName, R.layout.notification_date)
        rv.setTextViewText(R.id.nd_weekday, weekday)
        rv.setTextViewText(R.id.nd_greg, sub)
        rv.setTextViewText(R.id.nd_day, fa(j.d))
        rv.setImageViewBitmap(R.id.nd_ring, ringBitmap(c, j.d.toFloat() / monthLen.toFloat(), 40, 0xFFD8E6E6.toInt(), 0xFF2E7D7D.toInt()))
        val pi = PendingIntent.getActivity(c, 4, Intent(c, MainActivity::class.java).putExtra("tab", 2).setData(Uri.parse("vocabapp://notif")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Experiment: some OEM skins (MIUI included) render a media-session-backed notification without the
        // usual small-icon chip and without a swipe-to-dismiss/snooze affordance — which matches what the
        // reference app's calendar notification looks like. DecoratedMediaCustomViewStyle is the style built
        // for "custom content view + a media session" combination; a silent dummy session (nothing is ever
        // actually played) is enough to opt into that rendering. Untested on a real device — if it doesn't
        // help, this is easy to drop back to the plain custom-view notification.
        val session = android.media.session.MediaSession(c, "vocab_date").apply {
            setPlaybackState(android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PAUSED, 0, 1f).build())
            isActive = true
        }
        val n = Notification.Builder(c, "date")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setCustomContentView(rv)
            .setStyle(Notification.DecoratedMediaCustomViewStyle().setMediaSession(session.sessionToken))
            .setContentIntent(pi)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(2, n)
    } catch (e: Exception) { /* never crash the caller over the notification */ }
}

fun updateWidgets(c: Context) {
    updateDateNotification(c)
}

fun scheduleMidnight(c: Context) {
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0)
    }
    val pi = PendingIntent.getBroadcast(c, 10, Intent(c, MidnightReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    c.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.RTC, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
}

// ---------- daily reminder ----------
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("r", "یادآوری", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).putExtra("review", true), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, Notification.Builder(c, "r").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("وقت مرور کلمات!").setContentText("کلمه‌های امروز منتظرتن")
            .setContentIntent(pi).setAutoCancel(true).build())
    }
}
fun schedule(c: Context, h: Int, m: Int) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
    }
    val pi = PendingIntent.getBroadcast(c, 0, Intent(c, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    c.getSystemService(AlarmManager::class.java)
        .setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    Store.sp(c).edit().putInt("h", h).putInt("m", m).apply()
}

// ---------- audio / image ----------
var rec: MediaRecorder? = null
var recFile: File? = null
fun startRec(c: Context) {
    val f = File(c.filesDir, "a_${System.currentTimeMillis()}.m4a"); recFile = f
    rec = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(c) else MediaRecorder()).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(f.absolutePath); prepare(); start()
    }
}
fun stopRec() { try { rec?.stop() } catch (_: Exception) {}; rec?.release(); rec = null }
fun play(c: Context, name: String) {
    try { MediaPlayer().apply { setDataSource(File(c.filesDir, name).path); prepare(); start(); setOnCompletionListener { release() } } }
    catch (_: Exception) {}
}
fun saveImage(c: Context, u: Uri, highQuality: Boolean = false): String? = try {
    val bmp = c.contentResolver.openInputStream(u)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { if (!highQuality) inSampleSize = 2 })
    }!!
    val f = File(c.filesDir, "i_${System.currentTimeMillis()}.jpg")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, if (highQuality) 97 else 85, it) }
    f.name
} catch (e: Exception) { null }

// ---------- backup / restore (zip: data + media + this app's apk) ----------
fun backup(c: Context, u: Uri) {
    c.contentResolver.openOutputStream(u)?.let { out ->
        ZipOutputStream(out).use { z ->
            fun add(name: String, f: File) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            add("data.json", Store.file(c))
            c.filesDir.listFiles()?.filter { it.name.startsWith("a_") || it.name.startsWith("i_") }?.forEach { add(it.name, it) }
            add("VocabReview.apk", File(c.applicationInfo.sourceDir))
        }
    }
}
fun restore(c: Context, u: Uri) {
    c.contentResolver.openInputStream(u)?.let { inp ->
        ZipInputStream(inp).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = File(e.name).name
                if (n == "data.json" || n.startsWith("a_") || n.startsWith("i_")) File(c.filesDir, n).outputStream().use { z.copyTo(it) }
                e = z.nextEntry
            }
        }
    }
}
