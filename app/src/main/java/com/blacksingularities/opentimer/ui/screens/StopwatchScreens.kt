package com.blacksingularities.opentimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Route
import com.blacksingularities.opentimer.ui.Tab
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.theme.Palette

/**
 * Stoper z okrążeniami. Czas liczy się ze znacznika startu zapisanego w pamięci, więc zgadza się
 * także po zamknięciu aplikacji. Okrążenia pokazują odcinek i czas łączny, a najszybsze
 * i najwolniejsze z nich są wyróżnione tłem.
 */
@Composable
fun StopwatchTab(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val sw by Store.stopwatch.collectAsState()
    val clipboard = LocalClipboardManager.current
    val now = rememberNow(if (sw.running) (if (settings.stopwatchMs) 16L else 250L) else 1000L)
    val elapsed = sw.elapsedAt(now)
    val laps = sw.laps
    val lapTimes = remember(laps) { laps.mapIndexed { i, t -> t - (laps.getOrNull(i - 1) ?: 0L) } }
    val best = lapTimes.minOrNull()
    val worst = lapTimes.maxOrNull()
    val current = elapsed - (laps.lastOrNull() ?: 0L)

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TabHeader(tr("Stoper"), if (laps.isEmpty()) tr("Dotknij start, aby zacząć") else tr("Okrążeń: {0}", laps.size)) {
            if (laps.isNotEmpty()) RoundIcon(Icons.Rounded.ContentCopy, tr("Kopiuj okrążenia"), size = 38.dp) {
                clipboard.setText(AnnotatedString(lapReport(elapsed, laps)))
                vm.showSnack(tr("Skopiowano okrążenia"))
            }
            RoundIcon(Icons.Rounded.Timer, tr("Minutnik"), size = 38.dp) { vm.select(Tab.TIMERS) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = (elapsed % 60_000L) / 60_000f,
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(0.74f).aspectRatio(1f),
                stroke = 6.dp,
                marker = if (sw.running) (elapsed % 60_000L) / 60_000f else null,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TimeMath.clock(elapsed, centis = settings.stopwatchMs),
                        fontSize = if (settings.stopwatchMs) 38.sp else 44.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                    )
                    if (laps.isNotEmpty()) Text(
                        tr("Okrążenie {0} · {1}", laps.size + 1, TimeMath.clock(current, centis = settings.stopwatchMs)),
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                if (sw.running) tr("Pauza") else if (sw.isReset) tr("Start") else tr("Wznów"),
                modifier = Modifier.weight(1f),
            ) {
                if (sw.running) Actions.stopwatchPause(context) else Actions.stopwatchStart(context)
            }
            PillButton(tr("Okrążenie"), modifier = Modifier.weight(1f), primary = false, enabled = sw.running) {
                Actions.stopwatchLap(context)
            }
            RoundIcon(Icons.Rounded.Replay, tr("Zeruj"), enabled = !sw.isReset) {
                Actions.stopwatchReset(context)
                vm.showSnack(tr("Stoper wyzerowany"))
            }
        }
        if (laps.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryCard(Icons.Rounded.Flag, tr("Okrążenia"), tr("Podczas pomiaru zapisuj kolejne odcinki")) {
                    if (!sw.running) Actions.stopwatchStart(context) else Actions.stopwatchLap(context)
                }
                PrimaryCard(Icons.Rounded.DirectionsRun, tr("Trening interwałowy"), tr("Tabata, HIIT i własne plany z zapowiedziami")) {
                    vm.open(Route.Intervals)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text(tr("Nr"), fontSize = 11.sp, color = Palette.TextFaint, modifier = Modifier.width(34.dp))
                Text(tr("Odcinek"), fontSize = 11.sp, color = Palette.TextFaint, modifier = Modifier.weight(1f))
                Text(tr("Łącznie"), fontSize = 11.sp, color = Palette.TextFaint)
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (sw.running) item {
                    LapRow(laps.size + 1, current, elapsed, settings.stopwatchMs, mark = null, live = true)
                }
                itemsIndexed(laps.reversed()) { index, total ->
                    val number = laps.size - index
                    val lap = lapTimes[number - 1]
                    val mark = when {
                        lapTimes.size < 2 -> null
                        lap == best -> tr("najszybsze")
                        lap == worst -> tr("najwolniejsze")
                        else -> null
                    }
                    LapRow(number, lap, total, settings.stopwatchMs, mark, live = false)
                }
            }
        }
    }
}

@Composable
private fun LapRow(number: Int, lap: Long, total: Long, centis: Boolean, mark: String?, live: Boolean) {
    val background = when {
        mark != null -> Color.White.copy(alpha = 0.13f)
        live -> Color.White.copy(alpha = 0.1f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(number), fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(TimeMath.clock(lap, centis = centis), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            if (mark != null) Text(mark, fontSize = 10.sp, color = Palette.TextDim)
        }
        Text(TimeMath.clock(total, centis = centis), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Palette.TextDim)
    }
}

/** Okrążenia jako tekst do schowka: numer, odcinek, czas łączny. */
fun lapReport(elapsed: Long, laps: List<Long>): String = buildString {
    appendLine(tr("Stoper – {0}", TimeMath.clock(elapsed, centis = true)))
    laps.forEachIndexed { i, total ->
        val lap = total - (laps.getOrNull(i - 1) ?: 0L)
        appendLine("${i + 1}\t${TimeMath.clock(lap, centis = true)}\t${TimeMath.clock(total, centis = true)}")
    }
}
