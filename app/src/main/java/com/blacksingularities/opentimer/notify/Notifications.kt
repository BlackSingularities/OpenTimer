package com.blacksingularities.opentimer.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.blacksingularities.opentimer.MainActivity
import com.blacksingularities.opentimer.R
import com.blacksingularities.opentimer.alarm.ActionReceiver
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.PomodoroPhase
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.Fmt

/** Kanały i powiadomienia: trwające minutniki, stoper, pomodoro, nadchodzące budziki i drzemki. */
object Notifications {
    const val CH_RING = "ring"
    const val CH_RUNNING = "running"
    const val CH_UPCOMING = "upcoming"
    const val CH_PHASE = "phase"
    const val CH_MISSED = "missed"
    const val CH_UPDATES = "updates"

    const val ID_RING = 1
    const val ID_STOPWATCH = 2
    const val ID_POMODORO = 3
    private const val ID_TIMER = 1000
    private const val ID_PRE = 200_000
    private const val ID_SNOOZE = 300_000
    private const val ID_MISSED = 400_000
    const val GROUP_TIMERS = "timers"

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun createChannels(context: Context) {
        val nm = nm(context)
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, tr("Budziki i minutniki"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Dzwonienie budzików i koniec odliczania")
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, tr("Trwające odliczanie"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Minutniki, stoper i pomodoro w toku")
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPCOMING, tr("Nadchodzące budziki"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Przypomnienie przed budzikiem i drzemka w toku")
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PHASE, tr("Pomodoro"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Koniec skupienia i przerwy")
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MISSED, tr("Nieodebrane budziki"), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, tr("Aktualizacje"), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context, open: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun action(context: Context, action: String, id: Int = 0, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun act(context: Context, icon: Int, title: String, pi: PendingIntent) =
        Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, icon), title, pi).build()

    /** Odświeża wszystkie powiadomienia o trwającym odliczaniu na podstawie stanu w [Store]. */
    fun refresh(context: Context) {
        if (!allowed(context)) return
        val nm = nm(context)
        val now = System.currentTimeMillis()
        val active = nm.activeNotifications.map { it.id }.toSet()

        val timers = Store.timers.value.filter { !it.ringing && (it.running || it.remainingMs != it.durationMs) }
        val shown = mutableSetOf<Int>()
        for (t in timers) {
            val nid = ID_TIMER + t.id
            shown += nid
            val title = t.label.ifBlank { tr("Minutnik {0}", Fmt.durationShort(t.durationMs)) }
            val b = Notification.Builder(context, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(title)
                .setOngoing(t.running)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setGroup(GROUP_TIMERS)
                .setContentIntent(openApp(context, "timers", 10 + t.id))
                .setSortKey("%015d".format(t.remainingAt(now)))
            if (t.running) {
                b.setWhen(t.endAt).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                b.setContentText(tr("Zadzwoni o {0}", Fmt.time(context, t.endAt)))
                b.addAction(act(context, R.drawable.ic_stat_timer, tr("Pauza"), action(context, ActionReceiver.TIMER_PAUSE, t.id, 100 + t.id * 4)))
            } else {
                b.setShowWhen(false).setContentText(tr("Wstrzymany · zostało {0}", TimeMath.countdown(t.remainingMs)))
                b.addAction(act(context, R.drawable.ic_stat_timer, tr("Wznów"), action(context, ActionReceiver.TIMER_RESUME, t.id, 100 + t.id * 4)))
            }
            b.addAction(act(context, R.drawable.ic_stat_timer, tr("+1 min"), action(context, ActionReceiver.TIMER_ADD, t.id, 101 + t.id * 4)))
            b.addAction(act(context, R.drawable.ic_stat_timer, tr("Usuń"), action(context, ActionReceiver.TIMER_DELETE, t.id, 102 + t.id * 4)))
            runCatching { nm.notify(nid, b.build()) }
        }
        active.filter { it in ID_TIMER until ID_PRE && it !in shown }.forEach { nm.cancel(it) }

        val sw = Store.stopwatch.value
        if (sw.isReset) {
            nm.cancel(ID_STOPWATCH)
        } else {
            val elapsed = sw.elapsedAt(now)
            val b = Notification.Builder(context, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_stopwatch)
                .setContentTitle(tr("Stoper"))
                .setOngoing(sw.running)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context, "stopwatch", 3))
            if (sw.running) {
                b.setWhen(now - elapsed).setUsesChronometer(true).setShowWhen(true)
                b.setContentText(if (sw.laps.isEmpty()) tr("Odmierzanie") else tr("Okrążenie {0}", sw.laps.size + 1))
                b.addAction(act(context, R.drawable.ic_stat_stopwatch, tr("Pauza"), action(context, ActionReceiver.SW_PAUSE, 0, 50)))
                b.addAction(act(context, R.drawable.ic_stat_stopwatch, tr("Okrążenie"), action(context, ActionReceiver.SW_LAP, 0, 51)))
            } else {
                b.setShowWhen(false).setContentText(tr("Wstrzymany · {0}", TimeMath.clock(elapsed, centis = true)))
                b.addAction(act(context, R.drawable.ic_stat_stopwatch, tr("Wznów"), action(context, ActionReceiver.SW_RESUME, 0, 50)))
                b.addAction(act(context, R.drawable.ic_stat_stopwatch, tr("Wyzeruj"), action(context, ActionReceiver.SW_RESET, 0, 52)))
            }
            runCatching { nm.notify(ID_STOPWATCH, b.build()) }
        }

        val p = Store.pomodoro.value
        val fresh = !p.running && p.remainingMs == p.minutesOf(p.phase) * 60_000L
        if (fresh) {
            nm.cancel(ID_POMODORO)
        } else {
            val b = Notification.Builder(context, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(Fmt.phase(p.phase))
                .setOngoing(p.running)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context, "pomodoro", 4))
            if (p.running) {
                b.setWhen(p.endAt).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                b.setContentText(tr("Sesja {0} · dziś ukończono {1}", p.cycle + 1, p.doneToday))
                b.addAction(act(context, R.drawable.ic_stat_focus, tr("Pauza"), action(context, ActionReceiver.POMO_PAUSE, 0, 60)))
            } else {
                b.setShowWhen(false).setContentText(tr("Wstrzymane · zostało {0}", TimeMath.countdown(p.remainingMs)))
                b.addAction(act(context, R.drawable.ic_stat_focus, tr("Wznów"), action(context, ActionReceiver.POMO_RESUME, 0, 60)))
            }
            b.addAction(act(context, R.drawable.ic_stat_focus, tr("Pomiń"), action(context, ActionReceiver.POMO_SKIP, 0, 61)))
            runCatching { nm.notify(ID_POMODORO, b.build()) }
        }

        // Drzemki w toku – z możliwością wyłączenia budzika przed kolejnym dzwonieniem.
        val snoozed = Store.alarms.value.filter { it.snoozedUntil > now }
        val snoozeIds = snoozed.map { ID_SNOOZE + it.id }.toSet()
        for (a in snoozed) {
            val b = Notification.Builder(context, CH_UPCOMING)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(tr("Drzemka do {0}", Fmt.time(context, a.snoozedUntil)))
                .setContentText(a.label.ifBlank { tr("Budzik {0}", Fmt.alarmTime(context, a)) })
                .setOngoing(true)
                .setWhen(a.snoozedUntil).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                .setContentIntent(openApp(context, "alarms", 5))
                .addAction(act(context, R.drawable.ic_stat_alarm, tr("Wyłącz"), action(context, ActionReceiver.ALARM_END_SNOOZE, a.id, 70_000 + a.id)))
            runCatching { nm.notify(ID_SNOOZE + a.id, b.build()) }
        }
        active.filter { it in ID_SNOOZE until ID_MISSED && it !in snoozeIds }.forEach { nm.cancel(it) }
    }

    /** Przypomnienie przed budzikiem z możliwością pominięcia go. */
    fun upcoming(context: Context, alarm: Alarm, at: Long) {
        if (!allowed(context)) return
        val b = Notification.Builder(context, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(tr("Nadchodzący budzik"))
            .setContentText(listOf(Fmt.dayAndTime(context, at), alarm.label).filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(openApp(context, "alarms", 6))
            .setTimeoutAfter((at - System.currentTimeMillis()).coerceAtLeast(1000))
            .addAction(act(context, R.drawable.ic_stat_alarm, tr("Pomiń ten raz"), action(context, ActionReceiver.ALARM_SKIP, alarm.id, 80_000 + alarm.id)))
        runCatching { nm(context).notify(ID_PRE + alarm.id, b.build()) }
    }

    fun cancelUpcoming(context: Context, id: Int) = nm(context).cancel(ID_PRE + id)

    fun missed(context: Context, title: String, text: String, id: Int) {
        if (!allowed(context)) return
        val b = Notification.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "alarms", 7))
        runCatching { nm(context).notify(ID_MISSED + id, b.build()) }
    }

    /** Koniec fazy pomodoro – zwykłe powiadomienie z dźwiękiem. */
    fun phaseDone(context: Context, finished: PomodoroPhase, next: PomodoroPhase, autoStarted: Boolean) {
        if (!allowed(context)) return
        val title = if (finished == PomodoroPhase.FOCUS) tr("Koniec skupienia") else tr("Koniec przerwy")
        val text = if (autoStarted) tr("Teraz: {0}", Fmt.phase(next)) else tr("Następnie: {0} · dotknij, aby zacząć", Fmt.phase(next))
        val b = Notification.Builder(context, CH_PHASE)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "pomodoro", 8))
        runCatching { nm(context).notify(ID_POMODORO + 10, b.build()) }
    }
}
