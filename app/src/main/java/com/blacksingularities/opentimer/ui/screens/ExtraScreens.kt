package com.blacksingularities.opentimer.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.CountdownEvent
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.plural
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Fmt
import com.blacksingularities.opentimer.ui.components.DarkDialog
import com.blacksingularities.opentimer.ui.components.DarkField
import com.blacksingularities.opentimer.ui.components.Group
import com.blacksingularities.opentimer.ui.components.InfoRow
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.components.SwitchRow
import com.blacksingularities.opentimer.ui.components.TabChip
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Odliczanie do wydarzeń: urodziny, wyjazdy, terminy. Wydarzenia coroczne liczą się same dalej. */
@Composable
fun CountdownScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val events by Store.events.collectAsState()
    val now = rememberNow(1000L)
    var editing by remember { mutableStateOf<CountdownEvent?>(null) }

    SubScreen(
        tr("Odliczanie"),
        onBack = { vm.back() },
        actions = {
            RoundIcon(Icons.Rounded.Add, tr("Nowe wydarzenie"), size = 38.dp) {
                editing = CountdownEvent(id = Store.nextId(), title = "", at = System.currentTimeMillis() + 86_400_000L)
            }
        },
    ) {
        if (events.isEmpty()) {
            PrimaryCard(Icons.Rounded.Celebration, tr("Dodaj wydarzenie"), tr("Zobacz, ile dni zostało do ważnego dnia")) {
                editing = CountdownEvent(id = Store.nextId(), title = "", at = System.currentTimeMillis() + 86_400_000L)
            }
            Text(
                tr("Wydarzenie coroczne (np. urodziny) po każdej dacie przeskakuje na kolejny rok."),
                fontSize = 12.sp,
                color = Palette.TextDim,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        events.forEach { e ->
            val target = TimeMath.eventTarget(e, now)
            val diff = target - now
            val days = (diff / 86_400_000L).toInt()
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.emoji.isNotBlank()) Text(e.emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title.ifBlank { tr("Wydarzenie") }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append(Fmt.fullDate(target))
                                append(" · ")
                                append(Fmt.time(context, target))
                                if (e.yearly) append(" · " + tr("{0}. rocznica", TimeMath.anniversary(e, now)))
                            },
                            fontSize = 11.sp,
                            color = Palette.TextDim,
                        )
                    }
                    RoundIcon(Icons.Rounded.Edit, tr("Zmień wydarzenie"), size = 34.dp) { editing = e }
                    RoundIcon(Icons.Rounded.Delete, tr("Usuń wydarzenie"), size = 34.dp) {
                        Actions.deleteEvent(e.id)
                        vm.showSnack(tr("Usunięto wydarzenie")) { Actions.saveEvent(e) }
                    }
                }
                Text(
                    if (diff >= 0) {
                        if (days >= 1) "$days ${plural(days, "dzień", "dni", "dni")}" else TimeMath.clock(diff, forceHours = true)
                    } else {
                        tr("minęło {0}", Fmt.until(-diff).removePrefix(tr("za ")))
                    },
                    fontSize = 30.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                )
                if (diff >= 0 && days >= 1) Text(
                    tr("dokładnie {0}", TimeMath.clock(diff % 86_400_000L, forceHours = true)),
                    fontSize = 11.sp,
                    color = Palette.TextDim,
                )
            }
        }
    }

    val event = editing
    if (event != null) {
        var draft by remember(event.id) { mutableStateOf(event) }
        DarkDialog(
            title = tr("Wydarzenie"),
            onDismiss = { editing = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { editing = null }
                PillButton(tr("Zapisz")) {
                    Actions.saveEvent(draft.copy(title = draft.title.trim()))
                    editing = null
                }
            },
        ) {
            DarkField(draft.title, { draft = draft.copy(title = it) }, tr("Nazwa (np. urlop, egzamin)"))
            DarkField(draft.emoji, { draft = draft.copy(emoji = it.take(2)) }, tr("Emoji (opcjonalnie)"))
            Group(null) {
                InfoRow(Icons.Rounded.CalendarMonth, tr("Data"), Fmt.fullDate(draft.at))
                InfoRow(Icons.Rounded.Schedule, tr("Godzina"), Fmt.time(context, draft.at))
                SwitchRow(Icons.Rounded.Cake, tr("Co roku"), tr("Rocznice i urodziny"), draft.yearly) { v -> draft = draft.copy(yearly = v) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Wybierz datę"), modifier = Modifier.weight(1f), primary = false) {
                    pickDate(context, draft.at) { draft = draft.copy(at = it) }
                }
                PillButton(tr("Wybierz godzinę"), modifier = Modifier.weight(1f), primary = false) {
                    pickTime(context, draft.at) { draft = draft.copy(at = it) }
                }
            }
        }
    }
}

/** Okno wyboru daty systemu – zachowuje godzinę z [epoch]. */
private fun pickDate(context: Context, epoch: Long, onPick: (Long) -> Unit) {
    val at = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
    DatePickerDialog(
        context,
        { _, y, m, d -> onPick(at.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli()) },
        at.year, at.monthValue - 1, at.dayOfMonth,
    ).show()
}

