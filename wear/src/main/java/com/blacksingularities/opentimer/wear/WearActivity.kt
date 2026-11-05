package com.blacksingularities.opentimer.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.delay

private val PRESETS = listOf(1, 3, 5, 10, 15, 25, 30, 45, 60)

private fun clock(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    val m = s / 60 % 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun stopwatchClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s / 60 % 60
    val sec = s % 60
    val cs = ms / 10 % 100
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d.%02d".format(m, sec, cs)
}

/** OpenTimer na Wear OS: minutnik z wibracją po czasie i stoper. */
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearStore.load(this)
        WearStore.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { MaterialTheme { WearApp() } }
    }
}

private enum class Page { HOME, STOPWATCH }

@Composable
private fun WearApp() {
    var page by remember { mutableStateOf(Page.HOME) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (WearStore.swRunning) 50 else 250)
        }
    }
    Scaffold(timeText = { TimeText() }) {
        when {
            WearStore.ringing -> RingPage()
            page == Page.STOPWATCH -> StopwatchPage(now) { page = Page.HOME }
            WearStore.timerActive -> TimerPage(now)
            else -> HomePage(now) { page = Page.STOPWATCH }
        }
    }
}

@Composable
private fun HomePage(now: Long, openStopwatch: () -> Unit) {
    val c = LocalContext.current
    ScalingLazyColumn(Modifier.fillMaxSize()) {
        item { ListHeader { Text(stringResource(R.string.timer)) } }
        items(PRESETS) { min ->
            Chip(
                onClick = { WearStore.startTimer(c, min * 60_000L) },
                label = { Text(c.getString(R.string.minutes, min)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ListHeader { Text(stringResource(R.string.stopwatch)) } }
        item {
            Chip(
                onClick = openStopwatch,
                label = {
                    val elapsed = WearStore.swElapsed(now)
                    val title = stringResource(R.string.stopwatch)
                    Text(if (elapsed > 0) "$title · ${stopwatchClock(elapsed)}" else title)
                },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BigTime(text: String) {
    Text(
        text,
        fontSize = 40.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimerPage(now: Long) {
    val c = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.timer), fontSize = 13.sp)
        BigTime(clock(WearStore.timerLeft(now)))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { WearStore.cancelTimer(c) }, colors = ButtonDefaults.secondaryButtonColors()) {
                Text("✕")
            }
            Button(onClick = { if (WearStore.timerRunning) WearStore.pauseTimer(c) else WearStore.resumeTimer(c) }) {
                Text(if (WearStore.timerRunning) "❚❚" else "▶")
            }
            Button(onClick = { WearStore.addMinute(c) }, colors = ButtonDefaults.secondaryButtonColors()) {
                Text("+1", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StopwatchPage(now: Long, back: () -> Unit) {
    val c = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.stopwatch), fontSize = 13.sp)
        BigTime(stopwatchClock(WearStore.swElapsed(now)))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = back, colors = ButtonDefaults.secondaryButtonColors()) { Text("‹") }
            Button(onClick = { WearStore.swToggle(c) }) { Text(if (WearStore.swRunning) "❚❚" else "▶") }
            Button(onClick = { WearStore.swReset(c) }, colors = ButtonDefaults.secondaryButtonColors()) { Text("↺") }
        }
    }
}

@Composable
private fun RingPage() {
    val c = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.time_up), fontSize = 22.sp, textAlign = TextAlign.Center)
        Chip(
            onClick = { WearStore.stopRinging(c) },
            label = { Text(stringResource(R.string.stop)) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
