package com.blacksingularities.opentimer.ring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.blacksingularities.opentimer.R
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.data.TimerItem
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Co teraz dzwoni: budzik albo zakończone minutniki. */
data class RingState(val alarm: Alarm?, val timers: List<TimerItem>, val startedAt: Long)

/**
 * Usługa pierwszoplanowa odtwarzająca dźwięk budzika lub minutnika: narastająca głośność,
 * wibracja, ekran pełnoekranowy na blokadzie, automatyczne wyciszenie, odwrócenie = drzemka.
 */
class RingService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ramp: Job? = null
    private var silence: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var faceDownSince = 0L
    private var sensors: SensorManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(applicationContext)
        I18n.apply(Store.settings.value.language)
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ALARM -> {
                val alarm = alarmFromIntent(intent)
                val current = _state.value?.alarm
                // Nowy budzik w trakcie dzwonienia poprzedniego – poprzedni idzie na drzemkę.
                if (current != null && alarm != null && current.id != alarm.id) Actions.snooze(this, current)
                _state.value = RingState(alarm, _state.value?.timers.orEmpty(), System.currentTimeMillis())
            }
            ACTION_TIMERS -> {
                val ringing = Store.timers.value.filter { it.ringing }
                _state.value = RingState(_state.value?.alarm, ringing, _state.value?.startedAt ?: System.currentTimeMillis())
            }
            ACTION_SNOOZE -> { snooze(); return START_NOT_STICKY }
            ACTION_DISMISS -> { dismiss(); return START_NOT_STICKY }
            ACTION_TIMER_ADD -> { addMinute(); return START_NOT_STICKY }
            else -> if (_state.value == null) { stopSelf(); return START_NOT_STICKY }
        }
        val st = _state.value
        if (st == null || (st.alarm == null && st.timers.isEmpty())) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground(st)
        play(st)
        return START_NOT_STICKY
    }

    private fun alarmFromIntent(intent: Intent): Alarm? =
        intent.getStringExtra(EXTRA_ALARM)?.let { runCatching { Alarm.fromJson(org.json.JSONObject(it)) }.getOrNull() }

    private fun goForeground(st: RingState) {
        val full = PendingIntent.getActivity(
            this, 0, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarm = st.alarm
        val title = if (alarm != null) alarm.label.ifBlank { tr("Budzik") } else tr("Czas minął")
        val text = if (alarm != null) Fmt.alarmTime(this, alarm)
        else st.timers.joinToString(", ") { it.label.ifBlank { Fmt.durationShort(it.durationMs) } }
        val b = Notification.Builder(this, Notifications.CH_RING)
            .setSmallIcon(if (alarm != null) R.drawable.ic_stat_alarm else R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (alarm != null) {
            b.addAction(action(tr("Drzemka"), ACTION_SNOOZE, 1))
            if (alarm.challenge == com.blacksingularities.opentimer.data.Challenge.NONE) b.addAction(action(tr("Wyłącz"), ACTION_DISMISS, 2))
        } else {
            b.addAction(action(tr("+1 min"), ACTION_TIMER_ADD, 3))
            b.addAction(action(tr("Zatrzymaj"), ACTION_DISMISS, 2))
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifications.ID_RING, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifications.ID_RING, b.build())
        }
        // Gdy ekran jest włączony, system pokazuje tylko dymek – próbujemy otworzyć ekran dzwonienia.
        runCatching { startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun action(title: String, action: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(this, code, Intent(this, RingService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_alarm), title, pi).build()
    }

    private fun play(st: RingState) {
        stopSound()
        val settings = Store.settings.value
        val alarm = st.alarm
        val soundSetting = alarm?.sound ?: settings.timerSound
        val vibrate = alarm?.vibrate ?: settings.timerVibrate
        val rampSec = alarm?.rampSeconds ?: settings.timerRamp
        val target = ((alarm?.volume ?: 100) / 100f).coerceIn(0.05f, 1f)

        if (wake == null) {
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTimer:ring").apply {
                setReferenceCounted(false)
                acquire(30 * 60_000L)
            }
        }

        if (soundSetting != SILENT) {
            val uri = soundSetting.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = createPlayer(uri) ?: createPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            player?.let { p ->
                if (rampSec > 0) {
                    p.setVolume(0.05f * target, 0.05f * target)
                    ramp = scope.launch {
                        val steps = rampSec * 4
                        for (i in 1..steps) {
                            delay(250)
                            val v = (0.05f + 0.95f * i / steps) * target
                            runCatching { p.setVolume(v, v) }
                        }
                    }
                } else {
                    p.setVolume(target, target)
                }
                runCatching { p.start() }
            }
        }
        if (vibrate) vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1200), 0))

        silence?.cancel()
        val minutes = settings.autoSilenceMin
        if (minutes > 0) silence = scope.launch {
            delay(minutes * 60_000L)
            autoSilence()
        }

        if (alarm != null && settings.flipToSnooze) {
            sensors = getSystemService(SensorManager::class.java)
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    private fun createPlayer(uri: Uri?): MediaPlayer? {
        if (uri == null) return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                setDataSource(this@RingService, uri)
                isLooping = true
                prepare()
            }
        }.getOrNull()
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)

    private fun stopSound() {
        ramp?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        runCatching { vibrator().cancel() }
        sensors?.unregisterListener(this)
        sensors = null
    }

    private fun autoSilence() {
        val st = _state.value ?: return
        st.alarm?.let { a ->
            Notifications.missed(this, tr("Nieodebrany budzik"), tr("{0} – wyciszony po {1} min", Fmt.alarmTime(this, a), Store.settings.value.autoSilenceMin), a.id)
            Actions.dismissAlarm(this, a)
        }
        if (st.timers.isNotEmpty()) Actions.stopRingingTimers(this, st.timers.map { it.id })
        finish()
    }

    private fun snooze() {
        val a = _state.value?.alarm
        if (a != null) Actions.snooze(this, a)
        clearAlarm()
    }

    private fun dismiss() {
        val st = _state.value ?: return finish()
        if (st.alarm != null) {
            Actions.dismissAlarm(this, st.alarm)
            clearAlarm()
        } else {
            Actions.stopRingingTimers(this, st.timers.map { it.id })
            finish()
        }
    }

    private fun addMinute() {
        val st = _state.value ?: return
        st.timers.forEach { Actions.addToTimer(this, it.id, 60_000L) }
        _state.value = st.copy(timers = emptyList())
        if (st.alarm == null) finish() else play(_state.value!!)
    }

    /** Budzik obsłużony – jeśli czekają jeszcze minutniki, dzwonią dalej. */
    private fun clearAlarm() {
        val st = _state.value ?: return finish()
        if (st.timers.isEmpty()) return finish()
        _state.value = st.copy(alarm = null)
        goForeground(_state.value!!)
        play(_state.value!!)
    }

    private fun finish() {
        _state.value = null
        stopSound()
        silence?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values.getOrNull(2) ?: return
        val now = System.currentTimeMillis()
        if (z < -8f) {
            if (faceDownSince == 0L) faceDownSince = now
            else if (now - faceDownSince > 800) { faceDownSince = 0; snooze() }
        } else {
            faceDownSince = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        stopSound()
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val SILENT = "silent"
        private const val EXTRA_ALARM = "alarm"
        private const val ACTION_ALARM = "ring.alarm"
        private const val ACTION_TIMERS = "ring.timers"
        const val ACTION_SNOOZE = "ring.snooze"
        const val ACTION_DISMISS = "ring.dismiss"
        const val ACTION_TIMER_ADD = "ring.timerAdd"

        private val _state = MutableStateFlow<RingState?>(null)
        val state: StateFlow<RingState?> = _state.asStateFlow()

        fun ringAlarm(context: Context, alarm: Alarm) {
            context.startForegroundService(
                Intent(context, RingService::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_ALARM, alarm.toJson().toString()),
            )
        }

        fun ringTimers(context: Context) {
            context.startForegroundService(Intent(context, RingService::class.java).setAction(ACTION_TIMERS))
        }

        fun command(context: Context, action: String) {
            context.startService(Intent(context, RingService::class.java).setAction(action))
        }

        /** Podgląd dźwięku w edytorze budzika (bez usługi). */
        fun preview(context: Context, sound: String): MediaPlayer? {
            if (sound == SILENT) return null
            val uri = sound.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) ?: return null
            return runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                    setDataSource(context, uri)
                    prepare()
                    start()
                }
            }.getOrNull()
        }
    }
}
