package com.example.vocab

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.time.YearMonth
import java.util.UUID
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

val titr = FontFamily(Font(R.font.lalezar_regular))
val amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))

fun appTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = titr), displayMedium = b.displayMedium.copy(fontFamily = titr),
        displaySmall = b.displaySmall.copy(fontFamily = titr), headlineLarge = b.headlineLarge.copy(fontFamily = titr),
        headlineMedium = b.headlineMedium.copy(fontFamily = titr), headlineSmall = b.headlineSmall.copy(fontFamily = titr),
        titleLarge = b.titleLarge.copy(fontFamily = titr), titleMedium = b.titleMedium.copy(fontFamily = titr),
        titleSmall = b.titleSmall.copy(fontFamily = titr), labelLarge = b.labelLarge.copy(fontFamily = titr),
        labelMedium = b.labelMedium.copy(fontFamily = titr), labelSmall = b.labelSmall.copy(fontFamily = titr),
        bodyLarge = b.bodyLarge.copy(fontFamily = amiri), bodyMedium = b.bodyMedium.copy(fontFamily = amiri),
        bodySmall = b.bodySmall.copy(fontFamily = amiri)
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val startReview = intent.getBooleanExtra("review", false)
        val initTab = intent.getIntExtra("tab", -1)
        setContent {
            val c = LocalContext.current
            var themeMode by remember { mutableIntStateOf(Store.sp(c).getInt("theme", 0)) }
            val sys = isSystemInDarkTheme()
            val dark = when (themeMode) { 1 -> false; 2 -> true; else -> sys }
            val scheme = if (Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this) }
            else if (dark) darkColorScheme(primary = Color(0xFF9FA8DA)) else lightColorScheme(primary = Color(0xFF3F51B5))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = scheme, typography = appTypography()) {
                    Surface(Modifier.fillMaxSize()) {
                        App(startReview, initTab, themeMode) { m -> themeMode = m; Store.sp(c).edit().putInt("theme", m).apply() }
                    }
                }
            }
        }
    }
}

@Composable
fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
fun Pic(c: Context, name: String?, h: Int = 160) {
    val bmp = remember(name) { name?.let { BitmapFactory.decodeFile(File(c.filesDir, it).path) } }
    bmp?.let { Image(it.asImageBitmap(), null, Modifier.height(h.dp)) }
}

@Composable
fun Confirm(msg: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(onDismissRequest = onNo, text = { Text(msg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onYes) { Text("حذف") } },
        dismissButton = { TextButton(onNo) { Text("انصراف") } })
}

@Composable
fun App(startReview: Boolean, initTab: Int, themeMode: Int, setThemeMode: (Int) -> Unit) {
    val c = LocalContext.current
    var st by remember { mutableStateOf(Store.load(c)) }
    val upd: (State) -> Unit = { n -> st = n; Store.save(c, n) }
    var screen by remember { mutableStateOf(if (startReview) "review" else "home") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        perm.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        val p = Store.sp(c)
        if (p.contains("h")) schedule(c, p.getInt("h", 0), p.getInt("m", 0))
        scheduleMidnight(c)
        updateWidgets(c)
    }
    val old = if (screen.startsWith("edit:")) st.words.firstOrNull { it.id == screen.removePrefix("edit:") } else null
    when {
        screen == "add" -> Add(c, null, { w -> upd(st.copy(words = st.words + w)) }) { screen = "home" }
        screen == "review" -> Review(c, st, Store.sp(c).getInt("s", 10), upd) { screen = "home" }
        old != null -> Add(c, old, { nw ->
            if (old.audio != null && old.audio != nw.audio) File(c.filesDir, old.audio).delete()
            if (old.image != null && old.image != nw.image) File(c.filesDir, old.image).delete()
            upd(st.copy(words = st.words.map { if (it.id == nw.id) nw else it })); screen = "home"
        }) { screen = "home" }
        else -> Home(c, st, upd, initTab, themeMode, setThemeMode) { screen = it }
    }
}

