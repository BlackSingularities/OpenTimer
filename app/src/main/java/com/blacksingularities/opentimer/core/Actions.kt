package com.blacksingularities.opentimer.core

import android.content.Context
import com.blacksingularities.opentimer.alarm.Scheduler
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.CountdownEvent
import com.blacksingularities.opentimer.data.Pomodoro
import com.blacksingularities.opentimer.data.PomodoroPhase
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.data.TimerItem
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.widget.ClockWidget
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wszystkie zmiany stanu przechodzą tędy – z interfejsu, powiadomień i odbiorników alarmów.
 * Po każdej zmianie odświeżany jest harmonogram, powiadomienia o odliczaniu i widżet.
 */
object Actions {
    private fun after(context: Context) {
        Scheduler.sync(context)
        Notifications.refresh(context)
        ClockWidget.refresh(context)
    }

    /** Po zmianie ustawień, strefy czasowej, restarcie telefonu. */
    fun resync(context: Context) {
        val now = System.currentTimeMillis()
        Store.updateAlarms { list ->
            list.map { a ->
                var x = a
                if (x.snoozedUntil in 1..now) x = x.copy(snoozedUntil = 0, snoozeCount = 0)
                if (x.skipUntil in 1..now) x = x.copy(skipUntil = 0)
                x
            }
        }
        after(context)
    }

    // ---------- Budziki ----------

    fun saveAlarm(context: Context, alarm: Alarm) {
        Store.updateAlarms { list ->
            val fixed = alarm.copy(snoozedUntil = 0, snoozeCount = 0, skipUntil = 0)
            if (list.any { it.id == alarm.id }) list.map { if (it.id == alarm.id) fixed else it } else list + fixed
        }
        Notifications.cancelUpcoming(context, alarm.id)
        after(context)
    }

    fun newAlarm(hour: Int, minute: Int): Alarm {
        val s = Store.settings.value
        return Alarm(id = Store.nextId(), hour = hour, minute = minute, snoozeMinutes = s.defaultSnooze, rampSeconds = s.defaultRamp)
    }

    fun deleteAlarm(context: Context, id: Int) {
        Store.updateAlarms { list -> list.filterNot { it.id == id } }
        Notifications.cancelUpcoming(context, id)
        after(context)
    }

    fun restoreAlarm(context: Context, alarm: Alarm) {
        Store.updateAlarms { list -> list.filterNot { it.id == alarm.id } + alarm }
        after(context)
    }

    fun setAlarmEnabled(context: Context, id: Int, enabled: Boolean) {
        Store.updateAlarms { list ->
            list.map { if (it.id == id) it.copy(enabled = enabled, snoozedUntil = 0, snoozeCount = 0, skipUntil = 0) else it }
        }
        if (!enabled) Notifications.cancelUpcoming(context, id)
        after(context)
    }

    fun setAllAlarms(context: Context, enabled: Boolean) {
        Store.updateAlarms { list -> list.map { it.copy(enabled = enabled, snoozedUntil = 0, snoozeCount = 0, skipUntil = 0) } }
        after(context)
    }

    /** Pomija najbliższe dzwonienie budzika cyklicznego (jednorazowy po prostu się wyłącza). */
    fun skipNext(context: Context, id: Int) {
        val now = System.currentTimeMillis()
        Store.updateAlarms { list ->
            list.map { a ->
                if (a.id != id) a
                else if (a.days == 0) a.copy(enabled = false, snoozedUntil = 0, snoozeCount = 0)
                else {
                    val next = TimeMath.firstAfter(a.copy(skipUntil = 0, snoozedUntil = 0), now) ?: 0L
                    a.copy(skipUntil = next, snoozedUntil = 0, snoozeCount = 0)
                }
            }
        }
        Notifications.cancelUpcoming(context, id)
        after(context)
    }

    fun cancelSkip(context: Context, id: Int) {
        Store.updateAlarms { list -> list.map { if (it.id == id) it.copy(skipUntil = 0) else it } }
        after(context)
    }

