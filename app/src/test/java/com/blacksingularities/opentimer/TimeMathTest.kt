package com.blacksingularities.opentimer

import com.blacksingularities.opentimer.core.IntervalStep
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.core.steps
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.CountdownEvent
import com.blacksingularities.opentimer.data.IntervalPlan
import com.blacksingularities.opentimer.data.Pomodoro
import com.blacksingularities.opentimer.data.PomodoroPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Wyliczenia czasu są czystym Kotlinem, więc da się je sprawdzić bez Androida. */
class TimeMathTest {
    private val zone = ZoneId.of("Europe/Warsaw")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `jednorazowy budzik dzwoni dzisiaj gdy godzina jeszcze nie minela`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        val next = TimeMath.nextTrigger(alarm, at("2026-03-10T06:00:00"), zone)
        assertEquals(at("2026-03-10T07:30:00"), next)
    }

    @Test
    fun `jednorazowy budzik po godzinie przechodzi na nastepny dzien`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        val next = TimeMath.nextTrigger(alarm, at("2026-03-10T08:00:00"), zone)
        assertEquals(at("2026-03-11T07:30:00"), next)
    }

    @Test
    fun `budzik na dni robocze w sobote dzwoni w poniedzialek`() {
        val alarm = Alarm(id = 1, hour = 6, minute = 0, days = Alarm.WEEKDAYS)
        // 14 marca 2026 to sobota.
        val next = TimeMath.nextTrigger(alarm, at("2026-03-14T09:00:00"), zone)
        assertEquals(at("2026-03-16T06:00:00"), next)
    }

    @Test
    fun `wylaczony budzik nie ma terminu`() {
        val alarm = Alarm(id = 1, hour = 6, minute = 0, enabled = false)
        assertNull(TimeMath.nextTrigger(alarm, at("2026-03-10T05:00:00"), zone))
    }

    @Test
    fun `drzemka ma pierwszenstwo przed zwykla godzina`() {
        val snoozed = Alarm(id = 1, hour = 7, minute = 0, snoozedUntil = at("2026-03-10T06:10:00"))
        assertEquals(at("2026-03-10T06:10:00"), TimeMath.nextTrigger(snoozed, at("2026-03-10T06:00:00"), zone))
    }

    @Test
    fun `pominiecie przesuwa dzwonienie na kolejny dzien`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY, skipUntil = at("2026-03-10T07:01:00"))
        assertEquals(at("2026-03-11T07:00:00"), TimeMath.nextTrigger(alarm, at("2026-03-10T06:00:00"), zone))
    }

    @Test
    fun `najblizszy budzik z listy`() {
        val early = Alarm(id = 1, hour = 6, minute = 0, days = Alarm.EVERY_DAY)
        val late = Alarm(id = 2, hour = 8, minute = 0, days = Alarm.EVERY_DAY)
        val next = TimeMath.nextOf(listOf(late, early), at("2026-03-10T05:00:00"), zone)
        assertEquals(1, next?.first?.id)
    }

    @Test
    fun `cyfry z klawiatury zamieniaja sie na czas`() {
        assertEquals(90_000L, TimeMath.digitsToMs("130"))
        assertEquals(5_400_000L, TimeMath.digitsToMs("13000"))
        assertEquals(0L, TimeMath.digitsToMs(""))
        assertEquals(59_000L, TimeMath.digitsToMs("59"))
    }

    @Test
    fun `formatowanie zegara i odliczania`() {
        assertEquals("01:05", TimeMath.clock(65_000))
        assertEquals("1:00:00", TimeMath.clock(3_600_000))
        assertEquals("00:01,50", TimeMath.clock(1_500, centis = true))
        // Odliczanie zaokrągla w górę, żeby „00:00” pojawiło się dopiero na końcu.
        assertEquals("00:02", TimeMath.countdown(1_001))
        assertEquals("00:00", TimeMath.countdown(0))
    }

    @Test
    fun `plan treningu rozpisuje sie na odcinki`() {
        val plan = IntervalPlan(prepareSec = 10, workSec = 20, restSec = 10, rounds = 3, sets = 2, setRestSec = 60, cooldownSec = 30)
        val steps = plan.steps()
        assertEquals(IntervalStep.Kind.PREPARE, steps.first().kind)
        assertEquals(IntervalStep.Kind.COOLDOWN, steps.last().kind)
        assertEquals(plan.totalSec, steps.sumOf { it.seconds })
        // Odpoczynek tylko między rundami, nie po ostatniej.
        assertEquals(6, steps.count { it.kind == IntervalStep.Kind.WORK })
        assertEquals(4, steps.count { it.kind == IntervalStep.Kind.REST })
        assertEquals(1, steps.count { it.kind == IntervalStep.Kind.SET_REST })
    }

    @Test
    fun `po czwartym skupieniu przychodzi dluga przerwa`() {
        var p = Pomodoro(longEvery = 4)
        repeat(3) {
            p = TimeMath.nextPomodoro(p)
            assertEquals(PomodoroPhase.SHORT_BREAK, p.phase)
            p = TimeMath.nextPomodoro(p)
            assertEquals(PomodoroPhase.FOCUS, p.phase)
        }
        p = TimeMath.nextPomodoro(p)
        assertEquals(PomodoroPhase.LONG_BREAK, p.phase)
    }

    @Test
    fun `wydarzenie coroczne przeskakuje na nastepna rocznice`() {
        val event = CountdownEvent(id = 1, title = "Urodziny", at = at("2000-06-15T10:00:00"), yearly = true)
        val target = TimeMath.eventTarget(event, at("2026-07-01T00:00:00"), zone)
        assertEquals(2027, java.time.Instant.ofEpochMilli(target).atZone(zone).year)
        assertEquals(27, TimeMath.anniversary(event, at("2026-07-01T00:00:00"), zone))
    }

    @Test
    fun `rocznica 29 lutego w zwyklym roku wypada 28 lutego`() {
        val event = CountdownEvent(id = 1, title = "Luty", at = at("2024-02-29T08:00:00"), yearly = true)
        val target = TimeMath.eventTarget(event, at("2026-01-01T00:00:00"), zone)
        val date = java.time.Instant.ofEpochMilli(target).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 2, 28), date)
    }

    @Test
    fun `roznica stref i dzien w innej strefie`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val summer = at("2026-07-01T12:00:00")
        assertEquals(7 * 60, TimeMath.zoneOffsetMinutes(tokyo, summer, zone))
        assertEquals(0, TimeMath.dayShift(tokyo, summer, zone))
        assertEquals(1, TimeMath.dayShift(tokyo, at("2026-07-01T23:00:00"), zone))
    }

    @Test
    fun `czesci czasu`() {
        val parts = TimeMath.parts(90_061_500)
        assertEquals(1L, parts.days)
        assertEquals(1L, parts.hours)
        assertEquals(1L, parts.minutes)
        assertEquals(1L, parts.seconds)
    }

    @Test
    fun `zmiana czasu na letni nie gubi budzika`() {
        // W Polsce 29 marca 2026 zegary przeskakują z 2:00 na 3:00.
        val alarm = Alarm(id = 1, hour = 2, minute = 30, days = Alarm.EVERY_DAY)
        val next = TimeMath.nextTrigger(alarm, at("2026-03-28T23:00:00"), zone)
        assertTrue(next != null && next > at("2026-03-28T23:00:00"))
        assertEquals(LocalDate.of(2026, 3, 29), java.time.Instant.ofEpochMilli(next!!).atZone(zone).toLocalDate())
    }
}
