package com.blacksingularities.opentimer.ui

import android.content.Context
import android.text.format.DateFormat
import com.blacksingularities.opentimer.core.TimeMath
import com.blacksingularities.opentimer.data.Alarm
import com.blacksingularities.opentimer.data.HourFormat
import com.blacksingularities.opentimer.data.PomodoroPhase
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.i18n.plural
import com.blacksingularities.opentimer.i18n.tr
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Formatowanie godzin, dat i czasów trwania zgodnie z językiem i formatem 12/24 h. */
object Fmt {
    fun is24(context: Context): Boolean = when (Store.settings.value.hourFormat) {
        HourFormat.H24 -> true
        HourFormat.H12 -> false
        HourFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }

    private fun pattern(context: Context, seconds: Boolean) =
        if (is24(context)) (if (seconds) "HH:mm:ss" else "HH:mm") else (if (seconds) "h:mm:ss a" else "h:mm a")

    fun time(context: Context, epoch: Long, zone: ZoneId = ZoneId.systemDefault(), seconds: Boolean = false): String =
        DateTimeFormatter.ofPattern(pattern(context, seconds), I18n.locale).format(Instant.ofEpochMilli(epoch).atZone(zone))

    /** Godzina i minuta budzika; w formacie 12 h oddzielnie „AM/PM” w [ampm]. */
    fun hm(context: Context, hour: Int, minute: Int): Pair<String, String?> {
        if (is24(context)) return "%02d:%02d".format(hour, minute) to null
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d".format(h, minute) to (if (hour < 12) "AM" else "PM")
    }

    fun alarmTime(context: Context, a: Alarm): String = hm(context, a.hour, a.minute).let { (t, ap) -> if (ap == null) t else "$t $ap" }

    fun date(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(if (I18n.english) "EEEE, MMMM d" else "EEEE, d MMMM", I18n.locale)
            .format(Instant.ofEpochMilli(epoch).atZone(zone)).replaceFirstChar { it.uppercase() }

    fun fullDate(epoch: Long): String =
        DateTimeFormatter.ofPattern(if (I18n.english) "EEE, MMM d, yyyy" else "EEE, d MMM yyyy", I18n.locale)
            .format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))

    /** „Dziś 7:00”, „Jutro 7:00”, „Pt 7:00”. */
    fun dayAndTime(context: Context, at: Long): String {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val day = when (d) {
            today -> tr("Dziś")
            today.plusDays(1) -> tr("Jutro")
            else -> if (d.isBefore(today.plusDays(7))) d.dayOfWeek.getDisplayName(TextStyle.SHORT, I18n.locale).replaceFirstChar { it.uppercase() }
            else DateTimeFormatter.ofPattern("d MMM", I18n.locale).format(d)
        }
        return "$day ${time(context, at)}"
    }

    /** „za 7 h 25 min”. */
    fun until(ms: Long): String {
        val p = TimeMath.parts(ms)
        val parts = buildList {
            if (p.days > 0) add("${p.days} ${plural(p.days.toInt(), "dzień", "dni", "dni")}")
            if (p.hours > 0) add("${p.hours} h")
            if (p.minutes > 0 && p.days == 0L) add("${p.minutes} min")
        }
        return if (parts.isEmpty()) tr("za mniej niż minutę") else tr("za {0}", parts.joinToString(" "))
    }

    /** „5 min”, „1 h 30 min”, „45 s”. */
    fun durationShort(ms: Long): String {
        val p = TimeMath.parts(ms)
        val h = p.days * 24 + p.hours
        return buildList {
            if (h > 0) add("$h h")
            if (p.minutes > 0) add("${p.minutes} min")
            if (p.seconds > 0 && h == 0L) add("${p.seconds} s")
        }.joinToString(" ").ifEmpty { "0 s" }
    }

    fun weekOrder(): List<DayOfWeek> =
        if (Store.settings.value.weekStartsMonday) DayOfWeek.entries
        else listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)

    fun dayLetter(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW_STANDALONE, I18n.locale).uppercase()
    fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).replaceFirstChar { it.uppercase() }

    /** Opis powtarzania budzika: „Codziennie”, „Dni robocze”, „Pn, Śr, Pt”, „Jednorazowo”. */
    fun repeat(a: Alarm): String = when (a.days) {
        0 -> if (a.oneShotDate > 0) fullDate(LocalDate.ofEpochDay(a.oneShotDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) else tr("Jednorazowo")
        Alarm.EVERY_DAY -> tr("Codziennie")
        Alarm.WEEKDAYS -> tr("Dni robocze")
        Alarm.WEEKEND -> tr("Weekend")
        else -> weekOrder().filter { a.repeatsOn(it.value) }.joinToString(", ") { dayShort(it) }
    }

    fun phase(p: PomodoroPhase): String = when (p) {
        PomodoroPhase.FOCUS -> tr("Skupienie")
        PomodoroPhase.SHORT_BREAK -> tr("Krótka przerwa")
        PomodoroPhase.LONG_BREAK -> tr("Długa przerwa")
    }

    fun cityOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

    fun zoneName(zoneId: String, at: Long): String {
        val zone = ZoneId.of(zoneId)
        val dst = zone.rules.isDaylightSavings(Instant.ofEpochMilli(at))
        return java.util.TimeZone.getTimeZone(zone).getDisplayName(dst, java.util.TimeZone.LONG, I18n.locale)
    }

    /** „+2 h”, „−5 h 30 min”, „ten sam czas”. */
    fun offset(minutes: Int): String {
        if (minutes == 0) return tr("ten sam czas")
        val sign = if (minutes > 0) "+" else "−"
        val a = kotlin.math.abs(minutes)
        return sign + (if (a % 60 == 0) "${a / 60} h" else "${a / 60} h ${a % 60} min")
    }

    fun zoned(epoch: Long, zone: String): ZonedDateTime = Instant.ofEpochMilli(epoch).atZone(ZoneId.of(zone))
}