    /** Szybka drzemka: jednorazowy budzik za [minutes] minut, usuwany po wyłączeniu. */
    fun nap(context: Context, minutes: Int): Alarm {
        val zone = ZoneId.systemDefault()
        val at = java.time.ZonedDateTime.now(zone).plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
        val alarm = newAlarm(at.hour, at.minute).copy(
            label = "",
            oneShotDate = at.toLocalDate().toEpochDay(),
            deleteAfter = true,
        )
        saveAlarm(context, alarm)
        return alarm
    }

    /** Budzik zadzwonił: jednorazowy wyłącza się od razu, cykliczny przechodzi na kolejny termin. */
    fun onAlarmFired(context: Context, id: Int): Alarm? {
        var fired: Alarm? = null
        Store.updateAlarms { list ->
            list.mapNotNull { a ->
                if (a.id != id) return@mapNotNull a
                fired = a
                if (a.days == 0) {
                    if (a.deleteAfter) null else a.copy(enabled = false, snoozedUntil = 0, skipUntil = 0)
                } else {
                    a.copy(snoozedUntil = 0, skipUntil = 0)
                }
            }
        }
        Notifications.cancelUpcoming(context, id)
        after(context)
        return fired
    }

    fun snooze(context: Context, alarm: Alarm, minutes: Int = alarm.snoozeMinutes) {
        val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        Store.updateAlarms { list ->
            val current = list.firstOrNull { it.id == alarm.id }
            val next = (current ?: alarm).copy(enabled = true, snoozedUntil = until, snoozeCount = alarm.snoozeCount + 1)
            list.filterNot { it.id == alarm.id } + next
        }
        after(context)
    }

    /** Wyłączenie po dzwonieniu: koniec drzemek, jednorazowy budzik zostaje wyłączony lub usunięty. */
    fun dismissAlarm(context: Context, alarm: Alarm) {
        Store.updateAlarms { list ->
            list.mapNotNull { a ->
                if (a.id != alarm.id) a
                else if (a.days == 0 && a.deleteAfter) null
                else if (a.days == 0) a.copy(enabled = false, snoozedUntil = 0, snoozeCount = 0)
                else a.copy(snoozedUntil = 0, snoozeCount = 0)
            }
        }
        after(context)
    }

    // ---------- Minutniki ----------

    fun addTimer(context: Context, durationMs: Long, label: String = "", start: Boolean = true): TimerItem {
        val now = System.currentTimeMillis()
        val t = TimerItem(
            id = Store.nextId(), label = label, durationMs = durationMs, remainingMs = durationMs,
            running = start, endAt = if (start) now + durationMs else 0L, createdAt = now,
        )
        Store.updateTimers { it + t }
        after(context)
        return t
    }

    private fun changeTimer(context: Context, id: Int, change: (TimerItem, Long) -> TimerItem?) {
        val now = System.currentTimeMillis()
        Store.updateTimers { list -> list.mapNotNull { if (it.id == id) change(it, now) else it } }
        after(context)
    }

    fun startTimer(context: Context, id: Int) = changeTimer(context, id) { t, now ->
        val left = if (t.remainingMs <= 0) t.durationMs else t.remainingMs
        t.copy(running = true, ringing = false, endAt = now + left, remainingMs = left)
    }

    fun pauseTimer(context: Context, id: Int) = changeTimer(context, id) { t, now ->
        if (!t.running) t else t.copy(running = false, remainingMs = (t.endAt - now).coerceAtLeast(0), endAt = 0)
    }

    fun resetTimer(context: Context, id: Int) = changeTimer(context, id) { t, _ ->
        t.copy(running = false, ringing = false, remainingMs = t.durationMs, endAt = 0)
    }

    fun addToTimer(context: Context, id: Int, ms: Long) = changeTimer(context, id) { t, now ->
        when {
            t.ringing -> t.copy(ringing = false, running = true, endAt = now + ms, remainingMs = ms, durationMs = ms)
            t.running -> t.copy(endAt = t.endAt + ms, durationMs = t.durationMs + ms)
            else -> t.copy(remainingMs = t.remainingMs + ms, durationMs = t.durationMs + ms)
        }
    }

    fun renameTimer(context: Context, id: Int, label: String) = changeTimer(context, id) { t, _ -> t.copy(label = label) }

    fun deleteTimer(context: Context, id: Int) = changeTimer(context, id) { _, _ -> null }