@Composable
fun Home(c: Context, st: State, upd: (State) -> Unit, initTab: Int, themeMode: Int, setThemeMode: (Int) -> Unit, go: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(if (initTab in 0..5) initTab else 0) }
    var toDelete by remember { mutableStateOf<Word?>(null) }
    var selected by remember { mutableStateOf<Word?>(null) }
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var rev by remember { mutableStateOf(false) }
    var byMeaning by remember { mutableStateOf(false) }
    val t = today()
    val tabNames = listOf("امروز", "همه کلمات", "روتین و تسک", "شیفت", "چک و قسط", "تنظیمات")
    val pagerState = rememberPagerState(initialPage = tab) { tabNames.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab) { if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab) }
    LaunchedEffect(pagerState.currentPage) { tab = pagerState.currentPage }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabNames.forEachIndexed { i, s ->
                val on = tab == i
                Text(
                    s, fontSize = 13.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        // HorizontalPager handles the swipe gesture natively — snappy with a short flick, and doesn't fight
        // with each page's own vertical scrolling the way a hand-rolled drag detector did.
        HorizontalPager(state = pagerState, modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) { page ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                0 -> {
                    val due = st.words.filter { it.due <= t }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ go("review") }, enabled = due.isNotEmpty()) { Text("شروع مرور (${fa(due.size)})") }
                        OutlinedButton({ go("add") }) { Text("افزودن کلمه") }
                    }
                    if (due.isEmpty()) Text("امروز کلمه‌ای برای مرور نیست 🎉")
                    LazyColumn {
                        items(due) { w ->
                            Column {
                                Text(w.text, fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
                                DividerLine()
                            }
                        }
                    }
                }
                1 -> {
                    OutlinedButton({ go("add") }) { Text("افزودن کلمه") }
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجو") })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        listOf("الفبایی", "نزدیک‌ترین مرور", "دورترین مرور").forEachIndexed { i, s ->
                            FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(s) })
                        }
                        TextButton({ rev = !rev }) { Text(if (rev) "⇅ معکوس" else "⇅") }
                    }
                    FilterChip(selected = byMeaning, onClick = { byMeaning = !byMeaning }, label = { Text("نمایش بر اساس معنی") })
                    val col = remember { java.text.Collator.getInstance(java.util.Locale("fa")) }
                    val key = q.trim()
                    val list = st.words.filter { key.isEmpty() || it.text.contains(key, true) || it.meaning.contains(key, true) }
                        .let { l -> when (sort) { 0 -> l.sortedWith { a, b -> col.compare(if (byMeaning) a.meaning else a.text, if (byMeaning) b.meaning else b.text) }; 1 -> l.sortedBy { it.due }; else -> l.sortedByDescending { it.due } } }
                        .let { if (rev) it.reversed() else it }
                    Text("مجموع: ${fa(list.size)} کلمه", fontSize = 12.sp)
                    LazyColumn {
                        itemsIndexed(list) { idx, w ->
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { selected = w }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("${fa(idx + 1)}.", Modifier.width(28.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(if (byMeaning) w.meaning else w.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
                                    Box(
                                        Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            fa(w.k), fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                        )
                                    }
                                    TextButton({ w.audio?.let { a -> play(c, a) } }, enabled = w.audio != null) { Text("🔊") }
                                }
                                DividerLine()
                            }
                        }
                    }
                }
                2 -> Routines(st, upd)
                3 -> Shifts(c, st, upd)
                4 -> Bills(st, upd)
                else -> Settings(c, st, upd, themeMode, setThemeMode)
            }
        }
        }
    }
    toDelete?.let { w ->
        Confirm("کلمه‌ی «${w.text}» حذف بشه؟ این کار قابل بازگشت نیست.", {
            w.audio?.let { File(c.filesDir, it).delete() }
            w.image?.let { File(c.filesDir, it).delete() }
            upd(st.copy(words = st.words - w)); toDelete = null
        }, { toDelete = null })
    }
    selected?.let { w ->
        Dialog(onDismissRequest = { selected = null }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(w.text, fontFamily = titr, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(w.meaning, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Pic(c, w.image, 160)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton({ w.audio?.let { a -> play(c, a) } }, enabled = w.audio != null) { Text("🔊", fontSize = 20.sp) }
                        Spacer(Modifier.width(20.dp))
                        TextButton({ val id = w.id; selected = null; go("edit:$id") }) { Text("✏️", fontSize = 20.sp) }
                        Spacer(Modifier.width(20.dp))
                        TextButton({ toDelete = w; selected = null }) { Text("🗑", fontSize = 20.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton({ selected = null }) { Text("بستن") }
                }
            }
        }
    }
}

@Composable
fun Routines(st: State, upd: (State) -> Unit) {
    val t = today()
    var name by remember { mutableStateOf("") }
    var sel by remember { mutableLongStateOf(t) }
    var ym by remember { mutableStateOf(jal(t).let { it.y to it.m }) }
    var delR by remember { mutableStateOf<Routine?>(null) }
    var delT by remember { mutableStateOf<Task?>(null) }
    val (first, len, off) = jmonth(ym.first, ym.second)
    val js = jal(sel)
    val cs = MaterialTheme.colorScheme
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ ym = if (ym.second == 1) (ym.first - 1) to 12 else ym.first to (ym.second - 1) }) { Text("▶") }
                        Text("${MONTHS[ym.second - 1]} ${fa(ym.first)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, fontSize = 20.sp)
                        TextButton({ ym = if (ym.second == 12) (ym.first + 1) to 1 else ym.first to (ym.second + 1) }) { Text("◀") }
                    }
                    Row { WEEK.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, color = cs.primary) } }
                    val cells: List<Int?> = List(off) { null } + (1..len).toList()
                    cells.chunked(7).forEach { row ->
                        Row {
                            (row + List(7 - row.size) { null }).forEach { d ->
                                if (d == null) Spacer(Modifier.weight(1f)) else {
                                    val day = first + d - 1
                                    val n = st.routines.count { day in (st.ticks[it.id] ?: emptySet()) }
                                    val full = n == st.routines.size
                                    val tk = st.tasks.filter { it.day == day }
                                    val bg = when { day == sel -> cs.primary; n > 0 && full -> cs.tertiaryContainer; n > 0 -> cs.secondaryContainer; else -> Color.Transparent }
                                    val fg = if (day == sel) cs.onPrimary else cs.onSurface
                                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape).background(bg)
                                        .let { if (day == t) it.border(2.dp, cs.primary, CircleShape) else it }
                                        .clickable { sel = day }, contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(fa(d), color = fg, fontSize = 15.sp)
                                            if (tk.isNotEmpty()) Box(Modifier.size(5.dp).clip(CircleShape).background(
                                                if (tk.all { it.done }) cs.tertiary else if (day == sel) cs.onPrimary else if (day < t) cs.error else cs.primary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("${DAYS[js.dow]} ${fa(js.d)} ${MONTHS[js.m - 1]} ${fa(js.y)}" + if (sel == t) " (امروز)" else "", fontFamily = titr, fontSize = 18.sp) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان روتین یا تسک") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ if (name.isNotBlank()) { upd(st.copy(routines = st.routines + Routine(UUID.randomUUID().toString(), name.trim()))); name = "" } }) { Text("+ روتین روزانه") }
                    OutlinedButton({ if (name.isNotBlank()) { upd(st.copy(tasks = st.tasks + Task(UUID.randomUUID().toString(), name.trim(), sel))); name = "" } }) { Text("+ تسک این روز") }
                }
            }
        }
        items(st.routines) { r ->
            val days = st.ticks[r.id] ?: emptySet()
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(sel in days, { on -> upd(st.copy(ticks = st.ticks + (r.id to (if (on) days + sel else days - sel)))) }, enabled = r.id != "review" && sel <= t)
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontFamily = titr, fontSize = 17.sp)
                        Text("🔥 ${fa(streak(days, t))} روز متوالی" + if (r.id == "review") " • خودکار" else " • روتین", fontSize = 12.sp)
                    }
                    if (r.id != "review") TextButton({ delR = r }) { Text("🗑") }
                }
            }
        }
        val dayTasks = st.tasks.filter { it.day == sel }
        val dayShifts = (st.shifts[sel] ?: emptySet()).toList()
        val dayBills = st.bills.filter { it.day == sel }
        if (dayTasks.isEmpty() && dayShifts.isEmpty() && dayBills.isEmpty()) item { Text("تسکی برای این روز ثبت نشده", fontSize = 13.sp) }
        items(dayShifts) { code ->
            Card(Modifier.fillMaxWidth()) { Text("🏥 " + shiftLabel(code), Modifier.padding(12.dp)) }
        }
        items(dayBills) { b ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(b.done, { on -> upd(st.copy(bills = st.bills.map { if (it.id == b.id) it.copy(done = on) else it })) })
                    Text(
                        "💳 ${b.kind}" + (if (b.title.isNotBlank()) " ${b.title}" else "") + " ${fa(b.amount)} تومان",
                        Modifier.weight(1f), textDecoration = if (b.done) TextDecoration.LineThrough else null
                    )
                }
            }
        }
        items(dayTasks) { k ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(k.done, { on -> upd(st.copy(tasks = st.tasks.map { if (it.id == k.id) it.copy(done = on) else it })) })
                    Text("📌 " + k.title, Modifier.weight(1f), textDecoration = if (k.done) TextDecoration.LineThrough else null)
                    TextButton({ delT = k }) { Text("🗑") }
                }
            }
        }
    }
    delR?.let { r ->
        Confirm("روتین «${r.name}» حذف بشه؟ سابقه‌ی تیک‌هاش هم پاک می‌شه.", {
            upd(st.copy(routines = st.routines - r, ticks = st.ticks - r.id)); delR = null
        }, { delR = null })
    }
    delT?.let { k -> Confirm("تسک «${k.title}» حذف بشه؟", { upd(st.copy(tasks = st.tasks - k)); delT = null }, { delT = null }) }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("دستیار", fontFamily = titr, fontSize = 28.sp)
                Spacer(Modifier.height(16.dp))
                Text("این برنامه برای کاربرد شخصی سید علی حسینی ساخته شده است.", textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                TextButton(onDismiss) { Text("بستن") }
            }
        }
    }
}