private fun pickTime(context: Context, epoch: Long, onPick: (Long) -> Unit) {
    val at = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
    TimePickerDialog(
        context,
        { _, h, m -> onPick(at.withHour(h).withMinute(m).withSecond(0).toInstant().toEpochMilli()) },
        at.hour, at.minute, Fmt.is24(context),
    ).show()
}

/**
 * Metronom: równe uderzenia z akcentem na pierwszą miarę. Tempo można wystukać palcem –
 * aplikacja liczy średnią z ostatnich uderzeń.
 */
@Composable
fun MetronomeScreen(vm: AppViewModel) {
    var bpm by remember { mutableIntStateOf(100) }
    var meter by remember { mutableIntStateOf(4) }
    var accent by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var beat by remember { mutableIntStateOf(0) }
    val taps = remember { mutableStateListOf<Long>() }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    LaunchedEffect(running, bpm, meter, accent) {
        if (!running) return@LaunchedEffect
        val period = 60_000.0 / bpm
        // Terminy liczone od startu, a nie kolejnymi delay() – tempo nie „ucieka” przez opóźnienia pętli.
        val start = System.nanoTime()
        var n = 0L
        while (true) {
            // Akcent tylko przy metrum > 1 (przy 1/4 każda miara jest „raz” – brzmią jednakowo).
            // Pojedyncze tony DTMF: TONE_PROP_BEEP2 to podwójny pisk, który brzmiał jak dwa uderzenia.
            val first = accent && meter > 1 && beat == 0
            tone?.startTone(if (first) ToneGenerator.TONE_DTMF_D else ToneGenerator.TONE_DTMF_1, 50)
            n++
            val wait = (start + (n * period * 1_000_000).toLong() - System.nanoTime()) / 1_000_000
            delay(wait.coerceAtLeast(0))
            beat = (beat + 1) % meter.coerceAtLeast(1)
        }
    }

    SubScreen(tr("Metronom"), onBack = { vm.back() }) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$bpm", fontSize = 72.sp, fontWeight = FontWeight.Thin, fontFamily = FontFamily.Monospace)
                Text(tr("uderzeń na minutę · {0}", tempoName(bpm)), fontSize = 12.sp, color = Palette.TextDim)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(meter) { i ->
                        val on = running && i == beat
                        Box(
                            Modifier.size(if (i == 0) 18.dp else 14.dp).clip(CircleShape)
                                .background(if (on) Color.White else Color.White.copy(alpha = 0.18f)),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (running) tr("Stop") else tr("Start"), modifier = Modifier.weight(1f)) {
                beat = 0
                running = !running
            }
            PillButton(tr("Wystukaj"), primary = false) {
                val now = System.currentTimeMillis()
                taps.removeAll { now - it > 3000 }
                taps.add(now)
                if (taps.size >= 2) {
                    val gaps = taps.zipWithNext { a, b -> b - a }
                    val average = gaps.average()
                    if (average > 100) bpm = (60_000.0 / average).toInt().coerceIn(20, 300)
                }
            }
        }
        Group(tr("Tempo i metrum")) {
            SliderRow(Icons.Rounded.MusicNote, tr("Tempo"), tr("{0} BPM", bpm), bpm, 20..300) { bpm = it }
            Stepper(tr("Metrum"), meter, { tr("{0}/4", it) }, 1..12, icon = Icons.Rounded.Repeat) { meter = it; beat = 0 }
            SwitchRow(Icons.Rounded.TouchApp, tr("Akcent na „raz”"), tr("Pierwsza miara brzmi wyżej"), accent) { accent = it }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 80, 100, 120, 140, 160).forEach { preset ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (bpm == preset) Color.White else Color.White.copy(alpha = 0.07f))
                        .reactive("$preset") { bpm = preset }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$preset", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (bpm == preset) Palette.Ink else Color.White)
                }
            }
        }
    }
}

