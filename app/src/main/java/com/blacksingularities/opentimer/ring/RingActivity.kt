package com.blacksingularities.opentimer.ring

import android.app.KeyguardManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.Challenge
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.data.VolumeKeys
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.Fmt
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.theme.GlassScope
import com.blacksingularities.opentimer.ui.theme.OpenTimerTheme
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/** Ekran dzwonienia – także na zablokowanym telefonie. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        I18n.apply(Store.settings.value.language)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            OpenTimerTheme {
                GlassScope(haptics = true) {
                    val st by RingService.state.collectAsState()
                    LaunchedEffect(st) { if (st == null) finish() }
                    st?.let { RingScreen(it) }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val st = RingService.state.value ?: return super.onKeyDown(keyCode, event)
            val alarm = st.alarm
            when (Store.settings.value.volumeKeys) {
                VolumeKeys.NOTHING -> return true
                VolumeKeys.SNOOZE -> RingService.command(this, if (alarm != null) RingService.ACTION_SNOOZE else RingService.ACTION_DISMISS)
                VolumeKeys.DISMISS -> if (alarm == null || alarm.challenge == Challenge.NONE) RingService.command(this, RingService.ACTION_DISMISS)
                else RingService.command(this, RingService.ACTION_SNOOZE)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun dismissAndUnlock() {
        RingService.command(this, RingService.ACTION_DISMISS)
        getSystemService(KeyguardManager::class.java)?.let { km ->
            if (km.isKeyguardLocked) runCatching { km.requestDismissKeyguard(this, null) }
        }
    }
}

@Composable
private fun RingScreen(st: RingState) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale")
    var solving by remember(st.alarm?.id) { mutableStateOf(false) }
    val alarm = st.alarm

    Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)) {
        if (alarm != null && solving) {
            Challenge(alarm, onSolved = { (context as? RingActivity)?.dismissAndUnlock() }, onBack = { solving = false })
            return@Box
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.6f))
            Box(
                Modifier.size(96.dp).scale(scale).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (alarm != null) Icons.Rounded.Alarm else Icons.Rounded.HourglassBottom, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(Fmt.time(context, now), fontSize = 76.sp, fontWeight = FontWeight.Light, color = Color.White)
            Text(Fmt.date(now), fontSize = 15.sp, color = Palette.TextDim)
            Spacer(Modifier.height(20.dp))
            if (alarm != null) {
                Text(alarm.label.ifBlank { tr("Budzik") }, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (alarm.snoozeCount > 0) Text(tr("Drzemka nr {0}", alarm.snoozeCount), fontSize = 13.sp, color = Palette.TextDim)
            } else {
                Text(tr("Czas minął"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                st.timers.forEach { t ->
                    val over = now - (t.endAt.takeIf { it > 0 } ?: st.startedAt)
                    Text(
                        "${t.label.ifBlank { Fmt.durationShort(t.durationMs) }} · −${TimeMath.clock(over.coerceAtLeast(0))}",
                        fontSize = 15.sp, color = Palette.TextDim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (alarm != null) {
                val canSnooze = alarm.maxSnoozes == 0 || alarm.snoozeCount < alarm.maxSnoozes
                if (canSnooze) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.1f))
                            .reactive(tr("Drzemka")) { RingService.command(context, RingService.ACTION_SNOOZE) }
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Snooze, null, tint = Color.White)
                        Spacer(Modifier.size(10.dp))
                        Text(tr("Drzemka {0} min", alarm.snoozeMinutes), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                SlideToDismiss(
                    if (alarm.challenge == Challenge.NONE) tr("Przesuń, aby wyłączyć") else tr("Przesuń, aby rozwiązać zadanie"),
                ) {
                    if (alarm.challenge == Challenge.NONE) (context as? RingActivity)?.dismissAndUnlock() else solving = true
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(tr("+1 min"), Modifier.weight(1f), primary = false) { RingService.command(context, RingService.ACTION_TIMER_ADD) }
                    PillButton(tr("Zatrzymaj"), Modifier.weight(1f)) { RingService.command(context, RingService.ACTION_DISMISS) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Suwak „przesuń, aby wyłączyć” – chroni przed wyłączeniem budzika przez przypadek. */
@Composable
private fun SlideToDismiss(text: String, onDone: () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(50)).background(Color.White),
        contentAlignment = Alignment.CenterStart,
    ) {
        val knob = 60.dp
        val max = with(density) { (maxWidth - knob - 8.dp).toPx() }
        var x by remember { mutableFloatStateOf(0f) }
        Text(text, color = Palette.Ink.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt() + with(density) { 4.dp.roundToPx() }, 0) }
                .size(knob)
                .clip(CircleShape)
                .background(Palette.Ink)
                .pointerInput(max) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (x > max * 0.85f) onDone(); x = 0f },
                        onDragCancel = { x = 0f },
                    ) { change, d ->
                        change.consume()
                        x = (x + d).coerceIn(0f, max)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AlarmOff, null, tint = Color.White)
        }
    }
}

/** Zadanie do rozwiązania przed wyłączeniem: działania matematyczne albo przepisanie kodu. */
@Composable
private fun Challenge(alarm: Alarm, onSolved: () -> Unit, onBack: () -> Unit) {
    val total = alarm.challengeCount.coerceIn(1, 10)
    var solved by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf(makeTask(alarm.challenge)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("Zadanie {0} z {1}", solved + 1, total), color = Palette.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text(task.first, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            input.ifEmpty { "?" },
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
            color = if (wrong) Palette.Record else Color.White,
        )
        Spacer(Modifier.weight(1f))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "-", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
                            .reactive(k) {
                                wrong = false
                                input = when (k) {
                                    "⌫" -> input.dropLast(1)
                                    "-" -> if (input.startsWith("-")) input.drop(1) else "-$input"
                                    else -> (input + k).take(8)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Rounded.Backspace, tr("Usuń"), tint = Color.White)
                        else Text(k, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(tr("Wróć"), Modifier.weight(1f), primary = false, onClick = onBack)
            PillButton(tr("Sprawdź"), Modifier.weight(1f)) {
                if (input == task.second) {
                    solved++
                    input = ""
                    if (solved >= total) onSolved() else task = makeTask(alarm.challenge)
                } else {
                    wrong = true
                }
            }
        }
    }
}

private fun makeTask(kind: Challenge): Pair<String, String> {
    val r = Random.Default
    return when (kind) {
        Challenge.MATH_HARD -> {
            val a = r.nextInt(12, 99)
            val b = r.nextInt(3, 13)
            val c = r.nextInt(10, 99)
            "$a × $b + $c" to (a * b + c).toString()
        }
        Challenge.TYPE_CODE -> {
            val code = (1..6).map { r.nextInt(0, 10) }.joinToString("")
            code.chunked(3).joinToString(" ") to code
        }
        else -> {
            val a = r.nextInt(10, 60)
            val b = r.nextInt(5, 40)
            if (r.nextBoolean()) "$a + $b" to (a + b).toString() else "${a + b} − $b" to a.toString()
        }
    }
}