    fun onTimerFired(context: Context, id: Int): TimerItem? {
        var fired: TimerItem? = null
        changeTimer(context, id) { t, _ ->
            if (!t.running) t else t.copy(running = false, ringing = true, remainingMs = 0).also { fired = it }
        }
        return fired
    }

    /** Wyłączenie dzwoniących minutników – wracają do pełnego czasu, gotowe do ponownego startu. */
    fun stopRingingTimers(context: Context, ids: Collection<Int>) {
        Store.updateTimers { list ->
            list.map { if (it.id in ids && it.ringing) it.copy(ringing = false, remainingMs = it.durationMs, endAt = 0) else it }
        }
        after(context)
    }

    // ---------- Stoper ----------

    fun stopwatchStart(context: Context) {
        Store.updateStopwatch { if (it.running) it else it.copy(running = true, startedAt = System.currentTimeMillis()) }
        after(context)
    }

    fun stopwatchPause(context: Context) {
        val now = System.currentTimeMillis()
        Store.updateStopwatch { if (!it.running) it else it.copy(running = false, accumulatedMs = it.elapsedAt(now)) }
        after(context)
    }

    fun stopwatchLap(context: Context) {
        val now = System.currentTimeMillis()
        Store.updateStopwatch { if (it.isReset) it else it.copy(laps = it.laps + it.elapsedAt(now)) }
        after(context)
    }

    fun stopwatchReset(context: Context) {
        Store.updateStopwatch { com.blacksingularities.opentimer.data.Stopwatch() }
        after(context)
    }

    // ---------- Pomodoro ----------

    fun pomodoroStart(context: Context) {
        val now = System.currentTimeMillis()
        Store.updatePomodoro { p ->
            if (p.running) p else {
                val left = if (p.remainingMs <= 0) p.minutesOf(p.phase) * 60_000L else p.remainingMs
                p.copy(running = true, endAt = now + left, remainingMs = left)
            }
        }
        after(context)
    }

    fun pomodoroPause(context: Context) {
        val now = System.currentTimeMillis()
        Store.updatePomodoro { p -> if (!p.running) p else p.copy(running = false, remainingMs = (p.endAt - now).coerceAtLeast(0), endAt = 0) }
        after(context)
    }

    fun pomodoroReset(context: Context) {
        Store.updatePomodoro { p -> p.copy(running = false, phase = PomodoroPhase.FOCUS, cycle = 0, endAt = 0, remainingMs = p.focusMin * 60_000L) }
        after(context)
    }

    fun pomodoroConfigure(context: Context, change: (Pomodoro) -> Pomodoro) {
        Store.updatePomodoro { p ->
            val n = change(p)
            if (n.running) n else n.copy(remainingMs = n.minutesOf(n.phase) * 60_000L)
        }
        after(context)
    }

    /** Pominięcie bieżącej fazy (bez zaliczania sesji skupienia). */
    fun pomodoroSkip(context: Context) {
        Store.updatePomodoro { p -> TimeMath.nextPomodoro(p) }
        after(context)
    }

    /** Faza dobiegła końca – zaliczenie sesji, przejście dalej i ewentualny automatyczny start. */
    fun onPomodoroFired(context: Context) {
        val now = System.currentTimeMillis()
        val before = Store.pomodoro.value
        if (!before.running) return
        val today = LocalDate.now().toEpochDay()
        val next = Store.updatePomodoro { p ->
            var counted = p
            if (p.phase == PomodoroPhase.FOCUS) {
                val todayCount = if (p.doneDay == today) p.doneToday + 1 else 1
                counted = p.copy(doneToday = todayCount, doneDay = today, doneTotal = p.doneTotal + 1, focusTotalMin = p.focusTotalMin + p.focusMin)
            }
            val n = TimeMath.nextPomodoro(counted)
            if (n.autoStart) n.copy(running = true, endAt = now + n.remainingMs) else n
        }
        Notifications.phaseDone(context, before.phase, next.phase, next.running)
        after(context)
    }

    // ---------- Odliczanie do wydarzeń i strefy ----------

    fun saveEvent(event: CountdownEvent) {
        Store.updateEvents { list -> list.filterNot { it.id == event.id } + event }
    }

    fun deleteEvent(id: Int) {
        Store.updateEvents { list -> list.filterNot { it.id == id } }
    }
}
