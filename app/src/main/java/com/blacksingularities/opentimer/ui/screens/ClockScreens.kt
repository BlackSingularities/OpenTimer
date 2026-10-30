package com.blacksingularities.opentimer.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.ClockStyle
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Fmt
import com.blacksingularities.opentimer.ui.Route
import com.blacksingularities.opentimer.ui.UpdateBanner
import com.blacksingularities.opentimer.ui.components.ColorDot
import com.blacksingularities.opentimer.ui.components.DarkField
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ClockTab(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val zones by Store.zones.collectAsState()
    val alarms by Store.alarms.collectAsState()
    val now = rememberNow(if (settings.clockStyle == ClockStyle.ANALOG) 50L else 1000L)
    val next = TimeMath.nextOf(alarms, now)
    val localZone = ZoneId.systemDefault()

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabHeader(tr("Zegar"), Fmt.zoneName(localZone.id, now)) {
                RoundIcon(Icons.Rounded.Bedtime, tr("Zegar nocny")) { vm.open(Route.NightClock) }
                RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), badge = if (vm.updatePending) "1" else null) { vm.open(Route.Settings) }
            }
        }
        item { UpdateBanner(vm) }
        item {
            Column(
                Modifier.fillMaxWidth().reactive(tr("Zmień styl zegara")) {
                    vm.settings { it.copy(clockStyle = if (it.clockStyle == ClockStyle.DIGITAL) ClockStyle.ANALOG else ClockStyle.DIGITAL) }
                }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (settings.clockStyle == ClockStyle.ANALOG) {
                    AnalogClock(now, localZone, settings.showSeconds, Modifier.widthIn(max = 300.dp).fillMaxWidth(0.78f).aspectRatio(1f))
                    Spacer(Modifier.size(12.dp))
                    Text(Fmt.time(context, now), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                } else {
                    DigitalTime(now, settings.showSeconds)
                }
                Text(Fmt.date(now), fontSize = 16.sp, color = Palette.TextDim)
                if (next != null) {
                    Spacer(Modifier.size(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                            .reactive(tr("Budziki")) { vm.select(com.blacksingularities.opentimer.ui.Tab.ALARMS) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${Fmt.dayAndTime(context, next.second)} · ${Fmt.until(next.second - now)}", fontSize = 13.sp)
                    }
                }
            }
        }
        // W podróży: zegar strefy, w której aplikacja została uruchomiona pierwszy raz.
        val home = settings.homeZone
            .takeIf { settings.homeClock && it.isNotBlank() && it != localZone.id }
            ?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() }
            ?.takeIf { TimeMath.zoneOffsetMinutes(it, now) != 0 }
        if (home != null) item {
            Tile(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Home, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Zegar domowy"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${Fmt.cityOf(home.id)} · ${Fmt.offset(TimeMath.zoneOffsetMinutes(home, now))}",
                            fontSize = 12.sp,
                            color = Palette.TextDim,
                        )
                    }
                    Text(Fmt.time(context, now, home), fontSize = 26.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(tr("Zegary świata"), Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Add, tr("Dodaj miasto"), size = 36.dp) { vm.open(Route.ZonePicker) }
            }
        }
        if (zones.isEmpty()) item {
            PrimaryCard(Icons.Rounded.Public, tr("Dodaj miasto"), tr("Godzina w innych strefach czasowych")) { vm.open(Route.ZonePicker) }
        }
        items(zones, key = { it }) { id ->
            val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@items
            val diff = TimeMath.zoneOffsetMinutes(zone, now)
            val shift = TimeMath.dayShift(zone, now)
            val hour = Instant.ofEpochMilli(now).atZone(zone).hour
            Tile(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Text(if (hour in 6..19) "☀" else "☾", fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(Fmt.cityOf(id), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val day = when (shift) { -1 -> tr("Wczoraj"); 1 -> tr("Jutro"); else -> tr("Dziś") }
                        Text("$day · ${Fmt.offset(diff)}", fontSize = 12.sp, color = Palette.TextDim)
                    }
                    Text(Fmt.time(context, now, zone), fontSize = 26.sp, fontWeight = FontWeight.Light)
                    RoundIcon(Icons.Rounded.Close, tr("Usuń {0}", Fmt.cityOf(id)), size = 32.dp, modifier = Modifier.padding(start = 8.dp)) {
                        Store.updateZones { it - id }
                        vm.showSnack(tr("Usunięto {0}", Fmt.cityOf(id))) { Store.updateZones { it + id } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalTime(now: Long, seconds: Boolean) {
    val context = LocalContext.current
    val (hm, ampm) = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).let { Fmt.hm(context, it.hour, it.minute) }
    val sec = "%02d".format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).second)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(hm, fontSize = 84.sp, fontWeight = FontWeight.Thin, color = Color.White)
        Column(Modifier.padding(start = 6.dp, bottom = 18.dp)) {
            if (ampm != null) Text(ampm, fontSize = 16.sp, color = Palette.TextDim, fontWeight = FontWeight.Bold)
            if (seconds) Text(sec, fontSize = 26.sp, color = Palette.TextDim, fontWeight = FontWeight.Light)
        }
    }
}

/** Tarcza analogowa: płynna wskazówka sekundowa i podziałka minutowa. */
@Composable
fun AnalogClock(now: Long, zone: ZoneId, seconds: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val t = Instant.ofEpochMilli(now).atZone(zone)
    val s = t.second + t.nano / 1e9f
    val m = t.minute + s / 60f
    val h = t.hour % 12 + m / 60f
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(color.copy(alpha = 0.06f), r, c)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90)
            val long = i % 5 == 0
            val inner = r * if (long) 0.84f else 0.9f
            drawLine(
                color.copy(alpha = if (long) 0.9f else 0.3f),
                Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                Offset(c.x + r * 0.95f * cos(a).toFloat(), c.y + r * 0.95f * sin(a).toFloat()),
                strokeWidth = if (long) r * 0.025f else r * 0.01f,
                cap = StrokeCap.Round,
            )
        }
        fun hand(turns: Float, length: Float, width: Float, col: Color) {
            val a = Math.toRadians(turns * 360.0 - 90)
            drawLine(
                col,
                Offset(c.x - r * 0.08f * cos(a).toFloat(), c.y - r * 0.08f * sin(a).toFloat()),
                Offset(c.x + r * length * cos(a).toFloat(), c.y + r * length * sin(a).toFloat()),
                strokeWidth = width, cap = StrokeCap.Round,
            )
        }
        hand(h / 12f, 0.5f, r * 0.055f, color)
        hand(m / 60f, 0.74f, r * 0.04f, color)
        if (seconds) hand(s / 60f, 0.82f, r * 0.015f, Palette.Record)
        drawCircle(if (seconds) Palette.Record else color, r * 0.04f, c)
    }
}

