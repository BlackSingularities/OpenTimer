package com.blacksingularities.opentimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.blacksingularities.opentimer.MainActivity
import com.blacksingularities.opentimer.R
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.ui.Fmt

/** Widżet: godzina i data (TextClock odświeża się sam) oraz najbliższy budzik. */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Store.init(context.applicationContext)
            I18n.apply(Store.settings.value.language)
            val next = TimeMath.nextOf(Store.alarms.value, System.currentTimeMillis())
            val open = PendingIntent.getActivity(
                context, 90, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "alarms"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val v = RemoteViews(context.packageName, R.layout.widget_clock)
                if (next != null) {
                    v.setViewVisibility(R.id.widget_alarm, View.VISIBLE)
                    v.setTextViewText(R.id.widget_alarm, "⏰ " + Fmt.dayAndTime(context, next.second))
                } else {
                    v.setViewVisibility(R.id.widget_alarm, View.GONE)
                }
                v.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}
