package com.blacksingularities.opentimer.data

import com.blacksingularities.opentimer.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

/** Zadanie do wykonania przed wyłączeniem budzika. */
enum class Challenge { NONE, MATH_EASY, MATH_HARD, TYPE_CODE }

/**
 * Budzik. [days] – dni tygodnia jako bity (poniedziałek = bit 0 … niedziela = bit 6); 0 = jednorazowy.
 * [skipUntil] – pominięcie najbliższego dzwonienia (epoch ms), [snoozedUntil] – drzemka w toku.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val sound: String = "",
    val vibrate: Boolean = true,
    val rampSeconds: Int = 30,
    val volume: Int = 100,
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int = 0,
    val challenge: Challenge = Challenge.NONE,
    val challengeCount: Int = 3,
    val skipUntil: Long = 0L,
    val snoozedUntil: Long = 0L,
    val snoozeCount: Int = 0,
    val oneShotDate: Long = 0L,
    val deleteAfter: Boolean = false,
) {
    fun repeatsOn(isoDay: Int) = days and (1 shl (isoDay - 1)) != 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute).put("days", days)
        .put("enabled", enabled).put("label", label).put("sound", sound).put("vibrate", vibrate)
        .put("ramp", rampSeconds).put("volume", volume).put("snooze", snoozeMinutes)
        .put("maxSnoozes", maxSnoozes).put("challenge", challenge.name).put("challengeCount", challengeCount)
        .put("skipUntil", skipUntil).put("snoozedUntil", snoozedUntil).put("snoozeCount", snoozeCount)
        .put("oneShotDate", oneShotDate).put("deleteAfter", deleteAfter)

    companion object {
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        const val EVERY_DAY = 0b1111111

        fun fromJson(o: JSONObject) = Alarm(
            id = o.optInt("id"),
            hour = o.optInt("hour"),
            minute = o.optInt("minute"),
            days = o.optInt("days"),
            enabled = o.optBoolean("enabled", true),
            label = o.optString("label"),
            sound = o.optString("sound"),
            vibrate = o.optBoolean("vibrate", true),
            rampSeconds = o.optInt("ramp", 30),
            volume = o.optInt("volume", 100),
            snoozeMinutes = o.optInt("snooze", 10),
            maxSnoozes = o.optInt("maxSnoozes", 0),
            challenge = enumOr(o.optString("challenge"), Challenge.NONE),
            challengeCount = o.optInt("challengeCount", 3),
            skipUntil = o.optLong("skipUntil"),
            snoozedUntil = o.optLong("snoozedUntil"),
            snoozeCount = o.optInt("snoozeCount"),
            oneShotDate = o.optLong("oneShotDate"),
            deleteAfter = o.optBoolean("deleteAfter"),
        )
    }
}

/**
 * Minutnik. Gdy działa, liczy się [endAt] (epoch ms); po zatrzymaniu – [remainingMs].
 * [ringing] – czas minął i minutnik dzwoni (albo dzwonił) i czeka na wyłączenie.
 */
data class TimerItem(
    val id: Int,
    val label: String = "",
    val durationMs: Long,
    val remainingMs: Long = durationMs,
    val endAt: Long = 0L,
    val running: Boolean = false,
    val ringing: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun remainingAt(now: Long): Long = if (running) endAt - now else remainingMs
    fun progressAt(now: Long): Float =
        if (durationMs <= 0) 0f else (1f - remainingAt(now).coerceAtLeast(0).toFloat() / durationMs).coerceIn(0f, 1f)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("duration", durationMs).put("remaining", remainingMs)
        .put("endAt", endAt).put("running", running).put("ringing", ringing).put("created", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = TimerItem(
            id = o.optInt("id"),
            label = o.optString("label"),
            durationMs = o.optLong("duration"),
            remainingMs = o.optLong("remaining"),
            endAt = o.optLong("endAt"),
            running = o.optBoolean("running"),
            ringing = o.optBoolean("ringing"),
            createdAt = o.optLong("created"),
        )
    }
}

/** Zapisany szybki minutnik. */
data class TimerPreset(val label: String, val durationMs: Long) {
    fun toJson(): JSONObject = JSONObject().put("label", label).put("duration", durationMs)

    companion object {
        fun fromJson(o: JSONObject) = TimerPreset(o.optString("label"), o.optLong("duration"))
    }
}

/**
 * Stoper. Czas = [accumulatedMs] + (teraz − [startedAt]) gdy działa. Okrążenia jako czasy łączne
 * w chwili ich zapisania (rosnąco), więc stan przeżywa zamknięcie aplikacji.
 */