@Composable
fun ZoomableImageDialog(c: Context, path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val bmp = remember(path) { BitmapFactory.decodeFile(File(c.filesDir, path).path) }
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            }
        ) {
            bmp?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                )
            }
            TextButton({ onDismiss() }, Modifier.align(Alignment.TopStart).padding(12.dp)) { Text("✕ بستن", color = Color.White, fontSize = 16.sp) }
        }
    }
}

@Composable
fun Shifts(c: Context, st: State, upd: (State) -> Unit) {
    var ym by remember { mutableStateOf(jal(today()).let { it.y to it.m }) }
    var delImg by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var viewImage by remember { mutableStateOf(false) }
    val mk = monthKey(ym.first, ym.second)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        u?.let { upd(st.copy(shiftImages = st.shiftImages + (mk to (saveImage(c, it, highQuality = true) ?: return@let)))) }
    }
    val codes = listOf("1" to "تایم صبح", "3" to "تایم عصر", "2" to "تایم شب")
    val (first, len, _) = jmonth(ym.first, ym.second)
    val monthImage = st.shiftImages[mk]
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ ym = if (ym.second == 1) (ym.first - 1) to 12 else ym.first to (ym.second - 1) }) { Text("▶") }
                Text("${MONTHS[ym.second - 1]} ${fa(ym.first)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, fontSize = 18.sp)
                TextButton({ ym = if (ym.second == 12) (ym.first + 1) to 1 else ym.first to (ym.second + 1) }) { Text("◀") }
            }
        }
        item {
            Button({ editing = !editing }, Modifier.fillMaxWidth()) { Text(if (editing) "پایان ویرایش ✓" else "✏️ ویرایش شیفت‌ها") }
        }
        if (!editing) item { Text("برای جلوگیری از تغییر اشتباهی، اول دکمه‌ی ویرایش رو بزن.", fontSize = 12.sp) }
        item {
            Row {
                Spacer(Modifier.width(80.dp))
                codes.forEach { (_, label) -> Text(label, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center) }
            }
        }
        items((1..len).toList()) { d ->
            val day = first + d - 1
            val jd = jal(day)
            val set = st.shifts[day] ?: emptySet()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fa(d)}  ${DAYS[jd.dow]}", Modifier.width(80.dp), fontSize = 12.sp)
                    codes.forEach { (code, _) ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Checkbox(code in set, { on ->
                                val ns = if (on) set + code else set - code
                                upd(st.copy(shifts = st.shifts + (day to ns)))
                            }, enabled = editing)
                        }
                    }
                }
                DividerLine()
            }
        }
        item {
            val monthCodes = (0 until len).flatMap { st.shifts[first + it] ?: emptySet() }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("گزارش این ماه", fontFamily = titr, fontSize = 16.sp)
                    Text("مجموع شیفت‌ها: ${fa(monthCodes.size)}")
                    codes.forEach { (code, label) -> Text("$label: ${fa(monthCodes.count { it == code })}") }
                }
            }
        }
        item {
            val t = today()
            val rows = (0 until len).flatMap { off ->
                val day = first + off
                (st.shifts[day] ?: emptySet()).map { code -> day to code }
            }.sortedBy { it.first }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("لیست شیفت‌های این ماه", fontFamily = titr, fontSize = 16.sp)
                    if (rows.isEmpty()) Text("شیفتی ثبت نشده", fontSize = 13.sp)
                    else {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                            Text("#", Modifier.width(28.dp), fontSize = 12.sp, fontFamily = titr, textAlign = TextAlign.Center)
                            Text("تاریخ", Modifier.weight(1f), fontSize = 12.sp, fontFamily = titr, textAlign = TextAlign.Center)
                            Text("روز", Modifier.weight(1f), fontSize = 12.sp, fontFamily = titr, textAlign = TextAlign.Center)
                            Text("شیفت", Modifier.weight(1f), fontSize = 12.sp, fontFamily = titr, textAlign = TextAlign.Center)
                        }
                        DividerLine()
                        rows.forEachIndexed { idx, (day, code) ->
                            val jd = jal(day)
                            val color = when { day < t -> MaterialTheme.colorScheme.onSurface; day == t -> Color(0xFFD32F2F); else -> Color(0xFF2E7D32) }
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(fa(idx + 1), Modifier.width(28.dp), color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
                                // Built as separate pieces (not one string) so the RTL layout direction places them
                                // day, month, year from the right — reliable, unlike relying on bidi text reordering.
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                                    Text(fa(jd.d), color = color, fontSize = 13.sp)
                                    Text("/", color = color, fontSize = 13.sp)
                                    Text(fa(jd.m), color = color, fontSize = 13.sp)
                                    Text("/", color = color, fontSize = 13.sp)
                                    Text(fa(jd.y), color = color, fontSize = 13.sp)
                                }
                                Text(DAYS[jd.dow], Modifier.weight(1f), color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
                                Text(shiftShort(code), Modifier.weight(1f), color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                            DividerLine()
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("عکس برنامه‌ی شیفت — ${MONTHS[ym.second - 1]} ${fa(ym.first)}", fontFamily = titr, fontSize = 16.sp)
                    if (monthImage != null) {
                        Box(Modifier.clickable { viewImage = true }) { Pic(c, monthImage, 160) }
                        Text("برای دیدن بزرگ و زوم کردن، روی عکس بزن", fontSize = 11.sp)
                    } else {
                        Text("برای این ماه عکسی اضافه نشده", fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ pick.launch("image/*") }) { Text(if (monthImage != null) "تغییر عکس این ماه" else "افزودن عکس این ماه") }
                        if (monthImage != null) TextButton({ delImg = true }) { Text("🗑 حذف عکس") }
                    }
                }
            }
        }
    }
    if (delImg) Confirm("عکس برنامه‌ی شیفت این ماه حذف بشه؟", {
        monthImage?.let { File(c.filesDir, it).delete() }
        upd(st.copy(shiftImages = st.shiftImages - mk)); delImg = false
    }, { delImg = false })
    if (viewImage && monthImage != null) ZoomableImageDialog(c, monthImage) { viewImage = false }
}

class ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val commasBefore = ((offset - 1).coerceAtLeast(0)) / 3
                return (offset + commasBefore).coerceAtMost(grouped.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                val commasBefore = grouped.take(offset.coerceAtMost(grouped.length)).count { it == ',' }
                return (offset - commasBefore).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(grouped), offsetMapping)
    }
}

@Composable
fun Bills(st: State, upd: (State) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var sel by remember { mutableLongStateOf(today()) }
    var repeat by remember { mutableIntStateOf(1) }
    var ym by remember { mutableStateOf(jal(today()).let { it.y to it.m }) }
    var editing by remember { mutableStateOf<Bill?>(null) }
    var delTarget by remember { mutableStateOf<Bill?>(null) }
    var kind by remember { mutableStateOf("قسط") }
    val (first, len, off) = jmonth(ym.first, ym.second)
    val cs = MaterialTheme.colorScheme
    val t = today()
    val monthBills = st.bills.filter { val jd = jal(it.day); jd.y == ym.first && jd.m == ym.second }.sortedBy { it.day }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("افزودن چک/قسط", fontFamily = titr, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == "قسط", onClick = { kind = "قسط" }, label = { Text("قسط") })
                        FilterChip(selected = kind == "چک", onClick = { kind = "چک" }, label = { Text("چک") })
                    }
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان (مثلاً: اقساط ماشین)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        amount, { amount = it.filter { ch -> ch.isDigit() } }, label = { Text("مبلغ (تومان)") }, singleLine = true,
                        visualTransformation = ThousandsTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ ym = if (ym.second == 1) (ym.first - 1) to 12 else ym.first to (ym.second - 1) }) { Text("▶") }
                        Text("${MONTHS[ym.second - 1]} ${fa(ym.first)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, fontSize = 20.sp)
                        TextButton({ ym = if (ym.second == 12) (ym.first + 1) to 1 else ym.first to (ym.second + 1) }) { Text("◀") }
                    }
                    Row { WEEK.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, color = cs.primary) } }
                    val cells: List<Int?> = List(off) { null } + (1..len).toList()
                    cells.chunked(7).forEach { row ->
                        Row {
                            (row + List(7 - row.size) { null }).forEach { d ->
                                if (d == null) Spacer(Modifier.weight(1f)) else {
                                    val day = first + d - 1
                                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                        .background(if (day == sel) cs.primary else Color.Transparent)
                                        .let { if (day == t) it.border(2.dp, cs.primary, CircleShape) else it }
                                        .clickable { sel = day }, contentAlignment = Alignment.Center) {
                                        Text(fa(d), color = if (day == sel) cs.onPrimary else cs.onSurface, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تعداد ماه (تکرار): ${fa(repeat)}")
                        TextButton({ if (repeat > 1) repeat-- }) { Text("−") }
                        TextButton({ repeat++ }) { Text("+") }
                    }
                    Button({
                        val amt = amount.toLongOrNull() ?: return@Button
                        val sid = if (repeat > 1) UUID.randomUUID().toString() else null
                        val newBills = (0 until repeat).map { k -> Bill(UUID.randomUUID().toString(), amt, addMonths(sel, k), sid, kind = kind, title = title.trim()) }
                        upd(st.copy(bills = st.bills + newBills))
                        amount = ""; repeat = 1; kind = "قسط"; title = ""
                    }, Modifier.fillMaxWidth()) { Text("افزودن") }
                }
            }
        }
        item { Text("چک و قسط‌های ${MONTHS[ym.second - 1]} ${fa(ym.first)}", fontFamily = titr, fontSize = 15.sp) }
        if (monthBills.isEmpty()) item { Text("موردی برای این ماه ثبت نشده", fontSize = 13.sp) }
        items(monthBills) { b ->
            val jd = jal(b.day)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(b.done, { on -> upd(st.copy(bills = st.bills.map { if (it.id == b.id) it.copy(done = on) else it })) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${b.kind}" + (if (b.title.isNotBlank()) " ${b.title}" else "") + " ${fa(b.amount)} تومان",
                            fontFamily = titr, textDecoration = if (b.done) TextDecoration.LineThrough else null
                        )
                        Text("${DAYS[jd.dow]} ${fa(jd.d)} ${MONTHS[jd.m - 1]} ${fa(jd.y)}" + if (b.seriesId != null) " • بخشی از سری" else "", fontSize = 12.sp)
                    }
                    TextButton({ editing = b }) { Text("✏️") }
                    TextButton({ delTarget = b }) { Text("🗑") }
                }
                DividerLine()
            }
        }
    }
    editing?.let { b -> BillEditDialog(b, onSave = { nb -> upd(st.copy(bills = st.bills.map { if (it.id == nb.id) nb else it })); editing = null }, onDismiss = { editing = null }) }
    delTarget?.let { b ->
        if (b.seriesId != null) {
            AlertDialog(onDismissRequest = { delTarget = null }, text = { Text("این مورد بخشی از یه سری تکراریه. کدوم حذف بشه؟", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton({ val sid = b.seriesId; upd(st.copy(bills = st.bills.filter { it.seriesId != sid })); delTarget = null }) { Text("کل سری") } },
                dismissButton = { TextButton({ upd(st.copy(bills = st.bills - b)); delTarget = null }) { Text("فقط همین") } })
        } else {
            Confirm("این چک/قسط حذف بشه؟", { upd(st.copy(bills = st.bills - b)); delTarget = null }, { delTarget = null })
        }
    }
}

