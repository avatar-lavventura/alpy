package com.alper.alpy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private sealed interface Screen
private data object Home : Screen
private data class EditR(val r: Reminder) : Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alarms.syncAll(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent {
            Themed {
                var screen by remember { mutableStateOf<Screen>(Home) }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var showDone by rememberSaveable { mutableStateOf(false) }
                val back = { screen = Home }
                BackHandler(screen != Home, back)
                when (val s = screen) {
                    Home -> HomeScreen(tab, { tab = it }, showDone, { showDone = it }) { screen = it }
                    is EditR -> ReminderEditor(s.r, back)
                }
            }
        }
    }
}

private val Green = Color(0xFF2E9E44)

@Composable
fun Themed(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        val s = Color(0xFF1C1C1E)
        darkColorScheme(
            primary = Color(0xFF4CC264), onPrimary = Color.White, background = Color.Black, surface = s,
            surfaceContainer = s, surfaceContainerLow = s, surfaceContainerHigh = s, surfaceContainerHighest = Color(0xFF3A3A3C),
            secondaryContainer = Color(0xFF1E4D27), onSecondaryContainer = Color.White,
        )
    } else {
        val s = Color.White
        lightColorScheme(
            primary = Green, onPrimary = Color.White, background = Color(0xFFEFF0EF), surface = s,
            surfaceContainer = s, surfaceContainerLow = s, surfaceContainerHigh = s, surfaceContainerHighest = Color(0xFFE3E5E3),
            secondaryContainer = Color(0xFFD5EFD9), onSecondaryContainer = Color.Black,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- small building blocks ----------

@Composable
fun NumField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { text = it.filter(Char::isDigit).take(6); onChange(text.toIntOrNull() ?: 0) }, modifier,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private val zone get() = ZoneId.systemDefault()
private val longFmt = DateTimeFormatter.ofPattern("d MMMM EEE HH:mm")
private fun at(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
private fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
private val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

private fun Reminder.describe(): String {
    if (rule == Every.ONCE) return "Never"
    val unit = rule.name.lowercase().let { if (every == 1) it.dropLast(1) else it }
    var s = if (every == 1) "Every $unit" else "Every $every $unit"
    if (windowFrom != null && windowTo != null) s += " ${hm(windowFrom)}-${hm(windowTo)}"
    if (weekdays.isNotEmpty()) s += " " + weekdays.sorted().joinToString(" ") { dayNames[it - 1] }
    if (nag) s += ", nags"
    return s
}

private fun whenLabel(t: Long) = at(t).let {
    if (it.toLocalDate() == LocalDate.now()) it.format(DateTimeFormatter.ofPattern("HH:mm")) else it.format(longFmt)
}

private fun rel(ms: Long): String {
    val m = abs(ms) / 60_000
    val s = when { m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h"; else -> "${m / 1440}d" }
    return if (ms < 0) "-$s" else s
}

private fun section(t: Long): Pair<Int, String> {
    val today = LocalDate.now()
    val day = at(t).toLocalDate()
    return when {
        day < today -> 0 to "Earlier"
        day == today -> 1 to "Today - " + today.format(DateTimeFormatter.ofPattern("d MMM yyyy EEE"))
        day == today.plusDays(1) -> 2 to "Tomorrow"
        day <= today.plusDays(7) -> 3 to "Next 7 days"
        else -> 4 to "Later"
    }
}

@Composable
private fun ClockIcon(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) =
    Canvas(modifier) {
        val w = size.minDimension / 14
        val r = size.minDimension / 2 - w
        drawCircle(color, r, style = Stroke(w))
        drawLine(color, center, center - Offset(0f, r * 0.6f), w, StrokeCap.Round)
        drawLine(color, center, center - Offset(r * 0.45f, 0f), w, StrokeCap.Round)
    }

@Composable
private fun PowerIcon(on: Boolean, onClick: () -> Unit) {
    val c = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick)) {
        val w = 2.dp.toPx()
        drawCircle(c, size.minDimension / 2 - w / 2, style = Stroke(w))
        val r = size.minDimension * 0.22f
        drawArc(c, -60f, 300f, false, center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
        drawLine(c, center - Offset(0f, r * 1.25f), center - Offset(0f, r * 0.25f), w, StrokeCap.Round)
    }
}

@Composable
private fun Header(center: @Composable () -> Unit, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
        right()
    }
    HorizontalDivider()
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val g = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.border(1.dp, g, shape).clip(shape)) {
        options.forEachIndexed { i, s ->
            Text(
                s, Modifier.background(if (i == selected) g else Color.Transparent).clickable { onSelect(i) }
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                color = if (i == selected) MaterialTheme.colorScheme.onPrimary else g,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, collapsed: Boolean? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (collapsed != null) Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown, null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NavRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)
            .heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

@Composable
private fun FieldRow(value: String, placeholder: String, minLines: Int = 1, onChange: (String) -> Unit) {
    val s = MaterialTheme.colorScheme.surface
    TextField(
        value, onChange, Modifier.fillMaxWidth(), placeholder = { Text(placeholder) },
        singleLine = minLines == 1, minLines = minLines,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = s, unfocusedContainerColor = s,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null)
        Text(label, Modifier.padding(8.dp))
    }

@Composable
private fun Empty(text: String) =
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ClockIcon(Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
private fun OptionsDialog(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    AlertDialog(
        close, confirmButton = { TextButton(close) { Text("OK") } }, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp), content = content) },
    )

@Composable
private fun Weekdays(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Text("Only on (none = every day)")
    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(4.dp)) {
        dayNames.forEachIndexed { i, n ->
            val day = i + 1
            FilterChip(day in days, { onChange(if (day in days) days - day else days + day) }, { Text(n) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, minutes: Int, onPick: (Int) -> Unit) {
    var openDlg by remember { mutableStateOf(false) }
    OutlinedButton({ openDlg = true }) { Text("$label ${hm(minutes)}") }
    if (openDlg) {
        val st = rememberTimePickerState(minutes / 60, minutes % 60, true)
        AlertDialog(
            { openDlg = false },
            confirmButton = { TextButton({ onPick(st.hour * 60 + st.minute); openDlg = false }) { Text("OK") } },
            text = { TimePicker(st) },
        )
    }
}

/** Date and time typed on the keyboard (date field follows the phone's locale format). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDateDialog(value: LocalDateTime, close: () -> Unit, onPick: (LocalDateTime) -> Unit) {
    val ds = rememberDatePickerState(
        initialSelectedDateMillis = value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialDisplayMode = DisplayMode.Input,
    )
    val ts = rememberTimePickerState(value.hour, value.minute, true)
    DatePickerDialog(
        close,
        confirmButton = {
            TextButton({
                ds.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atTime(ts.hour, ts.minute)) }
                close()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    ) {
        DatePicker(ds)
        TimeInput(ts, Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
    }
}

/** iOS-style scroll wheel; settles on the row nearest the middle line. */
@Composable
private fun Wheel(items: List<String>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val h = 40.dp
    val half = with(LocalDensity.current) { h.toPx() / 2 }
    val state = rememberLazyListState(selected)
    val cb by rememberUpdatedState(onSelect)
    val sel by rememberUpdatedState(selected)
    val centered by remember {
        derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > half) 1 else 0 }
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val i = centered.coerceIn(items.indices)
            state.animateScrollToItem(i)
            if (i != sel) cb(i)
        }
    }
    LaunchedEffect(selected) { if (!state.isScrollInProgress && centered != selected) state.scrollToItem(selected) }
    Box(modifier.height(h * 5)) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center)) {
            HorizontalDivider()
            Spacer(Modifier.height(h))
            HorizontalDivider()
        }
        LazyColumn(Modifier.fillMaxSize(), state, PaddingValues(vertical = h * 2)) {
            itemsIndexed(items) { i, s ->
                val mid = i == centered
                Text(
                    s, Modifier.fillMaxWidth().height(h).wrapContentHeight(), textAlign = TextAlign.Center,
                    fontSize = if (mid) 22.sp else 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (mid) 1f else 0.35f),
                )
            }
        }
    }
}

