package com.blacksingularities.opentimer.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.blacksingularities.opentimer.core.IntervalStep
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.core.steps
import com.blacksingularities.opentimer.data.IntervalPlan
import com.blacksingularities.opentimer.data.Store
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
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Pomodoro: bloki skupienia przeplatane przerwami. Odliczanie trzyma systemowy alarm, więc
 * faza kończy się także przy zamkniętej aplikacji; ekran pokazuje tylko stan z pamięci.
 */
@Composable
fun PomodoroScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val p by Store.pomodoro.collectAsState()
    val now = rememberNow(250L)
    val left = p.remainingAt(now).coerceAtLeast(0)
    val total = (p.minutesOf(p.phase) * 60_000L).coerceAtLeast(1)
    val progress = 1f - left.toFloat() / total

    SubScreen(tr("Pomodoro"), onBack = { vm.back() }) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = progress,
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(0.76f).aspectRatio(1f),
                stroke = 8.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.phase(p.phase).uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = Palette.TextDim, fontWeight = FontWeight.Bold)
                    Text(
                        TimeMath.countdown(left),
                        fontSize = 46.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                    )
                    Text(tr("Cykl {0} z {1}", p.cycle % p.longEvery.coerceAtLeast(1) + 1, p.longEvery.coerceAtLeast(1)), fontSize = 12.sp, color = Palette.TextDim)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (p.running) tr("Pauza") else tr("Start"), modifier = Modifier.weight(1f)) {
                if (p.running) Actions.pomodoroPause(context) else Actions.pomodoroStart(context)
            }
            RoundIcon(Icons.Rounded.SkipNext, tr("Następna faza")) { Actions.pomodoroSkip(context) }
            RoundIcon(Icons.Rounded.Replay, tr("Od początku")) { Actions.pomodoroReset(context) }
        }
        Group(tr("Długości faz")) {
            SliderRow(Icons.Rounded.Timelapse, tr("Skupienie"), tr("{0} min", p.focusMin), p.focusMin, 5..120) { v -> Actions.pomodoroConfigure(context) { it.copy(focusMin = v) } }
            SliderRow(Icons.Rounded.Coffee, tr("Krótka przerwa"), tr("{0} min", p.shortMin), p.shortMin, 1..30) { v -> Actions.pomodoroConfigure(context) { it.copy(shortMin = v) } }
            SliderRow(Icons.Rounded.Coffee, tr("Długa przerwa"), tr("{0} min", p.longMin), p.longMin, 5..60) { v -> Actions.pomodoroConfigure(context) { it.copy(longMin = v) } }
            SliderRow(Icons.Rounded.Repeat, tr("Długa przerwa co"), tr("{0} sesje", p.longEvery), p.longEvery, 2..8) { v -> Actions.pomodoroConfigure(context) { it.copy(longEvery = v) } }
            SwitchRow(Icons.Rounded.PlayArrow, tr("Automatyczne przejścia"), tr("Następna faza startuje sama"), p.autoStart) { v -> Actions.pomodoroConfigure(context) { it.copy(autoStart = v) } }
        }
        Group(tr("Podsumowanie")) {
            InfoRow(Icons.Rounded.Timelapse, tr("Sesje dziś"), "${p.doneToday}")
            InfoRow(Icons.Rounded.Timelapse, tr("Sesje łącznie"), "${p.doneTotal}")
            InfoRow(
                Icons.Rounded.Alarm,
                tr("Czas skupienia łącznie"),
                if (p.focusTotalMin >= 60) tr("{0} h {1} min", p.focusTotalMin / 60, p.focusTotalMin % 60) else tr("{0} min", p.focusTotalMin),
            )
        }
        Text(
            tr("Jeden blok skupienia to zwykle 25 minut. Po czterech blokach warto zrobić dłuższą przerwę."),
            fontSize = 11.sp,
            color = Palette.TextFaint,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Faza treningu z własnym kolorem tła karty i zapowiedzią głosową. */
private fun kindLabel(kind: IntervalStep.Kind): String = when (kind) {
    IntervalStep.Kind.PREPARE -> tr("Przygotuj się")
    IntervalStep.Kind.WORK -> tr("Praca")
    IntervalStep.Kind.REST -> tr("Odpoczynek")
    IntervalStep.Kind.SET_REST -> tr("Przerwa między seriami")
    IntervalStep.Kind.COOLDOWN -> tr("Rozluźnienie")
}

/**
 * Trening interwałowy. Odcinki liczy sam ekran (trening trwa z włączonym telefonem w ręku),
 * a lektor i piknięcia zapowiadają zmianę odcinka, żeby nie trzeba było patrzeć na ekran.
 */
@Composable
fun IntervalScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val plans by Store.intervals.collectAsState()
    var planIndex by remember { mutableIntStateOf(0) }
    val plan = plans.getOrNull(planIndex) ?: IntervalPlan()
    val steps = remember(plan) { plan.steps() }
    var running by remember { mutableStateOf(false) }
    var stepIndex by remember(plan) { mutableIntStateOf(0) }
    var leftMs by remember(plan) { mutableStateOf((steps.firstOrNull()?.seconds ?: 0) * 1000L) }
    var editing by remember { mutableStateOf(false) }

    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { }
        speaker = tts
        onDispose {
            tts.shutdown()
            tone?.release()
        }
    }
    fun say(text: String) {
        if (!settings.intervalVoice) return
        speaker?.let {
            it.language = if (com.blacksingularities.opentimer.i18n.I18n.english) Locale.ENGLISH else Locale.forLanguageTag("pl-PL")
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "interval")
        }
    }

    // Odliczanie odcinka: krok co 100 ms, piknięcia na trzech ostatnich sekundach.
    LaunchedEffect(running, stepIndex, plan) {
        if (!running) return@LaunchedEffect
        val step = steps.getOrNull(stepIndex) ?: run { running = false; return@LaunchedEffect }
        say(kindLabel(step.kind))
        var beeped = -1
        while (leftMs > 0) {
            delay(100)
            leftMs -= 100
            val secondsLeft = ((leftMs + 999) / 1000).toInt()
            if (settings.intervalBeeps && secondsLeft in 1..3 && secondsLeft != beeped) {
                beeped = secondsLeft
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            }
        }
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        val next = stepIndex + 1
        if (next >= steps.size) {
            running = false
            stepIndex = 0
            leftMs = (steps.firstOrNull()?.seconds ?: 0) * 1000L
            say(tr("Koniec treningu"))
            vm.showSnack(tr("Trening ukończony"))
        } else {
            stepIndex = next
            leftMs = steps[next].seconds * 1000L
        }
    }

    val step = steps.getOrNull(stepIndex)
    val done = steps.take(stepIndex).sumOf { it.seconds } * 1000L + ((step?.seconds ?: 0) * 1000L - leftMs)
    val totalMs = plan.totalSec * 1000L

    SubScreen(
        tr("Interwały"),
        onBack = { vm.back() },
        actions = {
            RoundIcon(Icons.Rounded.Add, tr("Nowy plan"), size = 38.dp) {
                Store.updateIntervals { it + IntervalPlan(name = tr("Mój plan")) }
                planIndex = plans.size
                editing = true
            }
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = if (step == null) 0f else 1f - leftMs.toFloat() / (step.seconds * 1000L).coerceAtLeast(1),
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(0.76f).aspectRatio(1f),
                stroke = 8.dp,
                color = if (step?.kind == IntervalStep.Kind.WORK) Color.White else Palette.Moon,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((step?.let { kindLabel(it.kind) } ?: tr("Gotowy")).uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = Palette.TextDim, fontWeight = FontWeight.Bold)
                    Text(
                        TimeMath.countdown(leftMs),
                        fontSize = 46.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                    )
                    if (step != null && step.round > 0) Text(
                        tr("Runda {0}/{1} · seria {2}/{3}", step.round, plan.rounds, step.set, plan.sets),
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
            Box(
                Modifier.fillMaxWidth(if (totalMs <= 0) 0f else (done.toFloat() / totalMs).coerceIn(0f, 1f))
                    .height(4.dp).clip(RoundedCornerShape(50)).background(Color.White),
            )
        }
        Text(
            tr("Cały trening: {0} · zostało {1}", Fmt.durationShort(totalMs), Fmt.durationShort((totalMs - done).coerceAtLeast(0))),
            fontSize = 11.sp,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (running) tr("Pauza") else tr("Start"), modifier = Modifier.weight(1f), enabled = steps.isNotEmpty()) {
                running = !running
            }
            RoundIcon(Icons.Rounded.SkipNext, tr("Następny odcinek"), enabled = steps.isNotEmpty()) {
                val next = (stepIndex + 1).coerceAtMost(steps.lastIndex)
                stepIndex = next
                leftMs = steps[next].seconds * 1000L
            }
            RoundIcon(Icons.Rounded.RestartAlt, tr("Od początku")) {
                running = false
                stepIndex = 0
                leftMs = (steps.firstOrNull()?.seconds ?: 0) * 1000L
            }
        }
        SectionLabel(tr("Plany"))
        plans.forEachIndexed { index, item ->
            val active = index == planIndex
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                    .reactive(item.name.ifBlank { tr("Plan {0}", index + 1) }) {
                        planIndex = index
                        running = false
                        stepIndex = 0
                        leftMs = (item.steps().firstOrNull()?.seconds ?: 0) * 1000L
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.name.ifBlank { tr("Plan {0}", index + 1) }, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    Text(
                        tr("{0} × {1} s pracy / {2} s odpoczynku · {3}", item.rounds, item.workSec, item.restSec, Fmt.durationShort(item.totalSec * 1000L)),
                        fontSize = 11.sp,
                        color = Palette.TextDim,
                    )
                }
                if (active) RoundIcon(Icons.Rounded.DirectionsRun, tr("Zmień plan"), size = 34.dp) { editing = true }
                if (plans.size > 1) RoundIcon(Icons.Rounded.Delete, tr("Usuń plan"), size = 34.dp) {
                    Store.updateIntervals { list -> list - item }
                    planIndex = 0
                    running = false
                    vm.showSnack(tr("Usunięto plan")) { Store.updateIntervals { list -> list + item } }
                }
            }
        }
    }

    if (editing) {
        var draft by remember(plan) { mutableStateOf(plan) }
        DarkDialog(
            title = tr("Plan treningu"),
            onDismiss = { editing = false },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { editing = false }
                PillButton(tr("Zapisz")) {
                    Store.updateIntervals { list -> list.map { if (it == plan) draft else it } }
                    running = false
                    stepIndex = 0
                    leftMs = (draft.steps().firstOrNull()?.seconds ?: 0) * 1000L
                    editing = false
                }
            },
        ) {
            DarkField(draft.name, { draft = draft.copy(name = it) }, tr("Nazwa planu"))
            Stepper(tr("Przygotowanie"), draft.prepareSec, { secLabel(it) }, 0..120, 5) { draft = draft.copy(prepareSec = it) }
            Stepper(tr("Praca"), draft.workSec, { secLabel(it) }, 5..600, 5) { draft = draft.copy(workSec = it) }
            Stepper(tr("Odpoczynek"), draft.restSec, { secLabel(it) }, 0..600, 5) { draft = draft.copy(restSec = it) }
            Stepper(tr("Rundy"), draft.rounds, { "$it" }, 1..50) { draft = draft.copy(rounds = it) }
            Stepper(tr("Serie"), draft.sets, { "$it" }, 1..20) { draft = draft.copy(sets = it) }
            Stepper(tr("Przerwa między seriami"), draft.setRestSec, { secLabel(it) }, 0..600, 10) { draft = draft.copy(setRestSec = it) }
            Stepper(tr("Rozluźnienie"), draft.cooldownSec, { secLabel(it) }, 0..600, 10) { draft = draft.copy(cooldownSec = it) }
            Text(tr("Cały trening: {0}", Fmt.durationShort(draft.totalSec * 1000L)), fontSize = 12.sp, color = Palette.TextDim)
        }
    }
}