@Composable
fun BillEditDialog(bill: Bill, onSave: (Bill) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(bill.title) }
    var amount by remember { mutableStateOf(bill.amount.toString()) }
    var kind by remember { mutableStateOf(bill.kind) }
    var sel by remember { mutableLongStateOf(bill.day) }
    var ym by remember { mutableStateOf(jal(bill.day).let { it.y to it.m }) }
    val (first, len, off) = jmonth(ym.first, ym.second)
    val cs = MaterialTheme.colorScheme
    val t = today()
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ویرایش چک/قسط", fontFamily = titr, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "قسط", onClick = { kind = "قسط" }, label = { Text("قسط") })
                    FilterChip(selected = kind == "چک", onClick = { kind = "چک" }, label = { Text("چک") })
                }
                OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it.filter { ch -> ch.isDigit() } }, label = { Text("مبلغ (تومان)") }, singleLine = true,
                    visualTransformation = ThousandsTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ ym = if (ym.second == 1) (ym.first - 1) to 12 else ym.first to (ym.second - 1) }) { Text("▶") }
                    Text("${MONTHS[ym.second - 1]} ${fa(ym.first)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, fontSize = 18.sp)
                    TextButton({ ym = if (ym.second == 12) (ym.first + 1) to 1 else ym.first to (ym.second + 1) }) { Text("◀") }
                }
                Row { WEEK.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = titr, color = cs.primary, fontSize = 12.sp) } }
                val cells: List<Int?> = List(off) { null } + (1..len).toList()
                cells.chunked(7).forEach { row ->
                    Row {
                        (row + List(7 - row.size) { null }).forEach { d ->
                            if (d == null) Spacer(Modifier.weight(1f)) else {
                                val day = first + d - 1
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                    .background(if (day == sel) cs.primary else Color.Transparent)
                                    .let { if (day == t) it.border(2.dp, cs.primary, CircleShape) else it }
                                    .clickable { sel = day }, contentAlignment = Alignment.Center) {
                                    Text(fa(d), color = if (day == sel) cs.onPrimary else cs.onSurface, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({
                        val amt = amount.toLongOrNull() ?: return@Button
                        onSave(bill.copy(amount = amt, day = sel, kind = kind, title = title.trim()))
                    }) { Text("ذخیره") }
                    TextButton(onDismiss) { Text("انصراف") }
                }
            }
        }
    }
}