@Composable
private fun DateTimeWheel(value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    val today = remember { LocalDate.now() }
    val days = remember { (-30L..730L).map { today.plusDays(it) } }
    val labels = remember { days.map { if (it == today) "Today" else it.format(DateTimeFormatter.ofPattern("d MMM EEE")) } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Wheel(labels, days.indexOf(value.toLocalDate()).coerceAtLeast(0), Modifier.weight(2f)) { onChange(days[it].atTime(value.toLocalTime())) }
        Wheel((0..23).map { "%02d".format(it) }, value.hour, Modifier.weight(1f)) { onChange(value.withHour(it)) }
        Wheel((0..59).map { "%02d".format(it) }, value.minute, Modifier.weight(1f)) { onChange(value.withMinute(it)) }
    }
}

@Composable
private fun MelodyRow(uri: String?, onPick: (String?) -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        @Suppress("DEPRECATION")
        if (res.resultCode == Activity.RESULT_OK) onPick(res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString())
    }
    val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val name = remember(uri) {
        runCatching { RingtoneManager.getRingtone(ctx, uri?.let(Uri::parse) ?: def).getTitle(ctx) }.getOrNull() ?: "Default"
    }
    NavRow("Melody", "($name)") {
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, def)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri?.let(Uri::parse) ?: def)
        )
    }
}

