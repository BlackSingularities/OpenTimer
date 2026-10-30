package com.blacksingularities.opentimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.plural
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.Fmt
import com.blacksingularities.opentimer.ui.Route
import com.blacksingularities.opentimer.ui.Tab
import com.blacksingularities.opentimer.ui.UpdateBanner
import com.blacksingularities.opentimer.ui.components.ActionRow
import com.blacksingularities.opentimer.ui.components.Group
import com.blacksingularities.opentimer.ui.components.InfoRow
import com.blacksingularities.opentimer.ui.components.PillButton
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive
import com.blacksingularities.opentimer.update.UpdateManager
import com.blacksingularities.opentimer.update.UpdateState

/** Jednolita informacja o autorze i wsparciu – ta sama we wszystkich aplikacjach. */
const val AUTHOR = "Adam Rędzikowski"
const val AUTHOR_SITE = "redzikowski.com"
const val AUTHOR_URL = "https://redzikowski.com"
const val COFFEE_URL = "https://buycoffee.to/adamredzikowski"

/** Zakładka „Więcej”: narzędzia czasu, drzemka na szybko, ustawienia i informacje. */
@Composable
fun MoreTab(vm: AppViewModel) {
    val context = LocalContext.current
    val pomodoro by Store.pomodoro.collectAsState()
    val events by Store.events.collectAsState()
    val settings by Store.settings.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabHeader(tr("Więcej"), tr("Narzędzia czasu i ustawienia")) {
                RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), size = 38.dp, badge = if (vm.updatePending) "1" else null) { vm.open(Route.Settings) }
            }
        }
        item { UpdateBanner(vm) }
        item {
            ToolGrid(
                listOf(
                    Tool(Icons.Rounded.Timelapse, tr("Pomodoro"), tr("Skupienie {0} min · dziś {1}", pomodoro.focusMin, pomodoro.doneToday)) { vm.open(Route.Pomodoro) },
                    Tool(Icons.Rounded.DirectionsRun, tr("Interwały"), tr("Tabata, HIIT, własne plany")) { vm.open(Route.Intervals) },
                    Tool(Icons.Rounded.Celebration, tr("Odliczanie"), if (events.isEmpty()) tr("Do ważnych dni") else tr("Wydarzeń: {0}", events.size)) { vm.open(Route.Countdowns) },
                    Tool(Icons.Rounded.MusicNote, tr("Metronom"), tr("Tempo, metrum, odliczanie")) { vm.open(Route.Metronome) },
                    Tool(Icons.Rounded.Calculate, tr("Kalkulator czasu"), tr("Dodawanie godzin i różnice dat")) { vm.open(Route.Calculator) },
                    Tool(Icons.Rounded.Bedtime, tr("Zegar nocny"), tr("Przyciemniony, w kolorze")) { vm.open(Route.NightClock) },
                    Tool(Icons.Rounded.Public, tr("Zegary świata"), tr("Godzina w innych strefach")) { vm.select(Tab.CLOCK); vm.open(Route.ZonePicker) },
                    Tool(Icons.Rounded.Hotel, tr("Drzemka {0} min", settings.defaultSnooze), tr("Budzik liczony od teraz")) {
                        val a = Actions.nap(context, settings.defaultSnooze)
                        vm.showSnack(tr("Drzemka do {0}", Fmt.alarmTime(context, a)))
                    },
                ),
            )
        }
        item { SectionLabel(tr("Twoje liczby")) }
        item {
            Group(null) {
                InfoRow(Icons.Rounded.Timelapse, tr("Sesje skupienia dziś"), "${pomodoro.doneToday}")
                InfoRow(Icons.Rounded.Favorite, tr("Sesje skupienia łącznie"), "${pomodoro.doneTotal}")
                InfoRow(
                    Icons.Rounded.Timelapse,
                    tr("Czas skupienia łącznie"),
                    if (pomodoro.focusTotalMin >= 60) {
                        tr("{0} h {1} min", pomodoro.focusTotalMin / 60, pomodoro.focusTotalMin % 60)
                    } else {
                        "${pomodoro.focusTotalMin} ${plural(pomodoro.focusTotalMin.toInt(), "minuta", "minuty", "minut")}"
                    },
                )
            }
        }
        item { SectionLabel(tr("O aplikacji")) }
        item { AboutBlock(vm) }
    }
}

private class Tool(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
private fun ToolGrid(tools: List<Tool>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
                            .reactive(t.title, onClick = t.onClick).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(t.subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Informacja o aplikacji: wersja, aktualizacje, autor i wsparcie. Blok jest wspólny dla
 * zakładki „Więcej” i ustawień, więc wszędzie wygląda i brzmi identycznie.
 */
@Composable
fun AboutBlock(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("OpenTimer ${UpdateManager.currentVersion}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Zegar, budzik, stoper, minutnik i narzędzia czasu w jednej aplikacji. Bez reklam, śledzenia i konta – wszystko działa w telefonie."),
                fontSize = 12.sp,
                color = Palette.TextDim,
                lineHeight = 17.sp,
            )
        }
        Group(null) {
            val state = vm.update
            ActionRow(
                Icons.Rounded.SystemUpdate,
                when (state) {
                    is UpdateState.Checking -> tr("Sprawdzanie aktualizacji…")
                    is UpdateState.Available -> tr("Nowa wersja {0}", state.info.version)
                    is UpdateState.Downloading -> tr("Pobieranie… {0}%", (state.progress * 100).toInt())
                    is UpdateState.Ready -> tr("Zainstaluj wersję {0}", state.info.version)
                    is UpdateState.Installing -> tr("Instalowanie…")
                    is UpdateState.UpToDate -> tr("Masz najnowszą wersję")
                    is UpdateState.Failed -> tr("Nie udało się sprawdzić: {0}", state.message)
                    UpdateState.Idle -> tr("Sprawdź aktualizacje")
                },
                tr("Aktualizacje pobierane z {0}", AUTHOR_SITE),
            ) { vm.updateNow() }
            ActionRow(Icons.Rounded.Language, AUTHOR_SITE, tr("Pomysł i wykonanie: {0}", AUTHOR)) { uri.openUri(AUTHOR_URL) }
            ActionRow(Icons.Rounded.Coffee, tr("Postaw kawę"), tr("Wesprzyj rozwój na buycoffee.to")) { uri.openUri(COFFEE_URL) }
        }
        Text(
            tr("Pomysł i wykonanie: {0} · {1}", AUTHOR, AUTHOR_SITE),
            fontSize = 11.sp,
            color = Palette.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        )
    }
}

/** Osobny ekran „O aplikacji” – ten sam blok informacji z przyciskiem wsparcia na wierzchu. */
@Composable
fun AboutScreen(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    SubScreen(tr("O aplikacji"), onBack = { vm.back() }) {
        AboutBlock(vm)
        PillButton(tr("Postaw kawę"), modifier = Modifier.fillMaxWidth()) { uri.openUri(COFFEE_URL) }
        Spacer(Modifier.height(8.dp))
    }
}