/** Nazwa tempa po włosku – jak na metronomach mechanicznych. */
private fun tempoName(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

/**
 * Kalkulator czasu: dodawanie i odejmowanie czasów trwania, przesuwanie godziny
 * oraz różnica między dwiema datami.
 */
@Composable
fun CalculatorScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    SubScreen(tr("Kalkulator czasu"), onBack = { vm.back() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(Icons.Rounded.Calculate, tr("Czasy"), tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabChip(Icons.Rounded.Schedule, tr("Godzina"), tab == 1, Modifier.weight(1f)) { tab = 1 }
            TabChip(Icons.Rounded.CalendarMonth, tr("Daty"), tab == 2, Modifier.weight(1f)) { tab = 2 }
        }
        when (tab) {
            0 -> DurationMath()
            1 -> ClockMath(context)
            else -> DateMath(context)
        }
    }
}

@Composable
private fun DurationMath() {
    var firstH by remember { mutableIntStateOf(1) }
    var firstM by remember { mutableIntStateOf(30) }
    var firstS by remember { mutableIntStateOf(0) }
    var secondH by remember { mutableIntStateOf(0) }
    var secondM by remember { mutableIntStateOf(45) }
    var secondS by remember { mutableIntStateOf(0) }
    var plus by remember { mutableStateOf(true) }
    var multiplier by remember { mutableIntStateOf(1) }
    val a = ((firstH * 60L + firstM) * 60 + firstS) * 1000
    val b = ((secondH * 60L + secondM) * 60 + secondS) * 1000
    val result = (if (plus) a + b else a - b) * multiplier

    Group(tr("Pierwszy czas")) {
        Stepper(tr("Godziny"), firstH, { "$it" }, 0..999) { firstH = it }
        Stepper(tr("Minuty"), firstM, { "$it" }, 0..59) { firstM = it }
        Stepper(tr("Sekundy"), firstS, { "$it" }, 0..59) { firstS = it }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(tr("Dodaj"), modifier = Modifier.weight(1f), primary = plus) { plus = true }
        PillButton(tr("Odejmij"), modifier = Modifier.weight(1f), primary = !plus) { plus = false }
    }
    Group(tr("Drugi czas")) {
        Stepper(tr("Godziny"), secondH, { "$it" }, 0..999) { secondH = it }
        Stepper(tr("Minuty"), secondM, { "$it" }, 0..59) { secondM = it }
        Stepper(tr("Sekundy"), secondS, { "$it" }, 0..59) { secondS = it }
        Stepper(tr("Mnożnik"), multiplier, { "× $it" }, 1..100) { multiplier = it }
    }
    ResultCard(TimeMath.clock(result, forceHours = true), Fmt.durationShort(kotlin.math.abs(result)))
}

@Composable
private fun ClockMath(context: Context) {
    var start by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hours by remember { mutableIntStateOf(2) }
    var minutes by remember { mutableIntStateOf(30) }
    var plus by remember { mutableStateOf(true) }
    val shift = (hours * 60L + minutes) * 60_000L * (if (plus) 1 else -1)
    val target = start + shift

    Group(tr("Godzina początkowa")) {
        InfoRow(Icons.Rounded.Schedule, tr("Start"), "${Fmt.fullDate(start)} · ${Fmt.time(context, start)}")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(tr("Teraz"), modifier = Modifier.weight(1f), primary = false) { start = System.currentTimeMillis() }
        PillButton(tr("Wybierz godzinę"), modifier = Modifier.weight(1f), primary = false) { pickTime(context, start) { start = it } }
        PillButton(tr("Datę"), primary = false) { pickDate(context, start) { start = it } }
    }
    Group(tr("Przesunięcie")) {
        Stepper(tr("Godziny"), hours, { "$it" }, 0..999) { hours = it }
        Stepper(tr("Minuty"), minutes, { "$it" }, 0..59) { minutes = it }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(tr("Później"), modifier = Modifier.weight(1f), primary = plus) { plus = true }
        PillButton(tr("Wcześniej"), modifier = Modifier.weight(1f), primary = !plus) { plus = false }
    }
    ResultCard(Fmt.time(context, target), Fmt.fullDate(target))
}

@Composable
private fun DateMath(context: Context) {
    var from by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var to by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 86_400_000L) }
    val zone = ZoneId.systemDefault()
    val firstDate = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
    val secondDate = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(firstDate, secondDate)
    val months = java.time.temporal.ChronoUnit.MONTHS.between(firstDate, secondDate)
    val period = java.time.Period.between(firstDate, secondDate)
    val workdays = countWorkdays(firstDate, secondDate)

    Group(tr("Od")) { InfoRow(Icons.Rounded.CalendarMonth, tr("Data początkowa"), Fmt.fullDate(from)) }
    PillButton(tr("Wybierz datę początkową"), modifier = Modifier.fillMaxWidth(), primary = false) { pickDate(context, from) { from = it } }
    Group(tr("Do")) { InfoRow(Icons.Rounded.CalendarMonth, tr("Data końcowa"), Fmt.fullDate(to)) }
    PillButton(tr("Wybierz datę końcową"), modifier = Modifier.fillMaxWidth(), primary = false) { pickDate(context, to) { to = it } }
    ResultCard(
        "$days ${plural(days.toInt(), "dzień", "dni", "dni")}",
        tr("{0} lat, {1} mies., {2} dni · {3} tyg. · {4} mies. · {5} dni roboczych", period.years, period.months, period.days, days / 7, months, workdays),
    )
}

/** Dni od poniedziałku do piątku w przedziale (bez dnia końcowego). */
private fun countWorkdays(from: LocalDate, to: LocalDate): Long {
    if (to.isBefore(from)) return -countWorkdays(to, from)
    var day = from
    var count = 0L
    while (day.isBefore(to)) {
        if (day.dayOfWeek.value <= 5) count++
        day = day.plusDays(1)
    }
    return count
}

@Composable
private fun ResultCard(value: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr("Wynik").uppercase(), fontSize = 10.sp, letterSpacing = 1.4.sp, color = Palette.Ink.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, fontFamily = FontFamily.Monospace)
        Text(hint, fontSize = 12.sp, color = Palette.Ink.copy(alpha = 0.6f), textAlign = TextAlign.Center)
    }
}
