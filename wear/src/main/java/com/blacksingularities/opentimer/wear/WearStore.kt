package com.blacksingularities.opentimer.wear

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Stan minutnika i stopera na zegarku, zapisany w SharedPreferences (przeżywa zamknięcie). */
object WearStore {
    const val CHANNEL = "timer"
    const val NOTIF_ID = 1

    var timerDuration by mutableStateOf(0L)
    var timerEnd by mutableStateOf(0L)
    var timerRemaining by mutableStateOf(0L)
    var timerRunning by mutableStateOf(false)
    var ringing by mutableStateOf(false)

    var swRunning by mutableStateOf(false)
    var swStarted by mutableStateOf(0L)
    var swAccumulated by mutableStateOf(0L)

    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("wear", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        timerDuration = p.getLong("td", 0)
        timerEnd = p.getLong("te", 0)
        timerRemaining = p.getLong("tr", 0)
        timerRunning = p.getBoolean("trun", false)
        ringing = p.getBoolean("ring", false)
        swRunning = p.getBoolean("swr", false)
        swStarted = p.getLong("sws", 0)
        swAccumulated = p.getLong("swa", 0)
        if (timerRunning && timerEnd <= System.currentTimeMillis()) {
            timerRunning = false
            timerRemaining = 0
        }
    }

    private fun save(c: Context) {
        prefs(c).edit()
            .putLong("td", timerDuration).putLong("te", timerEnd).putLong("tr", timerRemaining)
            .putBoolean("trun", timerRunning).putBoolean("ring", ringing)
            .putBoolean("swr", swRunning).putLong("sws", swStarted).putLong("swa", swAccumulated)
            .apply()
    }

    fun timerLeft(now: Long): Long = if (timerRunning) (timerEnd - now).coerceAtLeast(0) else timerRemaining
    val timerActive: Boolean get() = timerRunning || timerRemaining > 0
    fun swElapsed(now: Long): Long = swAccumulated + if (swRunning) (now - swStarted).coerceAtLeast(0) else 0

    // --- minutnik ---

    fun startTimer(c: Context, ms: Long) {
        timerDuration = ms
        timerRemaining = ms
        resumeTimer(c)
    }

    fun resumeTimer(c: Context) {
        if (timerRemaining <= 0) return
        timerEnd = System.currentTimeMillis() + timerRemaining
        timerRunning = true
        schedule(c)
        save(c)
    }

    fun pauseTimer(c: Context) {
        if (!timerRunning) return
        timerRemaining = timerLeft(System.currentTimeMillis())
        timerRunning = false
        unschedule(c)
        save(c)
    }

    fun addMinute(c: Context) {
        if (timerRunning) {
            timerEnd += 60_000
            schedule(c)
        } else {
            timerRemaining += 60_000
        }
        timerDuration += 60_000
        save(c)
    }

    fun cancelTimer(c: Context) {
        timerRunning = false
        timerRemaining = 0
        timerDuration = 0
        unschedule(c)
        save(c)
    }

    /** Wywoływane przez alarm systemu po upływie czasu. */
    fun finish(c: Context) {
        timerRunning = false
        timerRemaining = 0
        ringing = true
        save(c)
        vibrator(c).vibrate(VibrationEffect.createWaveform(LongArray(40) { if (it % 2 == 0) 400L else 600L }, -1))
        notifyRinging(c)
    }

    fun stopRinging(c: Context) {
        ringing = false
        save(c)
        vibrator(c).cancel()
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    // --- stoper ---

    fun swToggle(c: Context) {
        val now = System.currentTimeMillis()
        if (swRunning) {
            swAccumulated = swElapsed(now)
            swRunning = false
        } else {
            swStarted = now
            swRunning = true
        }
        save(c)
    }

    fun swReset(c: Context) {
        swRunning = false
        swAccumulated = 0
        swStarted = 0
        save(c)
    }

    // --- system ---

    private fun vibrator(c: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            c.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            c.getSystemService(Vibrator::class.java)
        }

    private fun firePi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 1, Intent(c, TimerReceiver::class.java).setAction(TimerReceiver.FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openPi(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 2, Intent(c, WearActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timerEnd, firePi(c))
        } else {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(timerEnd, openPi(c)), firePi(c))
        }
    }

    private fun unschedule(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(firePi(c))
    }

    fun createChannel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, c.getString(R.string.channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun notifyRinging(c: Context) {
        createChannel(c)
        val stop = PendingIntent.getBroadcast(
            c, 3, Intent(c, TimerReceiver::class.java).setAction(TimerReceiver.STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(c.getString(R.string.time_up))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openPi(c))
            .setFullScreenIntent(openPi(c), true)
            .setDeleteIntent(stop)
            .addAction(Notification.Action.Builder(null, c.getString(R.string.stop), stop).build())
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) }
    }
}