data class Stopwatch(
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val accumulatedMs: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    fun elapsedAt(now: Long): Long = accumulatedMs + if (running) (now - startedAt).coerceAtLeast(0) else 0
    val isReset: Boolean get() = !running && accumulatedMs == 0L && laps.isEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("running", running).put("startedAt", startedAt).put("accumulated", accumulatedMs)
        .put("laps", JSONArray(laps))

    companion object {
        fun fromJson(o: JSONObject) = Stopwatch(
            running = o.optBoolean("running"),
            startedAt = o.optLong("startedAt"),
            accumulatedMs = o.optLong("accumulated"),
            laps = o.optJSONArray("laps").longs(),
        )
    }
}

/** Wydarzenie do odliczania (albo liczenia czasu od niego). */
data class CountdownEvent(
    val id: Int,
    val title: String,
    val at: Long,
    val yearly: Boolean = false,
    val emoji: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("at", at)
        .put("yearly", yearly).put("emoji", emoji)

    companion object {
        fun fromJson(o: JSONObject) = CountdownEvent(
            o.optInt("id"), o.optString("title"), o.optLong("at"), o.optBoolean("yearly"), o.optString("emoji"),
        )
    }
}

/** Trening interwałowy: przygotowanie, praca/odpoczynek × rundy, serie z przerwą. */
data class IntervalPlan(
    val name: String = "",
    val prepareSec: Int = 10,
    val workSec: Int = 20,
    val restSec: Int = 10,
    val rounds: Int = 8,
    val sets: Int = 1,
    val setRestSec: Int = 60,
    val cooldownSec: Int = 0,
) {
    val totalSec: Int
        get() = prepareSec + sets * (rounds * workSec + (rounds - 1).coerceAtLeast(0) * restSec) +
            (sets - 1).coerceAtLeast(0) * setRestSec + cooldownSec

    fun toJson(): JSONObject = JSONObject().put("name", name).put("prepare", prepareSec).put("work", workSec)
        .put("rest", restSec).put("rounds", rounds).put("sets", sets).put("setRest", setRestSec)
        .put("cooldown", cooldownSec)

    companion object {
        fun fromJson(o: JSONObject) = IntervalPlan(
            o.optString("name"), o.optInt("prepare", 10), o.optInt("work", 20), o.optInt("rest", 10),
            o.optInt("rounds", 8), o.optInt("sets", 1), o.optInt("setRest", 60), o.optInt("cooldown", 0),
        )
    }
}

enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

/** Pomodoro: bieżąca faza i licznik ukończonych sesji skupienia (dziś i łącznie). */
data class Pomodoro(
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
    val longEvery: Int = 4,
    val autoStart: Boolean = false,
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    val endAt: Long = 0L,
    val remainingMs: Long = 25 * 60_000L,
    val cycle: Int = 0,
    val doneToday: Int = 0,
    val doneDay: Long = 0L,
    val doneTotal: Int = 0,
    val focusTotalMin: Long = 0L,
) {
    fun minutesOf(p: PomodoroPhase) = when (p) {
        PomodoroPhase.FOCUS -> focusMin
        PomodoroPhase.SHORT_BREAK -> shortMin
        PomodoroPhase.LONG_BREAK -> longMin
    }

    fun remainingAt(now: Long): Long = if (running) endAt - now else remainingMs

    fun toJson(): JSONObject = JSONObject().put("focus", focusMin).put("short", shortMin).put("long", longMin)
        .put("longEvery", longEvery).put("auto", autoStart).put("phase", phase.name).put("running", running)
        .put("endAt", endAt).put("remaining", remainingMs).put("cycle", cycle).put("doneToday", doneToday)
        .put("doneDay", doneDay).put("doneTotal", doneTotal).put("focusTotal", focusTotalMin)

    companion object {
        fun fromJson(o: JSONObject) = Pomodoro(
            focusMin = o.optInt("focus", 25),
            shortMin = o.optInt("short", 5),
            longMin = o.optInt("long", 15),
            longEvery = o.optInt("longEvery", 4),
            autoStart = o.optBoolean("auto"),
            phase = enumOr(o.optString("phase"), PomodoroPhase.FOCUS),
            running = o.optBoolean("running"),
            endAt = o.optLong("endAt"),
            remainingMs = o.optLong("remaining", 25 * 60_000L),
            cycle = o.optInt("cycle"),
            doneToday = o.optInt("doneToday"),
            doneDay = o.optLong("doneDay"),
            doneTotal = o.optInt("doneTotal"),
            focusTotalMin = o.optLong("focusTotal"),
        )
    }
}

