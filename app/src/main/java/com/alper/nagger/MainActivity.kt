package com.alper.nagger

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private sealed interface Screen
private data object Home : Screen
private data object Prefs : Screen
private data class EditR(val r: Reminder) : Screen
private data class EditT(val t: Timer) : Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.syncAll(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent {
            Themed {
                var screen by remember { mutableStateOf<Screen>(Home) }
                val back = { screen = Home }
                BackHandler(screen != Home, back)
                when (val s = screen) {
                    Home -> HomeScreen { screen = it }
                    Prefs -> PrefsScreen(back)
                    is EditR -> ReminderEditor(s.r, back)
                    is EditT -> TimerEditor(s.t, back)
                }
            }
        }
    }
}

@Composable
fun Themed(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

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
private val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
private fun at(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
private fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
private val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

private fun Reminder.describe(): String {
    if (rule == Every.ONCE) return "Once"
    val unit = rule.name.lowercase().let { if (every == 1) it.dropLast(1) else it }
    var s = if (every == 1) "Every $unit" else "Every $every $unit"
    if (windowFrom != null && windowTo != null) s += " ${hm(windowFrom)}-${hm(windowTo)}"
    if (weekdays.isNotEmpty()) s += " " + weekdays.sorted().joinToString(" ") { dayNames[it - 1] }
    if (nag) s += ", nags"
    return s
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var bigId by remember { mutableStateOf<Int?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(250); value = System.currentTimeMillis() }
    }
    Scaffold(
        topBar = {
            TopAppBar({ Text("Nagger") }, actions = {
                IconButton({ open(Prefs) }) { Icon(Icons.Default.Settings, "Settings") }
            })
        },
        floatingActionButton = {
            FloatingActionButton({
                if (tab == 0) {
                    val start = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
                    open(EditR(Reminder(category = cat.orEmpty(), start = start.atZone(zone).toInstant().toEpochMilli())))
                } else open(EditT(Timer(category = cat.orEmpty())))
            }) { Icon(Icons.Default.Add, "Add") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Reminders") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Timers") })
            }
            val cats = (d.reminders.map { it.category } + d.timers.map { it.category }).filter { it.isNotBlank() }.distinct().sorted()
            if (cats.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), Arrangement.spacedBy(8.dp)) {
                    FilterChip(cat == null, { cat = null }, { Text("All") })
                    cats.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                }
            }
            LazyColumn {
                if (tab == 0) {
                    val list = d.reminders.filter { cat == null || it.category == cat }
                        .sortedWith(compareBy<Reminder> { !it.enabled }.thenBy { it.fireAt() ?: Long.MAX_VALUE })
                    items(list, key = { it.id }) { r ->
                        ListItem(
                            headlineContent = { Text(r.title.ifBlank { "(untitled)" }) },
                            supportingContent = {
                                val next = r.fireAt()?.let { " · next " + at(it).format(whenFmt) } ?: ""
                                Text(r.describe() + next)
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (r.activeSince != null || r.nagAt != null) TextButton({ act(ctx, "done", "r", r.id) }) { Text("Done") }
                                    Switch(r.enabled, { on -> act(ctx, if (on) "on" else "off", "r", r.id) })
                                }
                            },
                            modifier = Modifier.clickable { open(EditR(r)) },
                        )
                        HorizontalDivider()
                    }
                } else {
                    items(d.timers.filter { cat == null || it.category == cat }, key = { it.id }) { t ->
                        val shown = if (t.countUp) t.elapsed(now) else t.stepMs() - t.elapsed(now)
                        ListItem(
                            headlineContent = { Text(t.name.ifBlank { "(untitled)" }) },
                            supportingContent = {
                                Text(fmt(shown), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { bigId = t.id })
                            },
                            overlineContent = if (t.steps.size > 1 && !t.countUp) ({ Text("Step ${t.idx + 1}/${t.steps.size}") }) else null,
                            trailingContent = {
                                Row {
                                    if (t.runningSince != null) TextButton({ act(ctx, "pause", "t", t.id) }) { Text("Pause") }
                                    else TextButton({ act(ctx, "start", "t", t.id) }) { Text("Start") }
                                    TextButton({ act(ctx, "reset", "t", t.id) }) { Text("Reset") }
                                }
                            },
                            modifier = Modifier.clickable { open(EditT(t)) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    bigId?.let { id ->
        val t = d.timers.find { it.id == id } ?: return@let
        Dialog({ bigId = null }) {
            val shown = if (t.countUp) t.elapsed(now) else t.stepMs() - t.elapsed(now)
            Text(fmt(shown), fontSize = 80.sp, modifier = Modifier.clickable { bigId = null }, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(title: String, back: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, body: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar({ Text(title) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = {
            if (onDelete != null) IconButton({ onDelete(); back() }) { Icon(Icons.Default.Delete, "Delete") }
            TextButton({ onSave(); back() }) { Text("Save") }
        })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { body() }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, minutes: Int, onPick: (Int) -> Unit) {
    var openDlg by remember { mutableStateOf(false) }
    OutlinedButton({ openDlg = true }) { Text("$label ${hm(minutes)}") }
    if (openDlg) {
        val st = rememberTimePickerState(minutes / 60, minutes % 60, true)
        AlertDialog({ openDlg = false },
            confirmButton = { TextButton({ onPick(st.hour * 60 + st.minute); openDlg = false }) { Text("OK") } },
            text = { TimePicker(st) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var openDlg by remember { mutableStateOf(false) }
    OutlinedButton({ openDlg = true }) { Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))) }
    if (openDlg) {
        val st = rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog({ openDlg = false }, confirmButton = {
            TextButton({
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                openDlg = false
            }) { Text("OK") }
        }) { DatePicker(st) }
    }
}

@Composable
private fun SoundButton(uri: String?, onPick: (String?) -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        @Suppress("DEPRECATION")
        if (res.resultCode == Activity.RESULT_OK) onPick(res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString())
    }
    val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val name = remember(uri) { runCatching { RingtoneManager.getRingtone(ctx, uri?.let(Uri::parse) ?: def).getTitle(ctx) }.getOrNull() ?: "Default" }
    OutlinedButton({
        launcher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, def)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri?.let(Uri::parse) ?: def))
    }) { Text("Sound: $name") }
}

private fun cancelAlerts(ctx: Context, id: Int) =
    ctx.getSystemService(NotificationManager::class.java).run { cancel(id); cancel(TIMER_NOTIF + id) }

@Composable
private fun ReminderEditor(initial: Reminder, back: () -> Unit) {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf(initial) }
    val start = at(r.start)
    fun setStart(t: LocalDateTime) { r = r.copy(start = t.atZone(zone).toInstant().toEpochMilli()) }
    val save = {
        val now = System.currentTimeMillis()
        var saved: Reminder? = null
        Store.update { d ->
            val id = if (r.id == 0) d.nextId else r.id
            val x = r.copy(id = id).rescheduled(now)
            saved = x
            d.copy(nextId = maxOf(d.nextId, id + 1), reminders = d.reminders.filter { it.id != id } + x)
        }
        saved?.let { Alarms.sync(ctx, it); if (it.nextAt == null) Toast.makeText(ctx, "Saved, but it never fires again", Toast.LENGTH_LONG).show() }
        Unit
    }
    val delete = if (r.id == 0) null else ({
        Store.update { d -> d.copy(reminders = d.reminders.filter { it.id != r.id }) }
        Alarms.set(ctx, "r", r.id, null)
        cancelAlerts(ctx, r.id)
    })
    EditorScaffold(if (r.id == 0) "New reminder" else "Edit reminder", back, save, delete) {
        OutlinedTextField(r.title, { r = r.copy(title = it) }, Modifier.fillMaxWidth(), label = { Text("Title") })
        OutlinedTextField(r.notes, { r = r.copy(notes = it) }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
        OutlinedTextField(r.category, { r = r.copy(category = it) }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton(start.toLocalDate()) { setStart(it.atTime(start.toLocalTime())) }
            TimeButton("at", start.hour * 60 + start.minute) { setStart(start.toLocalDate().atTime(it / 60, it % 60)) }
        }
        Text("Repeat")
        Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            Every.entries.forEach { e ->
                FilterChip(r.rule == e, { r = r.copy(rule = e) }, { Text(e.name.lowercase().replaceFirstChar(Char::uppercase)) })
            }
        }
        if (r.rule != Every.ONCE) {
            NumField("Every how many ${r.rule.name.lowercase()}", r.every) { r = r.copy(every = it.coerceAtLeast(1)) }
            Text("Only on (none = every day)")
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(4.dp)) {
                dayNames.forEachIndexed { i, n ->
                    val day = i + 1
                    FilterChip(day in r.weekdays, { r = r.copy(weekdays = if (day in r.weekdays) r.weekdays - day else r.weekdays + day) }, { Text(n) })
                }
            }
        }
        if (r.rule == Every.MINUTES || r.rule == Every.HOURS) {
            SwitchRow("Only between (DayMinder)", r.windowFrom != null) { on ->
                r = if (on) r.copy(windowFrom = 9 * 60, windowTo = 21 * 60) else r.copy(windowFrom = null, windowTo = null)
            }
            val from = r.windowFrom
            val to = r.windowTo
            if (from != null && to != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeButton("from", from) { r = r.copy(windowFrom = it) }
                TimeButton("to", to) { r = r.copy(windowTo = it) }
            }
        }
        NumField("Pre-alarm, minutes before (0 = off)", r.preMin) { r = r.copy(preMin = it) }
        SwitchRow("Nag me until done", r.nag) { r = r.copy(nag = it) }
        if (r.nag) {
            NumField("Nag every N minutes", r.nagEveryMin) { r = r.copy(nagEveryMin = it.coerceAtLeast(1)) }
            NumField("Stop after N nags (0 = until done)", r.nagTimes) { r = r.copy(nagTimes = it) }
            NumField("Stop after N minutes (0 = until done)", r.nagForMin) { r = r.copy(nagForMin = it) }
        }
        SwitchRow("Full-screen alarm (off = notification only)", r.fullScreen) { r = r.copy(fullScreen = it) }
        if (r.fullScreen) SoundButton(r.sound) { r = r.copy(sound = it) }
    }
}

@Composable
private fun TimerEditor(initial: Timer, back: () -> Unit) {
    val ctx = LocalContext.current
    var t by remember { mutableStateOf(initial) }
    val save = {
        var saved: Timer? = null
        Store.update { d ->
            val id = if (t.id == 0) d.nextId else t.id
            val x = t.copy(id = id).reset()
            saved = x
            d.copy(nextId = maxOf(d.nextId, id + 1), timers = d.timers.filter { it.id != id } + x)
        }
        saved?.let { Alarms.sync(ctx, it) }
        Unit
    }
    val delete = if (t.id == 0) null else ({
        Store.update { d -> d.copy(timers = d.timers.filter { it.id != t.id }) }
        Alarms.set(ctx, "t", t.id, null)
        cancelAlerts(ctx, t.id)
    })
    EditorScaffold(if (t.id == 0) "New timer" else "Edit timer", back, save, delete) {
        OutlinedTextField(t.name, { t = t.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        OutlinedTextField(t.category, { t = t.copy(category = it) }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
        SwitchRow("Count up (stopwatch)", t.countUp) { t = t.copy(countUp = it) }
        if (!t.countUp) {
            t.steps.forEachIndexed { i, s ->
                fun set(x: Step) { t = t.copy(steps = t.steps.toMutableList().also { it[i] = x }) }
                // key on size so fields re-read their values after a step is removed
                androidx.compose.runtime.key(i, t.steps.size) {
                    Text(if (t.steps.size > 1) "Step ${i + 1}" else "Duration")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumField("h", (s.sec / 3600).toInt(), Modifier.weight(1f)) { set(s.copy(sec = (it.coerceAtMost(999) * 3600L) + s.sec % 3600)) }
                        NumField("m", (s.sec / 60 % 60).toInt(), Modifier.weight(1f)) { set(s.copy(sec = s.sec - s.sec / 60 % 60 * 60 + it.coerceAtMost(59) * 60L)) }
                        NumField("s", (s.sec % 60).toInt(), Modifier.weight(1f)) { set(s.copy(sec = s.sec - s.sec % 60 + it.coerceAtMost(59))) }
                        if (t.steps.size > 1) IconButton({ t = t.copy(steps = t.steps.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Close, "Remove step") }
                    }
                    OutlinedTextField(s.msg, { set(s.copy(msg = it)) }, Modifier.fillMaxWidth(), label = { Text("Message") }, singleLine = true)
                }
            }
            TextButton({ t = t.copy(steps = t.steps + Step()) }) { Text("Add step (queue)") }
            if (t.steps.size > 1) NumField("Pause between steps, seconds", t.gapSec.toInt()) { t = t.copy(gapSec = it.toLong()) }
            SoundButton(t.sound) { t = t.copy(sound = it) }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun PrefsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
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
        }.getOrElse { Toast.makeText(ctx, "Not a Nagger backup: ${it.message}", Toast.LENGTH_LONG).show(); null }
    }
    EditorScaffold("Settings", back, {}, null) {
        NumField("Default snooze, minutes", d.snoozeMin) { v -> Store.update { it.copy(snoozeMin = v.coerceAtLeast(1)) } }
        NumField("Silence ringing after, minutes", d.silenceMin) { v -> Store.update { it.copy(silenceMin = v.coerceAtLeast(1)) } }
        HorizontalDivider()
        Button({ export.launch("nagger-${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) { Text("Export backup") }
        OutlinedButton({ import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.fillMaxWidth()) { Text("Import backup") }
        Text("Daily automatic backups go to Download/Nagger (last 7 kept).", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        fun open(action: String) = ctx.startActivity(Intent(action, Uri.parse("package:${ctx.packageName}")))
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName))
            OutlinedButton({ open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }, Modifier.fillMaxWidth()) { Text("Disable battery optimization") }
        if (Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent())
            OutlinedButton({ open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) }, Modifier.fillMaxWidth()) { Text("Allow full-screen alarms") }
        if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
            OutlinedButton({ open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }, Modifier.fillMaxWidth()) { Text("Allow exact alarms") }
        OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }, Modifier.fillMaxWidth()) {
            Text("Notification settings")
        }
    }
    pending?.let { data ->
        AlertDialog({ pending = null },
            title = { Text("Replace everything?") },
            text = { Text("${data.reminders.size} reminders and ${data.timers.size} timers will replace the current data.") },
            confirmButton = {
                TextButton({
                    Alarms.cancelAll(ctx)
                    Store.update { data }
                    Alarms.syncAll(ctx)
                    pending = null
                    Toast.makeText(ctx, "Imported", Toast.LENGTH_SHORT).show()
                }) { Text("Replace") }
            },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } })
    }
}
