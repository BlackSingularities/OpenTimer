package com.blacksingularities.opentimer.ui

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.opentimer.data.AppSettings
import com.blacksingularities.opentimer.data.StartTab
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.update.UpdateManager
import com.blacksingularities.opentimer.update.UpdateState
import com.blacksingularities.opentimer.update.isNewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Zakładki dolnego paska. */
enum class Tab { CLOCK, ALARMS, STOPWATCH, TIMERS, MORE }

/** Ekrany otwierane nad zakładkami. */
sealed interface Route {
    data class AlarmEditor(val alarmId: Int?, val hour: Int = -1, val minute: Int = -1) : Route
    data object Settings : Route
    data object Intervals : Route
    data object Pomodoro : Route
    data object Countdowns : Route
    data object Metronome : Route
    data object NightClock : Route
    data object ZonePicker : Route
    data object Calculator : Route
    data object About : Route
}

/** Komunikat z przyciskiem „Cofnij”. */
data class Snack(val text: String, val undo: (() -> Unit)?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val ctx get() = getApplication<Application>()

    var tab by mutableStateOf(initialTab())
    val stack = mutableStateListOf<Route>()
    var snack by mutableStateOf<Snack?>(null)
        private set
    private var snackToken = 0

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
    var updateBannerHidden by mutableStateOf(false)

    private fun initialTab(): Tab {
        val s = Store.settings.value
        return when (s.startTab) {
            StartTab.CLOCK -> Tab.CLOCK
            StartTab.ALARMS -> Tab.ALARMS
            StartTab.STOPWATCH -> Tab.STOPWATCH
            StartTab.TIMERS -> Tab.TIMERS
            StartTab.MORE -> Tab.MORE
            StartTab.LAST -> Tab.entries.getOrElse(s.lastTab) { Tab.CLOCK }
        }
    }

    fun select(t: Tab) {
        tab = t
        stack.clear()
        if (Store.settings.value.lastTab != t.ordinal) Store.updateSettings { it.copy(lastTab = t.ordinal) }
    }

    fun open(route: Route) {
        stack.add(route)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun settings(change: (AppSettings) -> AppSettings) {
        val before = Store.settings.value
        val after = Store.updateSettings(change)
        if (before.language != after.language) I18n.apply(after.language)
        if (before.preAlarmMin != after.preAlarmMin || before.hourFormat != after.hourFormat) {
            com.blacksingularities.opentimer.core.Actions.resync(ctx)
        }
    }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    fun showSnack(text: String, undo: (() -> Unit)? = null) {
        val token = ++snackToken
        snack = Snack(text, undo)
        viewModelScope.launch {
            delay(4500)
            if (snackToken == token) snack = null
        }
    }

    fun hideSnack() {
        snack = null
    }

    fun openIntent(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast(tr("Brak aplikacji do otwarcia")) }
    }

    /** Obsługa skrótów, powiadomień i intencji systemowych (SET_ALARM, SET_TIMER…). */
    fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            android.provider.AlarmClock.ACTION_SET_ALARM -> {
                val h = intent.getIntExtra(android.provider.AlarmClock.EXTRA_HOUR, -1)
                val m = intent.getIntExtra(android.provider.AlarmClock.EXTRA_MINUTES, 0)
                select(Tab.ALARMS)
                if (h >= 0 && intent.getBooleanExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)) {
                    val a = com.blacksingularities.opentimer.core.Actions.newAlarm(h, m)
                        .copy(label = intent.getStringExtra(android.provider.AlarmClock.EXTRA_MESSAGE).orEmpty())
                    com.blacksingularities.opentimer.core.Actions.saveAlarm(ctx, a)
                    toast(tr("Budzik ustawiony na {0}", Fmt.alarmTime(ctx, a)))
                } else {
                    open(Route.AlarmEditor(null, h, m))
                }
                return
            }
            android.provider.AlarmClock.ACTION_SET_TIMER -> {
                val sec = intent.getIntExtra(android.provider.AlarmClock.EXTRA_LENGTH, 0)
                select(Tab.TIMERS)
                if (sec > 0) com.blacksingularities.opentimer.core.Actions.addTimer(
                    ctx, sec * 1000L, intent.getStringExtra(android.provider.AlarmClock.EXTRA_MESSAGE).orEmpty(),
                )
                return
            }
            android.provider.AlarmClock.ACTION_SHOW_ALARMS -> { select(Tab.ALARMS); return }
            android.provider.AlarmClock.ACTION_SHOW_TIMERS -> { select(Tab.TIMERS); return }
        }
        when (intent.getStringExtra(com.blacksingularities.opentimer.MainActivity.EXTRA_OPEN)) {
            "alarms" -> select(Tab.ALARMS)
            "new_alarm" -> { select(Tab.ALARMS); open(Route.AlarmEditor(null)) }
            "timers", "timer" -> select(Tab.TIMERS)
            "stopwatch" -> select(Tab.STOPWATCH)
            "pomodoro" -> { select(Tab.MORE); open(Route.Pomodoro) }
            "night" -> { select(Tab.CLOCK); open(Route.NightClock) }
        }
    }

    // ---- Aktualizacje ------------------------------------------------------------------------

    fun checkUpdateIfDue() {
        val s = Store.settings.value
        if (!s.autoUpdate) return
        if (System.currentTimeMillis() - s.lastUpdateCheck < 6 * 60 * 60_000L) return
        checkUpdate(manual = false)
    }

    fun checkUpdate(manual: Boolean = true) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading || update is UpdateState.Installing) return
        Store.updateSettings { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        update = UpdateState.Checking
        viewModelScope.launch {
            update = runCatching {
                val info = UpdateManager.check()
                if (isNewer(info.version, UpdateManager.currentVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            }.getOrElse { e -> if (manual) UpdateState.Failed(e.message ?: tr("Brak połączenia")) else UpdateState.Idle }
            if (update is UpdateState.Available) updateBannerHidden = false
        }
    }

    fun updateNow() {
        when (val u = update) {
            is UpdateState.Available -> {
                if (!UpdateManager.canInstall(ctx)) {
                    UpdateManager.openInstallPermissionSettings(ctx)
                    toast(tr("Zezwól na instalację aktualizacji i dotknij ponownie"))
                    return
                }
                viewModelScope.launch {
                    update = UpdateState.Downloading(u.info, 0f)
                    update = runCatching {
                        val f = UpdateManager.download(ctx, u.info) { p -> update = UpdateState.Downloading(u.info, p) }
                        UpdateState.Ready(u.info, f)
                    }.getOrElse { UpdateState.Failed(it.message ?: tr("Pobieranie nie powiodło się")) }
                    (update as? UpdateState.Ready)?.let { install(it) }
                }
            }
            is UpdateState.Ready -> install(u)
            else -> checkUpdate()
        }
    }

    private fun install(r: UpdateState.Ready) {
        update = UpdateState.Installing(r.info)
        viewModelScope.launch {
            runCatching { UpdateManager.install(ctx, r.file) }
                .onFailure { update = UpdateState.Failed(it.message ?: tr("Instalacja nieudana")) }
            delay(8000)
            if (update is UpdateState.Installing) update = r
        }
    }

    val updatePending: Boolean
        get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing
}
