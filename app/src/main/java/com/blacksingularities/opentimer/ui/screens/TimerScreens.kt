package com.blacksingularities.opentimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.data.TimerItem
import com.blacksingularities.opentimer.data.TimerPreset
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ring.RingService
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Fmt
import com.blacksingularities.opentimer.ui.Route
import com.blacksingularities.opentimer.ui.UpdateBanner
import com.blacksingularities.opentimer.ui.components.DarkDialog
import com.blacksingularities.opentimer.ui.components.DarkField
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive

/**
 * Minutniki: dowolna liczba naraz, każdy z własnym czasem i nazwą. Nowy czas wpisuje się
 * klawiaturą (jak w zegarku: „130” to 1 min 30 s) albo wybiera z zapisanych szybkich czasów.
 */
@Composable
fun TimersTab(vm: AppViewModel, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val timers by Store.timers.collectAsState()
    val presets by Store.presets.collectAsState()
    var digits by remember { mutableStateOf("") }
    var keypad by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<TimerItem?>(null) }
    val typed = TimeMath.digitsToMs(digits)
    val showKeypad = keypad || timers.isEmpty()

    LaunchedPermission(timers.isNotEmpty(), onRequestNotifications)

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TabHeader(
            when {
                timers.isEmpty() -> tr("Wpisz czas i dotknij start")
                else -> tr("Działających: {0}", timers.count { it.running })
            },
        ) {
            RoundIcon(if (showKeypad && timers.isNotEmpty()) Icons.Rounded.Close else Icons.Rounded.Add, tr("Nowy minutnik"), size = 38.dp) {
                keypad = !showKeypad
                digits = ""
            }
            RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), size = 38.dp, badge = if (vm.updatePending) "1" else null) { vm.open(Route.Settings) }
        }
        UpdateBanner(vm)

        if (showKeypad) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    TimeMath.clock(typed, forceHours = true),
                    fontSize = 46.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                    color = if (typed > 0) Color.White else Palette.TextFaint,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Keypad(
                    onDigit = { d -> if (digits.length < 6) digits = (digits + d).trimStart('0') },
                    onBackspace = { digits = digits.dropLast(1) },
                    onClear = { digits = "" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr("Start"), modifier = Modifier.weight(1f), enabled = typed > 0) {
                        Actions.addTimer(context, typed)
                        digits = ""
                        keypad = false
                        onRequestNotifications()
                    }
                    RoundIcon(Icons.Rounded.Bookmark, tr("Zapisz czas"), enabled = typed > 0) {
                        Store.updatePresets { list -> (list + TimerPreset("", typed)).distinctBy { it.durationMs }.sortedBy { it.durationMs } }
                        vm.showSnack(tr("Zapisano szybki czas {0}", Fmt.durationShort(typed)))
                    }
                }
                SectionLabel(tr("Szybkie czasy"))
                PresetGrid(
                    presets = presets,
                    onPick = { p ->
                        Actions.addTimer(context, p.durationMs, p.label)
                        keypad = false
                        digits = ""
                        onRequestNotifications()
                    },
                    onRemove = { p ->
                        Store.updatePresets { list -> list - p }
                        vm.showSnack(tr("Usunięto szybki czas")) { Store.updatePresets { list -> (list + p).sortedBy { it.durationMs } } }
                    },
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (timers.isNotEmpty() && showKeypad) item { SectionLabel(tr("Minutniki")) }
            items(timers, key = { it.id }) { t ->
                TimerCard(
                    vm = vm,
                    timer = t,
                    single = timers.size == 1 && !showKeypad,
                    onRename = { rename = t },
                )
            }
            if (timers.size > 1) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr("Zatrzymaj wszystkie"), modifier = Modifier.weight(1f), primary = false) {
                        timers.forEach { Actions.pauseTimer(context, it.id) }
                    }
                    PillButton(tr("Usuń wszystkie"), modifier = Modifier.weight(1f), primary = false) {
                        val copy = timers
                        copy.forEach { Actions.deleteTimer(context, it.id) }
                        vm.showSnack(tr("Usunięto minutniki")) {
                            copy.forEach { Actions.addTimer(context, it.durationMs, it.label, start = false) }
                        }
                    }
                }
            }
        }
    }

    val editing = rename
    if (editing != null) {
        var label by remember(editing.id) { mutableStateOf(editing.label) }
        DarkDialog(
            title = tr("Nazwa minutnika"),
            onDismiss = { rename = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { rename = null }
                PillButton(tr("Zapisz")) {
                    Actions.renameTimer(context, editing.id, label.trim())
                    rename = null
                }
            },
        ) {
            DarkField(label, { label = it }, tr("Np. herbata, jajka, pranie"))
        }
    }
}

