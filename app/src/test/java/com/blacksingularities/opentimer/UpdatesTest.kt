package com.blacksingularities.opentimer

import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.AppSettings
import com.blacksingularities.opentimer.data.Challenge
import com.blacksingularities.opentimer.data.HourFormat
import com.blacksingularities.opentimer.data.Stopwatch
import com.blacksingularities.opentimer.data.TimerItem
import com.blacksingularities.opentimer.update.isNewer
import com.blacksingularities.opentimer.update.parseRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aktualizator i zapis stanu – obie rzeczy działają na zwykłym JSON-ie. */
class UpdatesTest {
    @Test
    fun `wersje porownuja sie liczbowo a nie tekstowo`() {
        assertTrue(isNewer("0.1.12", "0.1.9"))
        assertTrue(isNewer("1.0.0", "0.9.9"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("0.9.0", "1.0.0"))
        assertFalse(isNewer("1.0.0-dev", "1.0.0"))
    }

    @Test
    fun `wydanie z GitHuba daje plik apk i numer wersji bez v`() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "body": "Nowości\n---\nstopka",
              "html_url": "https://example.com/release",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10},
                {"name": "OpenTimer-1.2.3.apk", "browser_download_url": "https://example.com/app.apk", "size": 1234}
              ]
            }
        """.trimIndent()
        val info = parseRelease(json)
        assertEquals("1.2.3", info.version)
        assertEquals("https://example.com/app.apk", info.assetUrl)
        assertEquals(1234L, info.sizeBytes)
        assertEquals("Nowości", info.notes)
    }

    @Test(expected = IllegalStateException::class)
    fun `wydanie bez apk jest bledem`() {
        parseRelease("""{"tag_name":"v1.0.0","assets":[]}""")
    }

    @Test
    fun `budzik przechodzi przez json bez zmian`() {
        val alarm = Alarm(
            id = 7, hour = 6, minute = 45, days = Alarm.WEEKDAYS, label = "Praca",
            challenge = Challenge.MATH_HARD, challengeCount = 5, volume = 80, deleteAfter = true,
        )
        assertEquals(alarm, Alarm.fromJson(alarm.toJson()))
    }

    @Test
    fun `minutnik stoper i ustawienia przechodza przez json`() {
        val timer = TimerItem(id = 3, label = "Herbata", durationMs = 180_000, remainingMs = 90_000, running = true, endAt = 5_000)
        assertEquals(timer, TimerItem.fromJson(timer.toJson()))

        val stopwatch = Stopwatch(running = true, startedAt = 1_000, accumulatedMs = 2_000, laps = listOf(500L, 1_500L))
        assertEquals(stopwatch, Stopwatch.fromJson(stopwatch.toJson()))

        val settings = AppSettings(hourFormat = HourFormat.H12, defaultSnooze = 7, nightDim = 0.25f, autoUpdate = false)
        assertEquals(settings, AppSettings.fromJson(settings.toJson()))
    }

    @Test
    fun `dni tygodnia zapisane sa jako bity`() {
        val weekdays = Alarm(id = 1, hour = 6, minute = 0, days = Alarm.WEEKDAYS)
        assertTrue(weekdays.repeatsOn(1))
        assertTrue(weekdays.repeatsOn(5))
        assertFalse(weekdays.repeatsOn(6))
        assertFalse(weekdays.repeatsOn(7))
        val weekend = Alarm(id = 2, hour = 9, minute = 0, days = Alarm.WEEKEND)
        assertTrue(weekend.repeatsOn(6))
        assertTrue(weekend.repeatsOn(7))
        assertFalse(weekend.repeatsOn(1))
    }

    @Test
    fun `postep minutnika liczy sie od konca odliczania`() {
        val timer = TimerItem(id = 1, durationMs = 100_000, running = true, endAt = 50_000)
        assertEquals(0.5f, timer.progressAt(0L), 0.001f)
        assertEquals(50_000L, timer.remainingAt(0L))
    }
}
