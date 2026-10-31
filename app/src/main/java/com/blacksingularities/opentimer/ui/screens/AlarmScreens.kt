package com.blacksingularities.opentimer.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.alarm.Scheduler
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.Challenge
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.ring.RingService
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Fmt
import com.blacksingularities.opentimer.ui.Route
import com.blacksingularities.opentimer.ui.components.ActionRow
import com.blacksingularities.opentimer.ui.components.Chips
import com.blacksingularities.opentimer.ui.components.DarkField
import com.blacksingularities.opentimer.ui.components.Group
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.components.SwitchRow
import com.blacksingularities.opentimer.ui.theme.BlockShape
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive

@Composable
fun AlarmsTab(vm: AppViewModel, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val alarms by Store.alarms.collectAsState()
    val now = rememberNow(30_000)
    val next = TimeMath.nextOf(alarms, now)
    val canExact = remember(now) { Scheduler.canExact(context) }
    val notifOk = remember(now) { Notifications.allowed(context) }
    val fullScreenOk = remember(now) {
        Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
    }

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TabHeader(
                if (next != null) tr("Najbliższy {0} · {1}", Fmt.dayAndTime(context, next.second), Fmt.until(next.second - now)) else tr("Brak włączonych budzików"),
            ) {
                RoundIcon(Icons.Rounded.Add, tr("Nowy budzik")) { vm.open(Route.AlarmEditor(null)) }
            }
        }
        if (!notifOk || !canExact || !fullScreenOk) item {
            Warning(
                when {
                    !notifOk -> tr("Bez powiadomień budzik nie pokaże ekranu dzwonienia.")
                    !canExact -> tr("Bez zgody na dokładne alarmy budzik może się spóźnić.")
                    else -> tr("Zezwól na pełny ekran, aby budzik pokazywał się na blokadzie.")
                },
            ) {
                when {
                    !notifOk -> onRequestNotifications()
                    !canExact && Build.VERSION.SDK_INT >= 31 -> vm.openIntent(
                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                    )
                    Build.VERSION.SDK_INT >= 34 -> vm.openIntent(
                        Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName)),
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { m ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))
                            .reactive(tr("Drzemka {0} min", m)) {
                                val a = Actions.nap(context, m)
                                vm.showSnack(tr("Budzik o {0}", Fmt.alarmTime(context, a))) { Actions.deleteAlarm(context, a.id) }
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Snooze, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(Fmt.durationShort(m * 60_000L), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        items(alarms, key = { it.id }) { a -> AlarmCard(vm, a, now) }
        if (alarms.isEmpty()) item {
            PrimaryCard(Icons.Rounded.Alarm, tr("Nowy budzik"), tr("Dotknij, aby ustawić pierwszy budzik")) { vm.open(Route.AlarmEditor(null)) }
        }
        if (alarms.size > 1) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                PillButton(tr("Włącz wszystkie"), Modifier.weight(1f), primary = false) { Actions.setAllAlarms(context, true) }
                PillButton(tr("Wyłącz wszystkie"), Modifier.weight(1f), primary = false) {
                    val before = Store.alarms.value
                    Actions.setAllAlarms(context, false)
                    vm.showSnack(tr("Wyłączono wszystkie budziki")) { before.forEach { Actions.restoreAlarm(context, it) } }
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Record.copy(alpha = 0.16f))
            .reactive(text, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = Palette.Record)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 13.sp)
            Text(tr("Dotknij, aby naprawić"), fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}

@Composable
private fun AlarmCard(vm: AppViewModel, a: Alarm, now: Long) {
    val context = LocalContext.current
    val next = TimeMath.nextTrigger(a, now)
    val (time, ampm) = Fmt.hm(context, a.hour, a.minute)
    val dim = if (a.enabled) Color.White else Palette.TextFaint
    Tile(active = a.enabled, modifier = Modifier.reactive(tr("Edytuj budzik"), onLongPress = { duplicate(vm, a) }) { vm.open(Route.AlarmEditor(a.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (a.label.isNotBlank()) Text(a.label, fontSize = 13.sp, color = Palette.TextDim, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, fontSize = 44.sp, fontWeight = FontWeight.Light, color = dim)
                    if (ampm != null) Text(ampm, fontSize = 14.sp, color = dim, modifier = Modifier.padding(start = 4.dp, bottom = 9.dp))
                }
                Text(
                    buildString {
                        append(Fmt.repeat(a))
                        if (next != null && a.enabled) append(" · ").append(Fmt.until(next - now))
                    },
                    fontSize = 12.sp, color = Palette.TextDim,
                )
            }
            Switch(
                checked = a.enabled,
                onCheckedChange = { on ->
                    Actions.setAlarmEnabled(context, a.id, on)
                    if (on) TimeMath.nextTrigger(a.copy(enabled = true, skipUntil = 0, snoozedUntil = 0), System.currentTimeMillis())?.let {
                        vm.showSnack(tr("Budzik zadzwoni {0}", Fmt.until(it - System.currentTimeMillis())))
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Palette.Ink),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Fmt.weekOrder().forEach { d ->
                val on = a.repeatsOn(d.value)
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = if (a.enabled) 1f else 0.4f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Fmt.dayLetter(d), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) Palette.Ink else Palette.TextFaint)
                }
            }
            Spacer(Modifier.weight(1f))
            if (a.challenge != Challenge.NONE) Icon(Icons.Rounded.Calculate, tr("Zadanie przy wyłączaniu"), tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            if (a.enabled && a.days != 0) {
                if (a.skipUntil > now) {
                    Text(
                        tr("Pominięty · przywróć"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                        modifier = Modifier.clip(BlockShape).background(Color.White).reactive(tr("Przywróć")) { Actions.cancelSkip(context, a.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                } else {
                    RoundIcon(Icons.Rounded.SkipNext, tr("Pomiń najbliższy"), size = 32.dp) {
                        Actions.skipNext(context, a.id)
                        vm.showSnack(tr("Najbliższe dzwonienie pominięte")) { Actions.cancelSkip(context, a.id) }
                    }
                }
            }
            if (a.snoozedUntil > now) {
                Text(tr("Drzemka do {0}", Fmt.time(context, a.snoozedUntil)), fontSize = 11.sp, color = Palette.TextDim)
            }
        }
    }
}

private fun duplicate(vm: AppViewModel, a: Alarm) {
    val copy = a.copy(id = Store.nextId(), enabled = false, skipUntil = 0, snoozedUntil = 0, snoozeCount = 0)
    Actions.saveAlarm(vm.ctx, copy)
    vm.showSnack(tr("Utworzono kopię budzika")) { Actions.deleteAlarm(vm.ctx, copy.id) }
}

/** Edytor budzika: godzina, dni, etykieta, dźwięk, wibracja, narastanie, drzemka, zadanie. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(vm: AppViewModel, route: Route.AlarmEditor) {
    val context = LocalContext.current
    val existing = remember(route) { route.alarmId?.let { id -> Store.alarms.value.firstOrNull { it.id == id } } }
    val initial = remember(route) {
        existing ?: java.time.LocalTime.now().plusHours(1).let { t ->
            Actions.newAlarm(if (route.hour >= 0) route.hour else t.hour, if (route.hour >= 0) route.minute else 0).copy(days = 0)
        }
    }
    var a by remember(route) { mutableStateOf(initial) }
    val picker = rememberTimePickerState(initial.hour, initial.minute, Fmt.is24(context))
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.release() } }

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            a = a.copy(sound = uri?.toString() ?: RingService.SILENT)
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            a = a.copy(sound = uri.toString())
        }
    }

    fun save() {
        val final = a.copy(hour = picker.hour, minute = picker.minute, enabled = true)
        Actions.saveAlarm(context, final)
        TimeMath.nextTrigger(final, System.currentTimeMillis())?.let { vm.showSnack(tr("Budzik zadzwoni {0}", Fmt.until(it - System.currentTimeMillis()))) }
        vm.back()
    }

    SubScreen(
        if (existing == null) tr("Nowy budzik") else tr("Edytuj budzik"),
        onBack = { vm.back() },
        actions = {
            if (existing != null) RoundIcon(Icons.Rounded.Delete, tr("Usuń budzik")) {
                Actions.deleteAlarm(context, existing.id)
                vm.back()
                vm.showSnack(tr("Usunięto budzik")) { Actions.restoreAlarm(context, existing) }
            }
            PillButton(tr("Zapisz")) { save() }
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Palette.PanelHigh,
                    selectorColor = Color.White,
                    containerColor = Palette.Ink,
                    clockDialSelectedContentColor = Palette.Ink,
                    clockDialUnselectedContentColor = Color.White,
                    timeSelectorSelectedContainerColor = Color.White,
                    timeSelectorUnselectedContainerColor = Palette.PanelHigh,
                    timeSelectorSelectedContentColor = Palette.Ink,
                    timeSelectorUnselectedContentColor = Color.White,
                    periodSelectorSelectedContainerColor = Color.White,
                    periodSelectorSelectedContentColor = Palette.Ink,
                    periodSelectorUnselectedContentColor = Color.White,
                ),
            )
        }
        val preview2 = a.copy(hour = picker.hour, minute = picker.minute, enabled = true, skipUntil = 0, snoozedUntil = 0)
        TimeMath.nextTrigger(preview2, rememberNow(30_000))?.let {
            Text(
                tr("Zadzwoni {0} · {1}", Fmt.dayAndTime(context, it), Fmt.until(it - System.currentTimeMillis())),
                fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Group(tr("Powtarzanie")) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Fmt.weekOrder().forEach { d ->
                    val bit = 1 shl (d.value - 1)
                    val on = a.days and bit != 0
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.08f))
                            .reactive(Fmt.dayShort(d)) { a = a.copy(days = a.days xor bit, oneShotDate = 0) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(Fmt.dayLetter(d), fontWeight = FontWeight.Bold, color = if (on) Palette.Ink else Color.White)
                    }
                }
            }
            Chips(
                Icons.Rounded.Repeat, tr("Szybki wybór"),
                listOf(0, Alarm.WEEKDAYS, Alarm.WEEKEND, Alarm.EVERY_DAY), a.days,
                label = {
                    when (it) {
                        0 -> tr("Jednorazowo"); Alarm.WEEKDAYS -> tr("Dni robocze"); Alarm.WEEKEND -> tr("Weekend"); else -> tr("Codziennie")
                    }
                },
            ) { a = a.copy(days = it, oneShotDate = 0) }
        }

        Group(tr("Opis")) {
            Box(Modifier.padding(10.dp)) { DarkField(a.label, { a = a.copy(label = it.take(60)) }, tr("Etykieta (np. Praca, Leki)")) }
        }

        Group(tr("Dźwięk")) {
            ActionRow(Icons.Rounded.MusicNote, tr("Dzwonek"), soundName(context, a.sound)) {
                ringtone.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, a.sound.takeIf { it.isNotBlank() && it != RingService.SILENT }?.let(Uri::parse)),
                )
            }
            ActionRow(Icons.Rounded.MusicNote, tr("Własny plik audio"), tr("Dowolna muzyka z telefonu")) { file.launch(arrayOf("audio/*")) }
            ActionRow(
                if (preview == null) Icons.Rounded.PlayArrow else Icons.Rounded.Stop,
                if (preview == null) tr("Odsłuchaj") else tr("Zatrzymaj odsłuch"),
            ) {
                val p = preview
                if (p != null) { p.release(); preview = null } else preview = RingService.preview(context, a.sound)
            }
            SliderRow(Icons.Rounded.VolumeUp, tr("Głośność"), "${a.volume}%", a.volume, 10..100) { a = a.copy(volume = it) }
            Chips(Icons.Rounded.TrendingUp, tr("Narastanie głośności"), listOf(0, 15, 30, 60, 120, 300), a.rampSeconds, label = {
                if (it == 0) tr("Od razu") else if (it < 60) "$it s" else tr("{0} min", it / 60)
            }) { a = a.copy(rampSeconds = it) }
            SwitchRow(Icons.Rounded.Vibration, tr("Wibracja"), null, a.vibrate) { a = a.copy(vibrate = it) }
        }

        Group(tr("Drzemka")) {
            Chips(Icons.Rounded.Snooze, tr("Długość drzemki"), listOf(1, 3, 5, 10, 15, 20, 30), a.snoozeMinutes, label = { tr("{0} min", it) }) {
                a = a.copy(snoozeMinutes = it)
            }
            Chips(Icons.Rounded.AlarmOn, tr("Limit drzemek"), listOf(0, 1, 2, 3, 5), a.maxSnoozes, label = {
                if (it == 0) tr("Bez limitu") else "$it×"
            }) { a = a.copy(maxSnoozes = it) }
        }

        Group(tr("Wyłączanie")) {
            Chips(Icons.Rounded.Calculate, tr("Zadanie przed wyłączeniem"), Challenge.entries.toList(), a.challenge, label = {
                when (it) {
                    Challenge.NONE -> tr("Brak"); Challenge.MATH_EASY -> tr("Proste działania")
                    Challenge.MATH_HARD -> tr("Trudne działania"); Challenge.TYPE_CODE -> tr("Przepisz kod")
                }
            }, hint = tr("Pomaga naprawdę się obudzić – wyłączenie wymaga skupienia.")) { a = a.copy(challenge = it) }
            if (a.challenge != Challenge.NONE) {
                Chips(Icons.Rounded.NotificationsActive, tr("Liczba zadań"), listOf(1, 2, 3, 5), a.challengeCount, label = { "$it" }) {
                    a = a.copy(challengeCount = it)
                }
            }
        }

        if (existing != null) {
            Group(null) {
                ActionRow(Icons.Rounded.ContentCopy, tr("Duplikuj budzik")) { duplicate(vm, existing); vm.back() }
                ActionRow(Icons.Rounded.AlarmOff, tr("Usuń budzik"), danger = true) {
                    Actions.deleteAlarm(context, existing.id)
                    vm.back()
                    vm.showSnack(tr("Usunięto budzik")) { Actions.restoreAlarm(context, existing) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PillButton(tr("Zapisz budzik"), Modifier.fillMaxWidth()) { save() }
    }
}

fun soundName(context: android.content.Context, sound: String): String = when {
    sound == RingService.SILENT -> tr("Bez dźwięku")
    sound.isBlank() -> tr("Domyślny dźwięk budzika")
    else -> runCatching {
        val uri = Uri.parse(sound)
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            ?: uri.lastPathSegment
    }.getOrNull() ?: tr("Wybrany dźwięk")
}

@Composable
fun SliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.blacksingularities.opentimer.ui.components.RowIcon(icon)
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(valueText, fontSize = 13.sp, color = Palette.TextDim)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.padding(start = 40.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
        )
    }
}