enum class ClockStyle { DIGITAL, ANALOG, LCD }
enum class StopwatchStyle { RING, ANALOG, LCD }
enum class HourFormat { SYSTEM, H24, H12 }
enum class VolumeKeys { NOTHING, SNOOZE, DISMISS }
enum class StartTab { CLOCK, ALARMS, STOPWATCH, TIMERS, MORE, LAST }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val startTab: StartTab = StartTab.LAST,
    val lastTab: Int = 0,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val showSeconds: Boolean = true,
    val hourFormat: HourFormat = HourFormat.SYSTEM,
    val homeClock: Boolean = true,
    val homeZone: String = "",
    val weekStartsMonday: Boolean = true,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val defaultSnooze: Int = 10,
    val defaultRamp: Int = 30,
    val autoSilenceMin: Int = 10,
    val volumeKeys: VolumeKeys = VolumeKeys.SNOOZE,
    val flipToSnooze: Boolean = false,
    val preAlarmMin: Int = 60,
    val timerSound: String = "",
    val timerVibrate: Boolean = true,
    val timerRamp: Int = 0,
    val timerFullScreen: Boolean = true,
    val stopwatchMs: Boolean = true,
    val stopwatchStyle: StopwatchStyle = StopwatchStyle.RING,
    val intervalVoice: Boolean = true,
    val intervalBeeps: Boolean = true,
    val nightColor: Int = 0xFFFF453A.toInt(),
    val nightDim: Float = 0.5f,
    val nightSeconds: Boolean = false,
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val onboarded: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("language", language.name).put("startTab", startTab.name).put("lastTab", lastTab)
        .put("clockStyle", clockStyle.name).put("showSeconds", showSeconds).put("hourFormat", hourFormat.name)
        .put("homeClock", homeClock).put("homeZone", homeZone).put("weekMonday", weekStartsMonday).put("haptics", haptics)
        .put("keepScreenOn", keepScreenOn).put("defaultSnooze", defaultSnooze).put("defaultRamp", defaultRamp)
        .put("autoSilence", autoSilenceMin).put("volumeKeys", volumeKeys.name).put("flip", flipToSnooze)
        .put("preAlarm", preAlarmMin).put("timerSound", timerSound).put("timerVibrate", timerVibrate)
        .put("timerRamp", timerRamp).put("timerFullScreen", timerFullScreen).put("stopwatchMs", stopwatchMs)
        .put("stopwatchStyle", stopwatchStyle.name)
        .put("intervalVoice", intervalVoice).put("intervalBeeps", intervalBeeps).put("nightColor", nightColor)
        .put("nightDim", nightDim.toDouble()).put("nightSeconds", nightSeconds).put("autoUpdate", autoUpdate)
        .put("lastUpdateCheck", lastUpdateCheck).put("onboarded", onboarded)

    companion object {
        fun fromJson(o: JSONObject) = AppSettings(
            language = enumOr(o.optString("language"), AppLanguage.SYSTEM),
            startTab = enumOr(o.optString("startTab"), StartTab.LAST),
            lastTab = o.optInt("lastTab"),
            clockStyle = enumOr(o.optString("clockStyle"), ClockStyle.DIGITAL),
            showSeconds = o.optBoolean("showSeconds", true),
            hourFormat = enumOr(o.optString("hourFormat"), HourFormat.SYSTEM),
            homeClock = o.optBoolean("homeClock", true),
            homeZone = o.optString("homeZone"),
            weekStartsMonday = o.optBoolean("weekMonday", true),
            haptics = o.optBoolean("haptics", true),
            keepScreenOn = o.optBoolean("keepScreenOn"),
            defaultSnooze = o.optInt("defaultSnooze", 10),
            defaultRamp = o.optInt("defaultRamp", 30),
            autoSilenceMin = o.optInt("autoSilence", 10),
            volumeKeys = enumOr(o.optString("volumeKeys"), VolumeKeys.SNOOZE),
            flipToSnooze = o.optBoolean("flip"),
            preAlarmMin = o.optInt("preAlarm", 60),
            timerSound = o.optString("timerSound"),
            timerVibrate = o.optBoolean("timerVibrate", true),
            timerRamp = o.optInt("timerRamp", 0),
            timerFullScreen = o.optBoolean("timerFullScreen", true),
            stopwatchMs = o.optBoolean("stopwatchMs", true),
            stopwatchStyle = enumOr(o.optString("stopwatchStyle"), StopwatchStyle.RING),
            intervalVoice = o.optBoolean("intervalVoice", true),
            intervalBeeps = o.optBoolean("intervalBeeps", true),
            nightColor = o.optInt("nightColor", 0xFFFF453A.toInt()),
            nightDim = o.optDouble("nightDim", 0.5).toFloat(),
            nightSeconds = o.optBoolean("nightSeconds"),
            autoUpdate = o.optBoolean("autoUpdate", true),
            lastUpdateCheck = o.optLong("lastUpdateCheck"),
            onboarded = o.optBoolean("onboarded"),
        )
    }
}

inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

fun JSONArray?.longs(): List<Long> = if (this == null) emptyList() else List(length()) { optLong(it) }

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }

inline fun <T> JSONArray?.objects(map: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(map) }
