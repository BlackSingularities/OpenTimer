package com.blacksingularities.opentimer.ui.screens

import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.data.AppSettings
import com.blacksingularities.opentimer.data.ClockStyle
import com.blacksingularities.opentimer.data.HourFormat
import com.blacksingularities.opentimer.data.StartTab
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.data.VolumeKeys
import com.blacksingularities.opentimer.i18n.AppLanguage
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.ring.RingService
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Route
import com.blacksingularities.opentimer.ui.components.ActionRow
import com.blacksingularities.opentimer.ui.components.ColorDot
import com.blacksingularities.opentimer.ui.components.Chips
import com.blacksingularities.opentimer.ui.components.CompactChoice
import com.blacksingularities.opentimer.ui.components.DarkDialog
import com.blacksingularities.opentimer.ui.components.Group
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.components.SwitchRow
import com.blacksingularities.opentimer.ui.theme.Palette

/** Ustawienia aplikacji: wygląd, budziki, minutnik, dźwięki, uprawnienia i aktualizacje. */
@Composable
fun SettingsScreen(vm: AppViewModel, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val s by Store.settings.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    val power = remember { context.getSystemService(PowerManager::class.java) }
    val exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager?.canScheduleExactAlarms() == true
    val notificationsAllowed = Notifications.allowed(context)
    val batteryFree = power?.isIgnoringBatteryOptimizations(context.packageName) == true

    val timerSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            vm.settings { it.copy(timerSound = uri?.toString() ?: RingService.SILENT) }
        }
    }

    SubScreen(tr("Ustawienia"), onBack = { vm.back() }) {
        Group(tr("Wygląd")) {
            CompactChoice(
                Icons.Rounded.Language,
                tr("Język"),
                AppLanguage.entries,
                s.language,
                label = { l -> when (l) { AppLanguage.SYSTEM -> tr("Jak w systemie"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" } },
                short = { l -> when (l) { AppLanguage.SYSTEM -> tr("Auto"); AppLanguage.PL -> "PL"; AppLanguage.EN -> "EN" } },
            ) { l -> vm.settings { it.copy(language = l) } }
            CompactChoice(
                Icons.Rounded.Watch,
                tr("Styl zegara"),
                ClockStyle.entries,
                s.clockStyle,
                label = { c -> when (c) { ClockStyle.DIGITAL -> tr("Cyfrowy"); ClockStyle.ANALOG -> tr("Analogowy"); ClockStyle.LCD -> tr("Wyświetlacz LCD") } },
                short = { c -> when (c) { ClockStyle.DIGITAL -> tr("Cyfr."); ClockStyle.ANALOG -> tr("Analog."); ClockStyle.LCD -> "LCD" } },
            ) { c -> vm.settings { it.copy(clockStyle = c) } }
            CompactChoice(
                Icons.Rounded.Schedule,
                tr("Format godziny"),
                HourFormat.entries,
                s.hourFormat,
                label = { h -> when (h) { HourFormat.SYSTEM -> tr("Jak w systemie"); HourFormat.H24 -> tr("24-godzinny"); HourFormat.H12 -> tr("12-godzinny (AM/PM)") } },
                short = { h -> when (h) { HourFormat.SYSTEM -> tr("Auto"); HourFormat.H24 -> "24"; HourFormat.H12 -> "12" } },
            ) { h -> vm.settings { it.copy(hourFormat = h) } }
            SwitchRow(Icons.Rounded.Timelapse, tr("Sekundy na zegarze"), tr("Widoczne na ekranie głównym"), s.showSeconds) { v -> vm.settings { it.copy(showSeconds = v) } }
            SwitchRow(
                Icons.Rounded.Home,
                tr("Zegar domowy"),
                if (s.homeZone.isBlank() || s.homeZone == java.time.ZoneId.systemDefault().id) {
                    tr("W podróży pokaże godzinę w domu ({0})", com.blacksingularities.opentimer.ui.Fmt.cityOf(s.homeZone.ifBlank { java.time.ZoneId.systemDefault().id }))
                } else {
                    tr("Dom: {0} · teraz {1}", com.blacksingularities.opentimer.ui.Fmt.cityOf(s.homeZone), com.blacksingularities.opentimer.ui.Fmt.time(context, System.currentTimeMillis(), java.time.ZoneId.of(s.homeZone)))
                },
                s.homeClock,
            ) { v -> vm.settings { it.copy(homeClock = v) } }
            ActionRow(Icons.Rounded.Public, tr("Tu jest mój dom"), tr("Ustaw obecną strefę jako domową")) {
                val zone = java.time.ZoneId.systemDefault().id
                vm.settings { it.copy(homeZone = zone) }
                vm.showSnack(tr("Dom: {0}", com.blacksingularities.opentimer.ui.Fmt.cityOf(zone)))
            }
            SwitchRow(Icons.Rounded.CalendarMonth, tr("Tydzień od poniedziałku"), tr("Kolejność dni przy budzikach"), s.weekStartsMonday) { v -> vm.settings { it.copy(weekStartsMonday = v) } }
            Chips(
                Icons.Rounded.OpenInFull,
                tr("Zakładka po otwarciu"),
                StartTab.entries,
                s.startTab,
                label = { t ->
                    when (t) {
                        StartTab.CLOCK -> tr("Zegar")
                        StartTab.ALARMS -> tr("Budzik")
                        StartTab.STOPWATCH -> tr("Stoper")
                        StartTab.TIMERS -> tr("Minutnik")
                        StartTab.MORE -> tr("Więcej")
                        StartTab.LAST -> tr("Ostatnia")
                    }
                },
            ) { t -> vm.settings { it.copy(startTab = t) } }
            SwitchRow(Icons.Rounded.TouchApp, tr("Wibracja przy dotknięciu"), tr("Krótkie potwierdzenie gestów"), s.haptics) { v -> vm.settings { it.copy(haptics = v) } }
            SwitchRow(Icons.Rounded.ScreenLockPortrait, tr("Nie gaś ekranu"), tr("Ekran świeci, gdy aplikacja jest otwarta"), s.keepScreenOn) { v -> vm.settings { it.copy(keepScreenOn = v) } }
        }

        Group(tr("Budziki")) {
            SliderRow(Icons.Rounded.Snooze, tr("Domyślna drzemka"), tr("{0} min", s.defaultSnooze), s.defaultSnooze, 1..60) { v -> vm.settings { it.copy(defaultSnooze = v) } }
            SliderRow(Icons.Rounded.VolumeUp, tr("Narastanie głośności"), if (s.defaultRamp == 0) tr("od razu pełna") else tr("{0} s", s.defaultRamp), s.defaultRamp, 0..120) { v -> vm.settings { it.copy(defaultRamp = v) } }
            SliderRow(Icons.Rounded.Timer, tr("Samoczynne wyciszenie"), if (s.autoSilenceMin == 0) tr("nigdy") else tr("po {0} min", s.autoSilenceMin), s.autoSilenceMin, 0..60) { v -> vm.settings { it.copy(autoSilenceMin = v) } }
            CompactChoice(
                Icons.Rounded.Tune,
                tr("Klawisze głośności"),
                VolumeKeys.entries,
                s.volumeKeys,
                label = { k -> when (k) { VolumeKeys.NOTHING -> tr("Nic nie robią"); VolumeKeys.SNOOZE -> tr("Drzemka"); VolumeKeys.DISMISS -> tr("Wyłączają budzik") } },
                short = { k -> when (k) { VolumeKeys.NOTHING -> tr("Nic"); VolumeKeys.SNOOZE -> tr("Drzemka"); VolumeKeys.DISMISS -> tr("Wyłącz") } },
                hint = tr("Podczas dzwonienia"),
            ) { k -> vm.settings { it.copy(volumeKeys = k) } }
            SwitchRow(Icons.Rounded.ScreenRotation, tr("Obróć telefon, aby uciszyć"), tr("Położenie ekranem w dół włącza drzemkę"), s.flipToSnooze) { v -> vm.settings { it.copy(flipToSnooze = v) } }
            SliderRow(Icons.Rounded.NotificationsActive, tr("Zapowiedź budzika"), if (s.preAlarmMin == 0) tr("bez zapowiedzi") else tr("{0} min przed", s.preAlarmMin), s.preAlarmMin, 0..180) { v -> vm.settings { it.copy(preAlarmMin = v) } }
        }

        Group(tr("Minutnik i stoper")) {
            ActionRow(Icons.Rounded.MusicNote, tr("Dźwięk minutnika"), soundName(context, s.timerSound)) {
                timerSound.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            s.timerSound.takeIf { it.isNotBlank() && it != RingService.SILENT }?.let(Uri::parse),
                        ),
                )
            }
            SwitchRow(Icons.Rounded.Vibration, tr("Wibracje minutnika"), null, s.timerVibrate) { v -> vm.settings { it.copy(timerVibrate = v) } }
            SliderRow(Icons.Rounded.VolumeUp, tr("Narastanie minutnika"), if (s.timerRamp == 0) tr("od razu pełna") else tr("{0} s", s.timerRamp), s.timerRamp, 0..60) { v -> vm.settings { it.copy(timerRamp = v) } }
            SwitchRow(Icons.Rounded.OpenInFull, tr("Pełny ekran po czasie"), tr("Minutnik budzi ekran jak budzik"), s.timerFullScreen) { v -> vm.settings { it.copy(timerFullScreen = v) } }
            SwitchRow(Icons.Rounded.Speed, tr("Setne sekundy w stoperze"), null, s.stopwatchMs) { v -> vm.settings { it.copy(stopwatchMs = v) } }
        }

        Group(tr("Interwały")) {
            SwitchRow(Icons.Rounded.Campaign, tr("Zapowiedzi odcinków"), tr("Mówi „praca”, „odpoczynek”, „koniec”"), s.intervalVoice) { v -> vm.settings { it.copy(intervalVoice = v) } }
            SwitchRow(Icons.Rounded.MusicNote, tr("Piknięcia na ostatnich sekundach"), null, s.intervalBeeps) { v -> vm.settings { it.copy(intervalBeeps = v) } }
            ActionRow(Icons.Rounded.DirectionsRun, tr("Plany treningów"), tr("Tabata, HIIT i własne")) { vm.open(Route.Intervals) }
        }

        Group(tr("Zegar nocny")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFFBF5AF2, 0xFFFFFFFF).forEach { c ->
                    ColorDot(c.toInt(), s.nightColor == c.toInt()) { vm.settings { it.copy(nightColor = c.toInt()) } }
                }
            }
            SliderRow(Icons.Rounded.Lightbulb, tr("Przyciemnienie"), "${(s.nightDim * 100).toInt()}%", (s.nightDim * 100).toInt(), 0..95) { v -> vm.settings { it.copy(nightDim = v / 100f) } }
            SwitchRow(Icons.Rounded.Timelapse, tr("Sekundy w zegarze nocnym"), null, s.nightSeconds) { v -> vm.settings { it.copy(nightSeconds = v) } }
            ActionRow(Icons.Rounded.Bedtime, tr("Włącz zegar nocny"), tr("Dotknięcie ekranu pokazuje przyciski")) { vm.open(Route.NightClock) }
        }

        Group(tr("Uprawnienia")) {
            ActionRow(
                Icons.Rounded.Notifications,
                tr("Powiadomienia"),
                if (notificationsAllowed) tr("Włączone – budziki i minutniki widać na ekranie") else tr("Wyłączone – dotknij, aby zezwolić"),
            ) {
                if (notificationsAllowed) {
                    vm.openIntent(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                } else {
                    onRequestNotifications()
                }
            }
            ActionRow(
                Icons.Rounded.Alarm,
                tr("Dokładne alarmy"),
                if (exactAllowed) tr("Włączone – budzik zadzwoni co do minuty") else tr("Wyłączone – budzik może się spóźnić"),
            ) {
                if (Build.VERSION.SDK_INT >= 31) {
                    vm.openIntent(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                }
            }
            ActionRow(
                Icons.Rounded.Battery5Bar,
                tr("Bez oszczędzania baterii"),
                if (batteryFree) tr("Włączone – system nie usypia budzików") else tr("Zalecane, gdy budziki się spóźniają"),
            ) {
                vm.openIntent(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        Group(tr("Aktualizacje")) {
            SwitchRow(Icons.Rounded.Download, tr("Sprawdzaj samoczynnie"), tr("Co kilka godzin, gdy aplikacja jest otwarta"), s.autoUpdate) { v -> vm.settings { it.copy(autoUpdate = v) } }
            ActionRow(Icons.Rounded.Info, tr("O aplikacji"), tr("Wersja, autor i wsparcie")) { vm.open(Route.About) }
        }

        Group(tr("Dane")) {
            ActionRow(Icons.Rounded.RestartAlt, tr("Przywróć domyślne ustawienia"), tr("Budziki, minutniki i wydarzenia zostają"), danger = true) { confirmReset = true }
        }

        Text(
            tr("Pomysł i wykonanie: {0} · {1}", AUTHOR, AUTHOR_SITE),
            fontSize = 11.sp,
            color = Palette.TextFaint,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (confirmReset) DarkDialog(
        title = tr("Przywrócić domyślne ustawienia?"),
        onDismiss = { confirmReset = false },
        buttons = {
            PillButton(tr("Anuluj"), primary = false) { confirmReset = false }
            PillButton(tr("Przywróć"), danger = true) {
                val keep = Store.settings.value
                vm.settings { AppSettings(lastTab = keep.lastTab, lastUpdateCheck = keep.lastUpdateCheck, onboarded = true) }
                Actions.resync(context)
                confirmReset = false
                vm.showSnack(tr("Ustawienia przywrócone"))
            }
        },
    ) {
        Text(tr("Wygląd, dźwięki i zachowanie wrócą do stanu początkowego. Budziki, minutniki, okrążenia i wydarzenia nie zostaną zmienione."), fontSize = 13.sp, color = Palette.TextDim)
    }
}