/** Wybór strefy czasowej z wyszukiwaniem po mieście, regionie i nazwie strefy. */
@Composable
fun ZonePickerScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val zones by Store.zones.collectAsState()
    val now = rememberNow(60_000)
    val all = remember {
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
            .sortedBy { Fmt.cityOf(it) }
    }
    val q = query.trim().lowercase()
    val found = remember(q, all) {
        if (q.isEmpty()) all else all.filter {
            it.lowercase().replace('_', ' ').contains(q) || Fmt.zoneName(it, now).lowercase().contains(q)
        }
    }
    SubScreen(tr("Dodaj miasto"), onBack = { vm.back() }, scroll = false) {
        DarkField(query, { query = it }, tr("Szukaj miasta lub strefy"))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(found, key = { it }) { id ->
                val added = id in zones
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .reactive(Fmt.cityOf(id)) {
                            if (!added) Store.updateZones { it + id }
                            vm.back()
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.cityOf(id), fontSize = 15.sp, fontWeight = if (added) FontWeight.Bold else FontWeight.Medium)
                        Text("${id.substringBefore('/').replace('_', ' ')} · ${Fmt.zoneName(id, now)}", fontSize = 11.sp, color = Palette.TextDim, maxLines = 1)
                    }
                    Text(Fmt.time(context, now, ZoneId.of(id)), fontSize = 15.sp, color = if (added) Color.White else Palette.TextDim)
                }
            }
        }
    }
}

/**
 * Zegar nocny: przyciemniony, w wybranym kolorze, z powolnym przesuwaniem treści
 * (chroni ekran OLED przed wypaleniem). Dotknięcie pokazuje przyciski.
 */
@Composable
fun NightClockScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val alarms by Store.alarms.collectAsState()
    val now = rememberNow(1000)
    val activity = context as? Activity
    var controls by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(settings.nightDim) {
        val w = activity?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val before = w?.attributes?.screenBrightness ?: -1f
        w?.attributes = w?.attributes?.apply { screenBrightness = (0.01f + (1f - settings.nightDim) * 0.3f) }
        onDispose {
            if (!settings.keepScreenOn) w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            w?.attributes = w?.attributes?.apply { screenBrightness = before }
        }
    }
    LaunchedEffect(lastTouch) {
        delay(5000)
        controls = false
    }
    val color = Color(settings.nightColor).copy(alpha = 1f - settings.nightDim * 0.75f)
    val minute = now / 60_000
    val dx = ((minute * 37) % 41 - 20).toInt()
    val dy = ((minute * 53) % 61 - 30).toInt()
    val next = TimeMath.nextOf(alarms, now)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGesturesSimple { controls = !controls; lastTouch = System.currentTimeMillis() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.offset { IntOffset((dx * density).roundToInt(), (dy * density).roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Fmt.time(context, now, seconds = settings.nightSeconds), fontSize = 96.sp, fontWeight = FontWeight.Thin, color = color, textAlign = TextAlign.Center)
            Text(Fmt.date(now), fontSize = 18.sp, color = color.copy(alpha = color.alpha * 0.7f))
            if (next != null) Text("⏰ " + Fmt.dayAndTime(context, next.second), fontSize = 16.sp, color = color.copy(alpha = color.alpha * 0.7f), modifier = Modifier.padding(top = 6.dp))
        }
        if (controls) {
            Column(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFFBF5AF2, 0xFFFFFFFF).forEach { c ->
                        ColorDot(c.toInt(), settings.nightColor == c.toInt()) {
                            vm.settings { it.copy(nightColor = c.toInt()) }; lastTouch = System.currentTimeMillis()
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.blacksingularities.opentimer.ui.components.PillButton(tr("Jaśniej"), primary = false) {
                        vm.settings { it.copy(nightDim = (it.nightDim - 0.15f).coerceAtLeast(0f)) }; lastTouch = System.currentTimeMillis()
                    }
                    com.blacksingularities.opentimer.ui.components.PillButton(tr("Ciemniej"), primary = false) {
                        vm.settings { it.copy(nightDim = (it.nightDim + 0.15f).coerceAtMost(0.95f)) }; lastTouch = System.currentTimeMillis()
                    }
                    RoundIcon(Icons.Rounded.Watch, tr("Sekundy"), active = settings.nightSeconds) {
                        vm.settings { it.copy(nightSeconds = !it.nightSeconds) }; lastTouch = System.currentTimeMillis()
                    }
                    RoundIcon(Icons.Rounded.Close, tr("Zamknij")) { vm.back() }
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesSimple(onTap: () -> Unit) =
    detectTapGestures { onTap() }

