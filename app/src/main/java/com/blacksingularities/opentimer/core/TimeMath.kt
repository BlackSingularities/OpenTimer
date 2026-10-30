package com.blacksingularities.opentimer.core

import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.CountdownEvent
import com.blacksingularities.opentimer.data.IntervalPlan
import com.blacksingularities.opentimer.data.Pomodoro
import com.blacksingularities.opentimer.data.PomodoroPhase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** Obliczenia czasu bez Androida – testowane jednostkowo. */
object TimeMath {
    /**
     * Najbliższe dzwonienie budzika po chwili [now] (epoch ms) albo null, gdy wyłączony.
     * Uwzględnia drzemkę, pominięcie kolejnego dzwonienia i zmiany czasu (strefa [zone]).
     */
    fun nextTrigger(alarm: Alarm, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!alarm.enabled) return null
        if (alarm.snoozedUntil > now) return alarm.snoozedUntil
        var from = now
        // Pominięte dzwonienie: szukamy kolejnego terminu po nim.
        if (alarm.skipUntil > now) from = alarm.skipUntil
        return firstAfter(alarm, from, zone)
    }

    /** Pierwszy termin budzika ściśle po [from], bez drzemki i pominięć. */
    fun firstAfter(alarm: Alarm, from: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(from), zone)
        val time = LocalTime.of(alarm.hour, alarm.minute)
        if (alarm.days == 0 && alarm.oneShotDate > 0) {
            val date = LocalDate.ofEpochDay(alarm.oneShotDate)
            val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            return if (at > from) at else null
        }
        for (offset in 0..7) {
            val date = start.toLocalDate().plusDays(offset.toLong())
            if (alarm.days != 0 && !alarm.repeatsOn(date.dayOfWeek.value)) continue
            // atZone przesuwa nieistniejącą godzinę (zmiana na czas letni) do przodu.
            val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (at > from) return at
        }
        return null
    }

    /** Najbliższy z wielu budzików: para (budzik, termin). */
    fun nextOf(alarms: List<Alarm>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Alarm, Long>? =
        alarms.mapNotNull { a -> nextTrigger(a, now, zone)?.let { a to it } }.minByOrNull { it.second }

    /** Rozbicie czasu na dni, godziny, minuty i sekundy (wartość bezwzględna). */
    data class Parts(val days: Long, val hours: Long, val minutes: Long, val seconds: Long, val millis: Long)

    fun parts(ms: Long): Parts {
        val a = abs(ms)
        return Parts(a / 86_400_000, a / 3_600_000 % 24, a / 60_000 % 60, a / 1000 % 60, a % 1000)
    }

    /** „1:05:09”, „05:09”; z [centis] dochodzi „,42”. */
    fun clock(ms: Long, centis: Boolean = false, forceHours: Boolean = false): String {
        val neg = ms < 0
        val a = abs(ms)
        val h = a / 3_600_000
        val m = a / 60_000 % 60
        val s = a / 1000 % 60
        val base = if (h > 0 || forceHours) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        val tail = if (centis) ",%02d".format(a / 10 % 100) else ""
        return (if (neg) "−" else "") + base + tail
    }

    /** Odliczanie minutnika zaokrąglone w górę, żeby „00:00” pokazało się dopiero na końcu. */
    fun countdown(ms: Long): String {
        if (ms <= 0) return clock(ms)
        return clock(((ms + 999) / 1000) * 1000)
    }

    /** Cyfry wpisane na klawiaturze minutnika („130” = 1 min 30 s; „13000” = 1 h 30 min). */
    fun digitsToMs(digits: String): Long {
        val d = digits.takeLast(6).padStart(6, '0')
        val h = d.substring(0, 2).toLong()
        val m = d.substring(2, 4).toLong()
        val s = d.substring(4, 6).toLong()
        return ((h * 60 + m) * 60 + s) * 1000
    }

    /** Termin wydarzenia: dla corocznych najbliższa rocznica (dziś lub później). */
    fun eventTarget(event: CountdownEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!event.yearly) return event.at
        val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.at), zone)
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        var year = today.year
        while (true) {
            val day = at.dayOfMonth.coerceAtMost(LocalDate.of(year, at.month, 1).lengthOfMonth())
            val candidate = LocalDate.of(year, at.month, day)
            if (!candidate.isBefore(today)) return candidate.atTime(at.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
            year++
        }
    }

    /** Która to rocznica wydarzenia corocznego w najbliższym terminie. */
    fun anniversary(event: CountdownEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val origin = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.at), zone).year
        val target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(eventTarget(event, now, zone)), zone).year
        return target - origin
    }

    /** Różnica stref w minutach: ile [zone] jest przed strefą lokalną. */
    fun zoneOffsetMinutes(zone: ZoneId, now: Long, local: ZoneId = ZoneId.systemDefault()): Int {
        val i = Instant.ofEpochMilli(now)
        return (zone.rules.getOffset(i).totalSeconds - local.rules.getOffset(i).totalSeconds) / 60
    }

    /** Dzień w innej strefie względem lokalnego: −1 wczoraj, 0 dziś, 1 jutro. */
    fun dayShift(zone: ZoneId, now: Long, local: ZoneId = ZoneId.systemDefault()): Int {
        val i = Instant.ofEpochMilli(now)
        val there = LocalDate.ofInstant(i, zone)
        val here = LocalDate.ofInstant(i, local)
        return there.compareTo(here).coerceIn(-1, 1)
    }

    /** Kolejna faza pomodoro po zakończeniu bieżącej. */
    fun nextPomodoro(p: Pomodoro): Pomodoro {
        return if (p.phase == PomodoroPhase.FOCUS) {
            val cycle = p.cycle + 1
            val phase = if (p.longEvery > 0 && cycle % p.longEvery == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            p.copy(phase = phase, cycle = cycle, remainingMs = p.minutesOf(phase) * 60_000L, running = false, endAt = 0)
        } else {
            p.copy(phase = PomodoroPhase.FOCUS, remainingMs = p.focusMin * 60_000L, running = false, endAt = 0)
        }
    }
}

/** Jeden odcinek treningu interwałowego. */
data class IntervalStep(val kind: Kind, val seconds: Int, val round: Int, val set: Int) {
    enum class Kind { PREPARE, WORK, REST, SET_REST, COOLDOWN }
}

/** Plan rozpisany na kolejne odcinki (zerowe są pomijane). */
fun IntervalPlan.steps(): List<IntervalStep> {
    val out = mutableListOf<IntervalStep>()
    if (prepareSec > 0) out += IntervalStep(IntervalStep.Kind.PREPARE, prepareSec, 0, 0)
    for (set in 1..sets.coerceAtLeast(1)) {
        for (round in 1..rounds.coerceAtLeast(1)) {
            if (workSec > 0) out += IntervalStep(IntervalStep.Kind.WORK, workSec, round, set)
            if (restSec > 0 && round < rounds) out += IntervalStep(IntervalStep.Kind.REST, restSec, round, set)
        }
        if (set < sets && setRestSec > 0) out += IntervalStep(IntervalStep.Kind.SET_REST, setRestSec, rounds, set)
    }
    if (cooldownSec > 0) out += IntervalStep(IntervalStep.Kind.COOLDOWN, cooldownSec, rounds, sets)
    return out
}
