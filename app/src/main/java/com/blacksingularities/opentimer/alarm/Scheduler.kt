package com.blacksingularities.opentimer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.blacksingularities.opentimer.MainActivity
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Store

/**
 * Harmonogram w AlarmManagerze. Budziki idą przez `setAlarmClock` (ikona budzika na pasku stanu,
 * odporne na tryb Doze), minutniki i pomodoro – przez dokładne alarmy z wybudzeniem.
 * [sync] jest idempotentne: ustawia wszystko od nowa na podstawie stanu w [Store].
 */
object Scheduler {
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val KIND_ALARM = "alarm"
    const val KIND_PRE = "pre"
    const val KIND_TIMER = "timer"
    const val KIND_POMODORO = "pomodoro"

    private const val BASE_ALARM = 10_000
    private const val BASE_PRE = 20_000_000
    private const val BASE_TIMER = 40_000_000
    private const val CODE_POMODORO = 60_000_001
    private const val PREFS = "scheduler"

    private fun am(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun intent(context: Context, kind: String, id: Int, code: Int, flags: Int): PendingIntent? {
        val i = Intent(context, AlarmReceiver::class.java)
            .setAction("com.blacksingularities.opentimer.$kind.$id")
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, code, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pending(context: Context, kind: String, id: Int, code: Int) =
        intent(context, kind, id, code, PendingIntent.FLAG_UPDATE_CURRENT)!!

    private fun cancel(context: Context, kind: String, id: Int, code: Int) {
        intent(context, kind, id, code, PendingIntent.FLAG_NO_CREATE)?.let {
            am(context).cancel(it)
            it.cancel()
        }
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(context).canScheduleExactAlarms()

    private fun exact(context: Context, at: Long, pi: PendingIntent) {
        val manager = am(context)
        if (canExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun sync(context: Context) {
        val now = System.currentTimeMillis()
        val settings = Store.settings.value
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet("scheduled", emptySet()).orEmpty()
        val after = mutableSetOf<String>()
        val manager = am(context)

        val show = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "alarms")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        for (alarm in Store.alarms.value) {
            val at = TimeMath.nextTrigger(alarm, now) ?: continue
            val pi = pending(context, KIND_ALARM, alarm.id, BASE_ALARM + alarm.id)
            if (canExact(context)) manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            after += "$KIND_ALARM:${alarm.id}"
            val lead = settings.preAlarmMin * 60_000L
            if (lead > 0 && alarm.snoozedUntil <= now && at - lead > now) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC, at - lead, pending(context, KIND_PRE, alarm.id, BASE_PRE + alarm.id))
                after += "$KIND_PRE:${alarm.id}"
            }
        }
        for (timer in Store.timers.value) {
            if (!timer.running) continue
            exact(context, timer.endAt, pending(context, KIND_TIMER, timer.id, BASE_TIMER + timer.id))
            after += "$KIND_TIMER:${timer.id}"
        }
        val pomodoro = Store.pomodoro.value
        if (pomodoro.running) {
            exact(context, pomodoro.endAt, pending(context, KIND_POMODORO, 0, CODE_POMODORO))
            after += "$KIND_POMODORO:0"
        }

        for (stale in before - after) {
            val kind = stale.substringBefore(':')
            val id = stale.substringAfter(':').toIntOrNull() ?: continue
            val code = when (kind) {
                KIND_ALARM -> BASE_ALARM + id
                KIND_PRE -> BASE_PRE + id
                KIND_TIMER -> BASE_TIMER + id
                else -> CODE_POMODORO
            }
            cancel(context, kind, id, code)
        }
        prefs.edit().putStringSet("scheduled", after).apply()
    }
}