// ---------- home ----------

@Composable
private fun HomeScreen(tab: Int, setTab: (Int) -> Unit, showDone: Boolean, setDone: (Boolean) -> Unit, open: (Screen) -> Unit) {
    val d by Store.data.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(250); value = System.currentTimeMillis() }
    }
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, bottomBar = {
        NavigationBar(containerColor = cs.surface) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = cs.primary, selectedTextColor = cs.primary, indicatorColor = Color.Transparent,
            )
            NavigationBarItem(tab == 0, { setTab(0) }, { ClockIcon(Modifier.size(24.dp)) }, label = { Text("Reminders") }, colors = colors)
            NavigationBarItem(tab == 1, { setTab(1) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, colors = colors)
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            when (tab) {
                0 -> RemindersTab(d, now, showDone, setDone, open)
                else -> SettingsTab(d)
            }
        }
    }
}

private fun newReminder(d: Data): Reminder {
    val n = LocalDateTime.now().withSecond(0).withNano(0)
    return Reminder(start = ms(n.plusMinutes(5L - n.minute % 5)), snoozeMin = d.snoozeMin)
}

@Composable
private fun RemindersTab(d: Data, now: Long, showDone: Boolean, setDone: (Boolean) -> Unit, open: (Screen) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var collapsed by remember { mutableStateOf(setOf<Int>()) }
    var notes by remember { mutableStateOf<Reminder?>(null) }
    Header({ Segmented(listOf("Active", "Done"), if (showDone) 1 else 0) { setDone(it == 1); expanded = null } }) {
        IconButton({ query = if (query == null) "" else null }) { Icon(Icons.Default.Search, "Search", tint = cs.primary) }
        IconButton({ open(EditR(newReminder(d))) }) { Icon(Icons.Default.Add, "Add", tint = cs.primary) }
    }
    query?.let { q -> FieldRow(q, "Search title, notes, category") { query = it } }
    val q = query.orEmpty()
    val list = d.reminders.filter { r -> r.done == showDone && listOf(r.title, r.notes, r.category).any { it.contains(q, true) } }
    if (list.isEmpty()) return Empty(if (showDone) "No Done Reminders" else "No Active Timed Reminders")
    val groups = if (showDone) listOf((0 to "Done") to list.sortedByDescending { it.shownAt() })
    else list.sortedBy { it.shownAt() }.groupBy { section(it.shownAt()) }.toList().sortedBy { it.first.first }
    LazyColumn {
        groups.forEach { (sec, rs) ->
            val key = sec.first
            item(key = "h$key") {
                SectionHeader(sec.second, key in collapsed) { collapsed = if (key in collapsed) collapsed - key else collapsed + key }
                HorizontalDivider()
            }
            if (key !in collapsed) items(rs, key = { it.id }) { r ->
                ReminderRow(r, now) { if (showDone) open(EditR(r)) else expanded = if (expanded == r.id) null else r.id }
                if (expanded == r.id) QuickPanel(r, { open(EditR(r)) }, { notes = r })
            }
        }
    }
    notes?.let { r ->
        AlertDialog(
            { notes = null }, confirmButton = { TextButton({ notes = null }) { Text("OK") } },
            title = { Text(r.title) }, text = { Text(r.notes.ifBlank { "No notes" }) },
        )
    }
}

