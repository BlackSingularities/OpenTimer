package com.blacksingularities.opentimer.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.animateColorAsState
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.screens.AlarmEditorScreen
import com.blacksingularities.opentimer.ui.screens.AboutScreen
import com.blacksingularities.opentimer.ui.screens.AlarmsTab
import com.blacksingularities.opentimer.ui.screens.CalculatorScreen
import com.blacksingularities.opentimer.ui.screens.ClockTab
import com.blacksingularities.opentimer.ui.screens.CountdownScreen
import com.blacksingularities.opentimer.ui.screens.IntervalScreen
import com.blacksingularities.opentimer.ui.screens.MetronomeScreen
import com.blacksingularities.opentimer.ui.screens.MoreTab
import com.blacksingularities.opentimer.ui.screens.NightClockScreen
import com.blacksingularities.opentimer.ui.screens.PomodoroScreen
import com.blacksingularities.opentimer.ui.screens.SettingsScreen
import com.blacksingularities.opentimer.ui.screens.StopwatchTab
import com.blacksingularities.opentimer.ui.screens.TimersTab
import com.blacksingularities.opentimer.ui.screens.ZonePickerScreen
import com.blacksingularities.opentimer.ui.theme.BlockShape
import com.blacksingularities.opentimer.ui.theme.GlassScope
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.glass
import com.blacksingularities.opentimer.ui.theme.reactive

@Composable
fun AppRoot(vm: AppViewModel, onRequestNotifications: () -> Unit) {
    val settings by Store.settings.collectAsState()
    // Odczyt języka – zmiana w ustawieniach przerysowuje całe drzewo.
    @Suppress("UNUSED_VARIABLE") val english = I18n.english
    val activity = LocalActivity.current
    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }
    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }
    BackHandler(enabled = vm.stack.isEmpty() && vm.tab != Tab.CLOCK && settings.startTab == com.blacksingularities.opentimer.data.StartTab.CLOCK) {
        vm.select(Tab.CLOCK)
    }

    GlassScope(haptics = settings.haptics) {
        Column(Modifier.fillMaxSize().background(Palette.Ink)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = vm.tab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.CLOCK -> ClockTab(vm)
                        Tab.ALARMS -> AlarmsTab(vm, onRequestNotifications)
                        Tab.STOPWATCH -> StopwatchTab(vm)
                        Tab.TIMERS -> TimersTab(vm, onRequestNotifications)
                        Tab.MORE -> MoreTab(vm)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Palette.Ink).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TabButton(Icons.Rounded.Schedule, tr("Zegar"), vm.tab == Tab.CLOCK) { vm.select(Tab.CLOCK) }
                TabButton(Icons.Rounded.Alarm, tr("Budzik"), vm.tab == Tab.ALARMS) { vm.select(Tab.ALARMS) }
                TabButton(Icons.Rounded.Timer, tr("Stoper"), vm.tab == Tab.STOPWATCH) { vm.select(Tab.STOPWATCH) }
                TabButton(Icons.Rounded.HourglassBottom, tr("Minutnik"), vm.tab == Tab.TIMERS) { vm.select(Tab.TIMERS) }
                TabButton(Icons.Rounded.GridView, tr("Więcej"), vm.tab == Tab.MORE) { vm.select(Tab.MORE) }
            }
        }

        val top = vm.stack.lastOrNull()
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                if (targetState != null && (initialState == null || vm.stack.size > 1)) {
                    slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                } else {
                    fadeIn(tween(150)) togetherWith slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(180))
                }
            },
            label = "route",
        ) { r ->
            if (r != null) Box(Modifier.fillMaxSize().background(Palette.Ink)) {
                when (r) {
                    is Route.AlarmEditor -> AlarmEditorScreen(vm, r)
                    Route.Settings -> SettingsScreen(vm, onRequestNotifications)
                    Route.Intervals -> IntervalScreen(vm)
                    Route.Pomodoro -> PomodoroScreen(vm)
                    Route.Countdowns -> CountdownScreen(vm)
                    Route.Metronome -> MetronomeScreen(vm)
                    Route.NightClock -> NightClockScreen(vm)
                    Route.ZonePicker -> ZonePickerScreen(vm)
                    Route.Calculator -> CalculatorScreen(vm)
                    Route.About -> AboutScreen(vm)
                }
            }
        }

        AnimatedVisibility(
            visible = vm.snack != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 76.dp, start = 16.dp, end = 16.dp),
        ) {
            val s = vm.snack
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s?.text.orEmpty(), fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (s?.undo != null) {
                    Text(
                        tr("Cofnij"),
                        color = Palette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(BlockShape).background(Color.White)
                            .reactive(tr("Cofnij")) { s.undo.invoke(); vm.hideSnack() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    // Styl wspólny dla aplikacji Open: wybrana zakładka to biały kafel z czarną ikoną i podpisem.
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "tabBg")
    val tint by animateColorAsState(if (selected) Color.Black else Palette.TextDim, label = "tabTint")
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .reactive(label, onClick = onClick)
            .padding(top = 7.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}