@Composable
fun Settings(c: Context, st: State, upd: (State) -> Unit, themeMode: Int, setThemeMode: (Int) -> Unit) {
    var secs by remember { mutableIntStateOf(Store.sp(c).getInt("s", 10)) }
    var showAbout by remember { mutableStateOf(false) }
    var rem by remember {
        mutableStateOf(Store.sp(c).let { if (it.contains("h")) "%02d:%02d".format(it.getInt("h", 0), it.getInt("m", 0)) else "تنظیم نشده" })
    }
    val bk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        u?.let { Store.save(c, st); backup(c, it) }
    }
    val rs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { restore(c, it); upd(Store.load(c)) }
    }
    Text("تم برنامه", fontFamily = titr, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "سیستم", 1 to "روشن", 2 to "تیره").forEach { (v, label) ->
            FilterChip(selected = themeMode == v, onClick = { setThemeMode(v) }, label = { Text(label) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("نمایش جواب بعد از پاسخ: $secs ثانیه")
        TextButton({ if (secs > 3) { secs--; Store.sp(c).edit().putInt("s", secs).apply() } }) { Text("−") }
        TextButton({ secs++; Store.sp(c).edit().putInt("s", secs).apply() }) { Text("+") }
    }
    Button({
        TimePickerDialog(c, { _, h, m -> schedule(c, h, m); rem = "%02d:%02d".format(h, m) }, 20, 0, true).show()
    }) { Text("یادآوری روزانه: $rem") }
    Button({ bk.launch("vocab-backup.zip") }) { Text("گرفتن پشتیبان") }
    Button({ rs.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("برگردوندن پشتیبان") }
    Button({ showAbout = true }) { Text("درباره ما") }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
fun Add(c: Context, edit: Word?, onSave: (Word) -> Unit, back: () -> Unit) {
    var t by remember { mutableStateOf(edit?.text ?: "") }
    var m by remember { mutableStateOf(edit?.meaning ?: "") }
    var audio by remember { mutableStateOf(edit?.audio) }
    var img by remember { mutableStateOf(edit?.image) }
    var recording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableIntStateOf(0) }
    var lastSaved by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recording) {
        if (recording) {
            recSeconds = 0
            while (true) { delay(1000); recSeconds++ }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { img = saveImage(c, it) } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (edit == null) "کلمه‌ی جدید" else "ویرایش کلمه", fontSize = 20.sp, fontFamily = titr)
        OutlinedTextField(m, { m = it }, label = { Text("معنی") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(t, { t = it }, label = { Text("کلمه") }, modifier = Modifier.fillMaxWidth())
        Button({
            if (recording) { stopRec(); audio = recFile?.name; recording = false } else { startRec(c); recording = true }
        }) { Text(if (recording) "توقف ضبط" else if (audio != null) "ضبط دوباره‌ی تلفظ" else "ضبط تلفظ") }
        if (recording) Text("⏺ در حال ضبط... ${fa(recSeconds)} ثانیه", color = Color(0xFFD32F2F), fontSize = 13.sp)
        if (audio != null && !recording) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ play(c, audio!!) }) { Text("▶ پخش تلفظ") }
                TextButton({ File(c.filesDir, audio!!).delete(); audio = null }) { Text("🗑 حذف ضبط") }
            }
        }
        Button({ pick.launch("image/*") }) { Text(if (img != null) "تغییر تصویر" else "انتخاب تصویر (اختیاری)") }
        Pic(c, img, 140)
        lastSaved?.let { Text("«$it» ذخیره شد ✓", color = Color(0xFF2E7D32), fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                if (t.isNotBlank()) {
                    val savedWord = t.trim()
                    onSave(edit?.copy(text = t.trim(), meaning = m.trim(), audio = audio, image = img)
                        ?: Word(UUID.randomUUID().toString(), t.trim(), m.trim(), audio, img, due = today() + 1))
                    if (edit == null) {
                        // Stay on this screen so several words can be added back-to-back.
                        t = ""; m = ""; audio = null; img = null; lastSaved = savedWord
                    }
                }
            }) { Text("ذخیره") }
            TextButton(back) { Text(if (edit == null) "پایان و بازگشت" else "بازگشت") }
        }
    }
}