@Composable
private fun ReminderRow(r: Reminder, now: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val t = r.shownAt()
    Row(
        Modifier.fillMaxWidth().background(cs.surface).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PowerIcon(r.enabled) { act(ctx, if (r.enabled) "off" else "on", "r", r.id) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(whenLabel(t), style = MaterialTheme.typography.titleMedium, color = if (t < now && !r.done) cs.error else cs.onSurface)
                Text(" (${rel(t - now)})", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text(r.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val sub = listOfNotNull(r.describe().takeIf { r.rule != Every.ONCE }, r.category.ifBlank { null }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Checkbox(r.done, { act(ctx, if (r.done) "uncheck" else "check", "r", r.id) })
    }
    HorizontalDivider()
}

@Composable
private fun QuickPanel(r: Reminder, onEdit: () -> Unit, onNotes: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surface).border(1.dp, cs.outline).padding(vertical = 4.dp)) {
        listOf(-1, 1).forEach { sign ->
            Row {
                listOf(5 to "5m", 60 to "1h", 1440 to "1d").forEach { (m, label) ->
                    TextButton({ act(ctx, "adjust", "r", r.id, sign * m) }, Modifier.weight(1f)) {
                        Text((if (sign < 0) "-" else "+") + label, fontSize = 18.sp)
                    }
                }
                TextButton(if (sign < 0) onEdit else onNotes, Modifier.weight(1f)) {
                    Text(if (sign < 0) "Edit" else "Notes", fontSize = 18.sp)
                }
            }
        }
    }
    HorizontalDivider()
}

@SuppressLint("BatteryLife")
@Composable
private fun SettingsTab(d: Data) {
    val ctx = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.json.encodeToString(Data.serializer(), Store.data.value).toByteArray()) }
        Toast.makeText(ctx, "Exported", Toast.LENGTH_SHORT).show()
    }
    var pending by remember { mutableStateOf<Data?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pending = runCatching {
            Store.json.decodeFromString(Data.serializer(), ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
        }.getOrElse { Toast.makeText(ctx, "Not an Alpy backup: ${it.message}", Toast.LENGTH_LONG).show(); null }
    }
    fun open(action: String) = ctx.startActivity(Intent(action, Uri.parse("package:${ctx.packageName}")))
    Header({ Title("Settings") })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("Defaults")
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), Arrangement.spacedBy(12.dp)) {
            NumField("Snooze for new reminders, minutes", d.snoozeMin, Modifier.fillMaxWidth()) { v -> Store.update { it.copy(snoozeMin = v.coerceAtLeast(1)) } }
            NumField("Stop ringing after, minutes", d.silenceMin, Modifier.fillMaxWidth()) { v -> Store.update { it.copy(silenceMin = v.coerceAtLeast(1)) } }
        }
        SectionHeader("Backup")
        NavRow("Export backup", "") { export.launch("alpy-${LocalDate.now()}.json") }
        NavRow("Import backup", "") { import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        Text(
            "A daily automatic backup goes to Download/Alpy (last 7 kept).", Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader("Permissions")
        if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName))
            NavRow("Disable battery optimization", "") { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
        if (Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent())
            NavRow("Allow full-screen alarms", "") { open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) }
        if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
            NavRow("Allow exact alarms", "") { open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
        NavRow("Notification settings", "") {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        Spacer(Modifier.height(32.dp))
    }
    pending?.let { data ->
        AlertDialog(
            { pending = null },
            title = { Text("Replace everything?") },
            text = { Text("${data.reminders.size} reminders will replace the current data.") },
            confirmButton = {
                TextButton({
                    Alarms.cancelAll(ctx)
                    Store.update { data }
                    Alarms.syncAll(ctx)
                    pending = null
                    Toast.makeText(ctx, "Imported", Toast.LENGTH_SHORT).show()
                }) { Text("Replace") }
            },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } },
        )
    }
}

