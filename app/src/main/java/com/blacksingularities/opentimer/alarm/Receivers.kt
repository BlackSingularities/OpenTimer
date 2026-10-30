package com.blacksingularities.opentimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.ring.RingService

private fun ready(context: Context) {
    Store.init(context.applicationContext)
    I18n.apply(Store.settings.value.language)
}

/** Termin z AlarmManagera: budzik, przypomnienie przed budzikiem, koniec minutnika lub fazy pomodoro. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ready(context)
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)
        when (intent.getStringExtra(Scheduler.EXTRA_KIND)) {
            Scheduler.KIND_ALARM -> {
                val alarm = Actions.onAlarmFired(context, id) ?: return
                RingService.ringAlarm(context, alarm)
            }
            Scheduler.KIND_PRE -> {
                val alarm = Store.alarms.value.firstOrNull { it.id == id } ?: return
                val at = TimeMath.nextTrigger(alarm, System.currentTimeMillis()) ?: return
                Notifications.upcoming(context, alarm, at)
            }
            Scheduler.KIND_TIMER -> {
                Actions.onTimerFired(context, id) ?: return
                RingService.ringTimers(context)
            }
            Scheduler.KIND_POMODORO -> Actions.onPomodoroFired(context)
        }
    }
}

/** Przyciski w powiadomieniach. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ready(context)
        val id = intent.getIntExtra(EXTRA_ID, 0)
        when (intent.action) {
            TIMER_PAUSE -> Actions.pauseTimer(context, id)
            TIMER_RESUME -> Actions.startTimer(context, id)
            TIMER_ADD -> Actions.addToTimer(context, id, 60_000L)
            TIMER_DELETE -> Actions.deleteTimer(context, id)
            SW_PAUSE -> Actions.stopwatchPause(context)
            SW_RESUME -> Actions.stopwatchStart(context)
            SW_LAP -> Actions.stopwatchLap(context)
            SW_RESET -> Actions.stopwatchReset(context)
            POMO_PAUSE -> Actions.pomodoroPause(context)
            POMO_RESUME -> Actions.pomodoroStart(context)
            POMO_SKIP -> Actions.pomodoroSkip(context)
            ALARM_SKIP -> Actions.skipNext(context, id)
            ALARM_END_SNOOZE -> Store.alarms.value.firstOrNull { it.id == id }?.let { Actions.dismissAlarm(context, it) }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        private const val P = "com.blacksingularities.opentimer.action."
        const val TIMER_PAUSE = P + "TIMER_PAUSE"
        const val TIMER_RESUME = P + "TIMER_RESUME"
        const val TIMER_ADD = P + "TIMER_ADD"
        const val TIMER_DELETE = P + "TIMER_DELETE"
        const val SW_PAUSE = P + "SW_PAUSE"
        const val SW_RESUME = P + "SW_RESUME"
        const val SW_LAP = P + "SW_LAP"
        const val SW_RESET = P + "SW_RESET"
        const val POMO_PAUSE = P + "POMO_PAUSE"
        const val POMO_RESUME = P + "POMO_RESUME"
        const val POMO_SKIP = P + "POMO_SKIP"
        const val ALARM_SKIP = P + "ALARM_SKIP"
        const val ALARM_END_SNOOZE = P + "ALARM_END_SNOOZE"
    }
}

/** Po restarcie, zmianie czasu lub strefy – harmonogram ustawiany od nowa. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in KNOWN) return
        ready(context)
        Notifications.createChannels(context)
        // Minutnik, który skończył się, gdy telefon był wyłączony, dzwoni od razu.
        val now = System.currentTimeMillis()
        val overdue = Store.timers.value.filter { it.running && it.endAt <= now }
        Actions.resync(context)
        if (overdue.isNotEmpty()) {
            overdue.forEach { Actions.onTimerFired(context, it.id) }
            RingService.ringTimers(context)
        }
    }

    private companion object {
        val KNOWN = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