@Composable
fun Review(c: Context, st: State, secs: Int, onChange: (State) -> Unit, back: () -> Unit) {
    val t = today()
    val queue = remember { st.words.filter { it.due <= t }.map { it.id }.shuffled() }
    var i by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(secs) }
    val done = i >= queue.size
    val w = st.words.firstOrNull { it.id == queue.getOrNull(i) }
    LaunchedEffect(done) {
        if (done && queue.isNotEmpty()) onChange(st.copy(ticks = st.ticks + ("review" to ((st.ticks["review"] ?: emptySet()) + t))))
    }
    LaunchedEffect(i, answered) {
        if (answered) {
            left = secs
            while (left > 0) { delay(1000); left-- }
            i++; answered = false
        }
    }
    fun answer(ok: Boolean) {
        val w0 = w ?: return
        onChange(st.copy(words = st.words.map { if (it.id == w0.id) it.answer(ok, t) else it }))
        answered = true
        w0.audio?.let { play(c, it) }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (queue.isEmpty()) Text("امروز کلمه‌ای برای مرور نیست 🎉", fontSize = 22.sp)
        else if (done) Text("مرور امروز تمام شد 🎉", fontSize = 26.sp)
        else if (w != null) {
            Text("${i + 1}/${queue.size}")
            Spacer(Modifier.height(16.dp))
            Text(w.text, fontSize = 40.sp, fontFamily = titr)
            Spacer(Modifier.height(16.dp))
            if (!answered) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Button({ answer(true) }) { Text("✓", fontSize = 28.sp) }
                    Button({ answer(false) }) { Text("✗", fontSize = 28.sp) }
                }
            } else {
                Text(w.meaning, fontSize = 26.sp)
                Pic(c, w.image, 180)
                w.audio?.let { a -> TextButton({ play(c, a) }) { Text("🔊 پخش دوباره") } }
                Text("⏱ $left")
                TextButton({ left = 0 }) { Text("بعدی") }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(back) { Text("خروج") }
    }
}
