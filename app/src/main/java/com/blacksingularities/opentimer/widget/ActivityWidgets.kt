package com.blacksingularities.opentimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.blacksingularities.opentimer.MainActivity
import com.blacksingularities.opentimer.R
import com.blacksingularities.opentimer.alarm.ActionReceiver
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.ui.Fmt

private fun openPi(context: Context, open: String, code: Int): PendingIntent = PendingIntent.getActivity(
    context, code, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, open),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun ids(context: Context, cls: Class<*>): Pair<AppWidgetManager, IntArray>? {
    val manager = AppWidgetManager.getInstance(context) ?: return null
    val ids = manager.getAppWidgetIds(ComponentName(context, cls))
    return if (ids.isEmpty()) null else manager to ids
}

/**
 * Widżet odliczania: najbliższy minutnik albo stoper. Czas liczy systemowy Chronometer,
 * więc widżet nie musi się odświeżać co sekundę; przycisk pauzuje i wznawia.
 */
class CountdownWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    companion object {
        fun refresh(context: Context) {
            val (m, i) = ids(context, CountdownWidget::class.java) ?: return
            render(context, m, i)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Store.init(context.applicationContext)
            I18n.apply(Store.settings.value.language)
            val now = System.currentTimeMillis()
            val elapsedNow = SystemClock.elapsedRealtime()
            val timer = Store.timers.value
                .filter { !it.ringing && (it.running || it.remainingMs != it.durationMs) }
                .minByOrNull { if (it.running) it.remainingAt(now) else Long.MAX_VALUE / 2 + it.remainingMs }
            val sw = Store.stopwatch.value

            val v = RemoteViews(context.packageName, R.layout.widget_countdown)
            when {
                timer != null -> {
                    val left = timer.remainingAt(now).coerceAtLeast(0)
                    v.setTextViewText(R.id.wc_title, timer.label.ifBlank { tr("Minutnik {0}", Fmt.durationShort(timer.durationMs)) })
                    v.setViewVisibility(R.id.wc_chrono, View.VISIBLE)
                    v.setViewVisibility(R.id.wc_empty, View.GONE)
                    v.setChronometer(R.id.wc_chrono, elapsedNow + left, null, timer.running)
                    v.setChronometerCountDown(R.id.wc_chrono, true)
                    v.setTextViewText(
                        R.id.wc_sub,
                        if (timer.running) tr("Zadzwoni o {0}", Fmt.time(context, timer.endAt)) else tr("Wstrzymany"),
                    )
                    v.setViewVisibility(R.id.wc_toggle, View.VISIBLE)
                    v.setTextViewText(R.id.wc_toggle, if (timer.running) tr("Pauza") else tr("Wznów"))
                    v.setContentDescription(R.id.wc_toggle, if (timer.running) tr("Pauza") else tr("Wznów"))
                    v.setOnClickPendingIntent(
                        R.id.wc_toggle,
                        Notifications.action(
                            context, if (timer.running) ActionReceiver.TIMER_PAUSE else ActionReceiver.TIMER_RESUME,
                            timer.id, 500_000 + timer.id,
                        ),
                    )
                    v.setOnClickPendingIntent(R.id.wc_root, openPi(context, "timers", 91))
                }
                !sw.isReset -> {
                    val elapsed = sw.elapsedAt(now)
                    v.setTextViewText(R.id.wc_title, tr("Stoper"))
                    v.setViewVisibility(R.id.wc_chrono, View.VISIBLE)
                    v.setViewVisibility(R.id.wc_empty, View.GONE)
                    v.setChronometer(R.id.wc_chrono, elapsedNow - elapsed, null, sw.running)
                    v.setChronometerCountDown(R.id.wc_chrono, false)
                    v.setTextViewText(
                        R.id.wc_sub,
                        if (sw.running) {
                            if (sw.laps.isEmpty()) tr("Odmierzanie") else tr("Okrążenie {0}", sw.laps.size + 1)
                        } else {
                            tr("Wstrzymany · {0}", TimeMath.clock(elapsed, centis = false))
                        },
                    )
                    v.setViewVisibility(R.id.wc_toggle, View.VISIBLE)
                    v.setTextViewText(R.id.wc_toggle, if (sw.running) tr("Pauza") else tr("Wznów"))
                    v.setContentDescription(R.id.wc_toggle, if (sw.running) tr("Pauza") else tr("Wznów"))
                    v.setOnClickPendingIntent(
                        R.id.wc_toggle,
                        Notifications.action(context, if (sw.running) ActionReceiver.SW_PAUSE else ActionReceiver.SW_RESUME, 0, 500_900),
                    )
                    v.setOnClickPendingIntent(R.id.wc_root, openPi(context, "stopwatch", 92))
                }
                else -> {
                    v.setTextViewText(R.id.wc_title, tr("Minutnik"))
                    v.setViewVisibility(R.id.wc_chrono, View.GONE)
                    v.setViewVisibility(R.id.wc_empty, View.VISIBLE)
                    v.setTextViewText(R.id.wc_empty, tr("Brak odliczania"))
                    v.setTextViewText(R.id.wc_sub, tr("Dotknij, aby ustawić minutnik"))
                    v.setViewVisibility(R.id.wc_toggle, View.GONE)
                    v.setOnClickPendingIntent(R.id.wc_root, openPi(context, "timers", 91))
                }
            }
            for (id in ids) manager.updateAppWidget(id, v)
        }
    }
}

/** Widżet listy budzików: do czterech najbliższych włączonych budzików z dniem i godziną. */
class AlarmsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) refresh(context)
    }

    companion object {
        private val ROWS = intArrayOf(R.id.wa_row1, R.id.wa_row2, R.id.wa_row3, R.id.wa_row4)

        fun refresh(context: Context) {
            val (m, i) = ids(context, AlarmsWidget::class.java) ?: return
            render(context, m, i)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Store.init(context.applicationContext)
            I18n.apply(Store.settings.value.language)
            val now = System.currentTimeMillis()
            val upcoming = Store.alarms.value
                .mapNotNull { a -> TimeMath.nextTrigger(a, now)?.let { a to it } }
                .sortedBy { it.second }
                .take(ROWS.size)
            val v = RemoteViews(context.packageName, R.layout.widget_alarms)
            v.setTextViewText(R.id.wa_title, tr("Budziki"))
            v.setViewVisibility(R.id.wa_empty, if (upcoming.isEmpty()) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.wa_empty, tr("Brak włączonych budzików"))
            ROWS.forEachIndexed { i, row ->
                val item = upcoming.getOrNull(i)
                if (item == null) {
                    v.setViewVisibility(row, View.GONE)
                } else {
                    val (a, at) = item
                    val text = listOf(Fmt.dayAndTime(context, at), a.label).filter { it.isNotBlank() }.joinToString(" · ")
                    v.setViewVisibility(row, View.VISIBLE)
                    v.setTextViewText(row, text)
                }
            }
            v.setOnClickPendingIntent(R.id.wa_root, openPi(context, "alarms", 93))
            for (id in ids) manager.updateAppWidget(id, v)
        }
    }
}
