package com.blacksingularities.opentimer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cały stan aplikacji w jednym miejscu: SharedPreferences (JSON) + StateFlow dla interfejsu.
 * Zapis jest synchroniczny względem pamięci i asynchroniczny na dysk (`apply`), więc odbiorniki
 * budzików i interfejs widzą zawsze to samo. Zmiany robi [Actions], który dba też o harmonogram.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private val lock = Any()

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    private val _timers = MutableStateFlow<List<TimerItem>>(emptyList())
    private val _presets = MutableStateFlow<List<TimerPreset>>(emptyList())
    private val _stopwatch = MutableStateFlow(Stopwatch())
    private val _zones = MutableStateFlow<List<String>>(emptyList())
    private val _events = MutableStateFlow<List<CountdownEvent>>(emptyList())
    private val _intervals = MutableStateFlow<List<IntervalPlan>>(emptyList())
    private val _pomodoro = MutableStateFlow(Pomodoro())
    private val _settings = MutableStateFlow(AppSettings())

    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    val timers: StateFlow<List<TimerItem>> = _timers.asStateFlow()
    val presets: StateFlow<List<TimerPreset>> = _presets.asStateFlow()
    val stopwatch: StateFlow<Stopwatch> = _stopwatch.asStateFlow()
    val zones: StateFlow<List<String>> = _zones.asStateFlow()
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()
    val intervals: StateFlow<List<IntervalPlan>> = _intervals.asStateFlow()
    val pomodoro: StateFlow<Pomodoro> = _pomodoro.asStateFlow()
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val isReady: Boolean get() = ::prefs.isInitialized

    fun init(context: Context) {
        synchronized(lock) {
            if (isReady) return
            // Pamięć chroniona urządzeniem: budziki działają po restarcie jeszcze przed odblokowaniem.
            val storage = context.createDeviceProtectedStorageContext()
            storage.moveSharedPreferencesFrom(context, FILE)
            prefs = storage.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            _alarms.value = read("alarms") { it.objects(Alarm::fromJson) } ?: defaultAlarms()
            _timers.value = read("timers") { it.objects(TimerItem::fromJson) } ?: emptyList()
            _presets.value = read("presets") { it.objects(TimerPreset::fromJson) } ?: defaultPresets()
            // Jednorazowo: dłuższe szybkie czasy (1 h, 1,5 h, 2 h) także dla zapisanych wcześniej list.
            if (!prefs.getBoolean("presetsLong", false)) {
                val list = (_presets.value + LongPresets.map { TimerPreset("", it) }).distinctBy { it.durationMs }.sortedBy { it.durationMs }
                _presets.value = list
                writeArray("presets", list.map { it.toJson() })
                prefs.edit().putBoolean("presetsLong", true).apply()
            }
            _zones.value = read("zones") { it.strings() } ?: listOf("Europe/London", "America/New_York", "Asia/Tokyo")
            _events.value = read("events") { it.objects(CountdownEvent::fromJson) } ?: emptyList()
            _intervals.value = read("intervals") { it.objects(IntervalPlan::fromJson) } ?: defaultIntervals()
            _stopwatch.value = readObject("stopwatch")?.let(Stopwatch::fromJson) ?: Stopwatch()
            _pomodoro.value = readObject("pomodoro")?.let(Pomodoro::fromJson) ?: Pomodoro()
            _settings.value = readObject("settings")?.let(AppSettings::fromJson) ?: AppSettings()
            // Strefa domowa to ta, w ktorej aplikacja zostala pierwszy raz uruchomiona.
            if (_settings.value.homeZone.isBlank()) {
                updateSettingsLocked { it.copy(homeZone = java.time.ZoneId.systemDefault().id) }
            }
        }
    }

    private fun <T> read(key: String, map: (JSONArray) -> T): T? =
        prefs.getString(key, null)?.let { runCatching { map(JSONArray(it)) }.getOrNull() }

    private fun readObject(key: String): JSONObject? =
        prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun writeArray(key: String, items: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray(items).toString()).apply()
    }

    fun updateAlarms(change: (List<Alarm>) -> List<Alarm>): List<Alarm> = synchronized(lock) {
        val next = change(_alarms.value).sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        _alarms.value = next
        writeArray("alarms", next.map { it.toJson() })
        next
    }

    fun updateTimers(change: (List<TimerItem>) -> List<TimerItem>): List<TimerItem> = synchronized(lock) {
        val next = change(_timers.value)
        _timers.value = next
        writeArray("timers", next.map { it.toJson() })
        next
    }

    fun updatePresets(change: (List<TimerPreset>) -> List<TimerPreset>) = synchronized(lock) {
        val next = change(_presets.value)
        _presets.value = next
        writeArray("presets", next.map { it.toJson() })
    }

    fun updateStopwatch(change: (Stopwatch) -> Stopwatch): Stopwatch = synchronized(lock) {
        val next = change(_stopwatch.value)
        _stopwatch.value = next
        prefs.edit().putString("stopwatch", next.toJson().toString()).apply()
        next
    }

    fun updateZones(change: (List<String>) -> List<String>) = synchronized(lock) {
        val next = change(_zones.value).distinct()
        _zones.value = next
        prefs.edit().putString("zones", JSONArray(next).toString()).apply()
    }

    fun updateEvents(change: (List<CountdownEvent>) -> List<CountdownEvent>) = synchronized(lock) {
        val next = change(_events.value).sortedBy { it.at }
        _events.value = next
        writeArray("events", next.map { it.toJson() })
    }

    fun updateIntervals(change: (List<IntervalPlan>) -> List<IntervalPlan>) = synchronized(lock) {
        val next = change(_intervals.value)
        _intervals.value = next
        writeArray("intervals", next.map { it.toJson() })
    }

    fun updatePomodoro(change: (Pomodoro) -> Pomodoro): Pomodoro = synchronized(lock) {
        val next = change(_pomodoro.value)
        _pomodoro.value = next
        prefs.edit().putString("pomodoro", next.toJson().toString()).apply()
        next
    }

    fun updateSettings(change: (AppSettings) -> AppSettings): AppSettings = synchronized(lock) {
        updateSettingsLocked(change)
    }

    private fun updateSettingsLocked(change: (AppSettings) -> AppSettings): AppSettings {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit().putString("settings", next.toJson().toString()).apply()
        return next
    }

    /** Kolejny wolny identyfikator (wspólny licznik dla budzików, minutników i wydarzeń). */
    fun nextId(): Int = synchronized(lock) {
        val id = prefs.getInt("nextId", 1)
        prefs.edit().putInt("nextId", id + 1).apply()
        id
    }

    private fun defaultAlarms() = listOf(
        Alarm(id = 9001, hour = 7, minute = 0, days = Alarm.WEEKDAYS, enabled = false),
        Alarm(id = 9002, hour = 9, minute = 0, days = Alarm.WEEKEND, enabled = false),
    )

    private fun defaultPresets() = listOf(
        TimerPreset("", 60_000L),
        TimerPreset("", 3 * 60_000L),
        TimerPreset("", 5 * 60_000L),
        TimerPreset("", 10 * 60_000L),
        TimerPreset("", 15 * 60_000L),
        TimerPreset("", 30 * 60_000L),
    ) + LongPresets.map { TimerPreset("", it) }

    private val LongPresets = listOf(60 * 60_000L, 90 * 60_000L, 120 * 60_000L)

    private fun defaultIntervals() = listOf(
        IntervalPlan("Tabata", 10, 20, 10, 8, 1, 60, 0),
        IntervalPlan("HIIT 40/20", 10, 40, 20, 10, 1, 60, 0),
        IntervalPlan("EMOM 10", 10, 60, 0, 10, 1, 0, 0),
        IntervalPlan("3 × 6 · 45/15", 15, 45, 15, 6, 3, 90, 60),
    )

    private const val FILE = "opentimer"
}