// ---------- editors ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(title: String, back: () -> Unit, onSave: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column {
            CenterAlignedTopAppBar(
                { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(back) { Text("Cancel", fontSize = 17.sp) } },
                actions = { TextButton({ onSave(); back() }) { Text("Done", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) } },
            )
            HorizontalDivider()
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()), content = body)
    }
}

@Composable
private fun DeleteRow(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(28.dp))
    Text(
        label, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(16.dp),
        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
    )
}


@Composable
private fun ReminderEditor(initial: Reminder, back: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var r by remember { mutableStateOf(initial) }
    var wheel by remember { mutableStateOf(initial.id == 0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val start = at(r.start)
    fun setStart(t: LocalDateTime) { r = r.copy(start = ms(t)) }
    val save = {
        val now = System.currentTimeMillis()
        var saved: Reminder? = null
        Store.update { d ->
            val id = if (r.id == 0) d.nextId else r.id
            val x = r.copy(id = id, done = false).rescheduled(now)
            saved = x
            d.copy(nextId = maxOf(d.nextId, id + 1), reminders = d.reminders.filter { it.id != id } + x)
        }
        saved?.let { Alarms.sync(ctx, it); if (it.nextAt == null) Toast.makeText(ctx, "Saved, but that time has passed", Toast.LENGTH_LONG).show() }
        Unit
    }
    EditorScaffold(if (r.id == 0) "New Reminder" else "Reminder", back, save) {
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        FieldRow(r.title, "Title") { r = r.copy(title = it) }
        Row(Modifier.fillMaxWidth().background(cs.surface).clickable { wheel = !wheel }.padding(16.dp)) {
            Text("Alarm", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(start.format(longFmt), color = if (wheel) cs.primary else cs.onSurface, style = MaterialTheme.typography.bodyLarge)
        }
        if (wheel) {
            Row(Modifier.background(cs.surface).border(1.dp, cs.primary)) {
                listOf(7, 12, 17, 21).forEach { h ->
                    Text(
                        "%02d:00".format(h),
                        Modifier.weight(1f).border(0.5.dp, cs.primary).clickable {
                            var t = start.toLocalDate().atTime(h, 0)
                            if (t < LocalDateTime.now()) t = t.plusDays(1)
                            setStart(t)
                        }.padding(vertical = 10.dp),
                        color = cs.primary, textAlign = TextAlign.Center,
                    )
                }
            }
            Box(Modifier.background(cs.surface)) { DateTimeWheel(start) { setStart(it) } }
            TextButton({ dialog = "type" }, Modifier.fillMaxWidth().background(cs.surface)) { Text("Type date and time") }
        }
        HorizontalDivider()

        SectionHeader("Do-it / Delay-it options")
        NavRow("Nag-Me", if (r.nag) "Every ${r.nagEveryMin} min" else "(Off)") { dialog = "nag" }
        NavRow("Actionable Snooze", "(${r.snoozeMin} min)") { dialog = "snooze" }

        SectionHeader("Repeat options")
        NavRow("Repeat", if (r.windowFrom != null) "DayMinder" else r.describe()) { dialog = "repeat" }
        NavRow("DayMinder", r.windowFrom?.let { "${hm(it)}-${hm(r.windowTo ?: it)}" } ?: "Off") { dialog = "dayminder" }

        SectionHeader("Alarm options")
        MelodyRow(r.sound) { r = r.copy(sound = it) }
        SwitchRow("Alert Message", r.fullScreen) { r = r.copy(fullScreen = it) }
        SwitchRow("Sound", r.soundOn) { r = r.copy(soundOn = it) }

        SectionHeader("Pre-alarm options")
        val lead = r.start - r.preMin * 60_000L
        NavRow(
            "Lead Time", if (r.preMin > 0) at(lead).format(longFmt) else "Off",
            if (r.preMin > 0 && lead < System.currentTimeMillis()) cs.error else cs.onSurfaceVariant,
        ) { dialog = "lead" }

        SectionHeader("Category")
        FieldRow(r.category, "None") { r = r.copy(category = it) }
        SectionHeader("Notes")
        FieldRow(r.notes, "", minLines = 4) { r = r.copy(notes = it) }

        if (r.id != 0) DeleteRow("Delete Reminder") {
            Store.update { d -> d.copy(reminders = d.reminders.filter { it.id != r.id }) }
            Alarms.set(ctx, "r", r.id, null)
            ctx.getSystemService(NotificationManager::class.java).cancel(r.id)
            back()
        }
        Spacer(Modifier.height(32.dp))
    }

    val close = { dialog = null }
    when (dialog) {
        "nag" -> OptionsDialog("Nag-Me", close) {
            Text("Keeps alerting until you mark it done.")
            SwitchRow("Nag-Me", r.nag) { r = r.copy(nag = it) }
            if (r.nag) {
                NumField("Every N minutes", r.nagEveryMin) { r = r.copy(nagEveryMin = it.coerceAtLeast(1)) }
                NumField("Stop after N nags (0 = until done)", r.nagTimes) { r = r.copy(nagTimes = it) }
                NumField("Stop after N minutes (0 = until done)", r.nagForMin) { r = r.copy(nagForMin = it) }
            }
        }
        "snooze" -> OptionsDialog("Actionable Snooze", close) {
            Text("Snooze length for the notification's Snooze button.")
            NumField("Minutes", r.snoozeMin) { r = r.copy(snoozeMin = it.coerceAtLeast(1)) }
        }
        "repeat" -> OptionsDialog("Repeat", close) {
            Every.entries.forEach { e ->
                val label = if (e == Every.ONCE) "Never" else e.name.lowercase().replaceFirstChar(Char::uppercase)
                RadioRow(label, r.rule == e && r.windowFrom == null) { r = r.copy(rule = e, windowFrom = null, windowTo = null) }
            }
            if (r.rule != Every.ONCE && r.windowFrom == null) {
                NumField("Every how many ${r.rule.name.lowercase()}", r.every) { r = r.copy(every = it.coerceAtLeast(1)) }
                Weekdays(r.weekdays) { r = r.copy(weekdays = it) }
            }
        }
        "dayminder" -> OptionsDialog("DayMinder", close) {
            Text("Repeats through the day between two times.")
            SwitchRow("DayMinder", r.windowFrom != null) { on ->
                r = if (on) r.copy(
                    rule = Every.MINUTES, every = if (r.rule == Every.MINUTES) r.every else 60,
                    windowFrom = 9 * 60, windowTo = 21 * 60,
                ) else r.copy(rule = Every.ONCE, windowFrom = null, windowTo = null)
            }
            val from = r.windowFrom
            val to = r.windowTo
            if (from != null && to != null) {
                NumField("Every N minutes", r.every) { r = r.copy(every = it.coerceAtLeast(1)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton("From", from) { r = r.copy(windowFrom = it) }
                    TimeButton("To", to) { r = r.copy(windowTo = it) }
                }
                Weekdays(r.weekdays) { r = r.copy(weekdays = it) }
            }
        }
        "lead" -> OptionsDialog("Lead Time", close) {
            Text("An early warning before the alarm.")
            listOf(0, 5, 10, 15, 30, 60, 120, 1440).forEach { m ->
                val label = when {
                    m == 0 -> "Off"
                    m < 60 -> "$m minutes before"
                    m == 60 -> "1 hour before"
                    m < 1440 -> "${m / 60} hours before"
                    else -> "1 day before"
                }
                RadioRow(label, r.preMin == m) { r = r.copy(preMin = m) }
            }
        }
        "type" -> TypeDateDialog(start, close) { setStart(it) }
        else -> {}
    }
}