/** Prośba o zgodę na powiadomienia pokazuje się raz, gdy pojawia się pierwszy minutnik. */
@Composable
private fun LaunchedPermission(active: Boolean, onRequest: () -> Unit) {
    LaunchedEffect(active) {
        if (active) onRequest()
    }
}

@Composable
private fun TimerCard(vm: AppViewModel, timer: TimerItem, single: Boolean, onRename: () -> Unit) {
    val context = LocalContext.current
    // Tyknięcie wewnątrz karty – przewijana lista nie przerysowuje się co 200 ms.
    val now = rememberNow(200L)
    val left = timer.remainingAt(now)
    val progress = timer.progressAt(now)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (timer.ringing) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    timer.label.ifBlank { Fmt.durationShort(timer.durationMs) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        timer.ringing -> tr("Czas minął")
                        timer.running -> tr("Koniec o {0}", Fmt.time(context, now + left.coerceAtLeast(0)))
                        else -> tr("Zatrzymany · z {0}", Fmt.durationShort(timer.durationMs))
                    },
                    fontSize = 11.sp,
                    color = Palette.TextDim,
                )
            }
            RoundIcon(Icons.Rounded.Edit, tr("Zmień nazwę"), size = 34.dp, onClick = onRename)
            RoundIcon(Icons.Rounded.Delete, tr("Usuń minutnik"), size = 34.dp) {
                Actions.deleteTimer(context, timer.id)
                vm.showSnack(tr("Usunięto minutnik")) { Actions.addTimer(context, timer.durationMs, timer.label, start = false) }
            }
        }
        if (single) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = progress,
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(0.7f).aspectRatio(1f),
                    stroke = 7.dp,
                ) {
                    Text(
                        TimeMath.countdown(left),
                        fontSize = 40.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                    )
                }
            }
        } else {
            Text(
                TimeMath.countdown(left),
                fontSize = 32.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                color = Color.White,
            )
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (timer.ringing) {
                PillButton(tr("Zatrzymaj"), modifier = Modifier.weight(1f)) {
                    RingService.command(context, RingService.ACTION_DISMISS)
                    Actions.stopRingingTimers(context, listOf(timer.id))
                }
                PillButton(tr("+1 min"), primary = false) { Actions.addToTimer(context, timer.id, 60_000L) }
            } else {
                PillButton(
                    if (timer.running) tr("Pauza") else if (left <= 0) tr("Od nowa") else tr("Wznów"),
                    modifier = Modifier.weight(1f),
                ) {
                    if (timer.running) Actions.pauseTimer(context, timer.id) else Actions.startTimer(context, timer.id)
                }
                PillButton(tr("+1 min"), primary = false) { Actions.addToTimer(context, timer.id, 60_000L) }
                RoundIcon(Icons.Rounded.Replay, tr("Od początku"), size = 38.dp) { Actions.resetTimer(context, timer.id) }
            }
        }
    }
}

@Composable
private fun PresetGrid(presets: List<TimerPreset>, onPick: (TimerPreset) -> Unit, onRemove: (TimerPreset) -> Unit) {
    if (presets.isEmpty()) {
        Text(tr("Zapisz czas gwiazdką, aby mieć go pod ręką"), fontSize = 12.sp, color = Palette.TextDim)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))
                            .reactive(Fmt.durationShort(p.durationMs), onLongPress = { onRemove(p) }) { onPick(p) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(Fmt.durationShort(p.durationMs), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (p.label.isNotBlank()) Text(p.label, fontSize = 10.sp, color = Palette.TextDim, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Klawiatura czasu: cyfry wpadają od prawej (sekundy → minuty → godziny). */
@Composable
private fun Keypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> Key(c.toString(), Modifier.weight(1f)) { onDigit(c) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Key("00", Modifier.weight(1f)) { onDigit('0'); onDigit('0') }
            Key("0", Modifier.weight(1f)) { onDigit('0') }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                RoundIcon(Icons.AutoMirrored.Rounded.Backspace, tr("Usuń cyfrę"), size = 44.dp, onLongPress = onClear, onClick = onBackspace)
            }
        }
    }
}

@Composable
private fun Key(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.07f))
            .reactive(text, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}
