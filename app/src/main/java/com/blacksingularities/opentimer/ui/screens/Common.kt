package com.blacksingularities.opentimer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opentimer.i18n.tr
import com.blacksingularities.opentimer.ui.components.RoundIcon
import com.blacksingularities.opentimer.ui.theme.Palette
import com.blacksingularities.opentimer.ui.theme.reactive
import kotlinx.coroutines.delay

/** Bieżący czas odświeżany co [periodMs] (wyrównany do pełnych okresów, żeby sekundy nie „skakały”). */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs + 1)
        }
    }
    return now
}

/** Nagłówek zakładki: podtytuł i przyciski po prawej (nazwę zakładki pokazuje dolny pasek). */
@Composable
fun TabHeader(subtitle: String?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            subtitle.orEmpty(), fontSize = 14.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Ekran nad zakładkami: przycisk wstecz, tytuł, akcje i przewijana treść. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxSize())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** Biała karta akcji – główny przycisk ekranu (jak „Otwórz dokument” w OpenPDF). */
@Composable
fun PrimaryCard(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .reactive(title, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Ink, modifier = Modifier.padding(end = 14.dp))
        Column {
            Text(title, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Palette.Ink.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

/** Ciemna karta listy. */
@Composable
fun Tile(modifier: Modifier = Modifier, active: Boolean = false, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) Palette.PanelHigh else Color.White.copy(alpha = 0.06f))
            .padding(padding),
        content = content,
    )
}

/** Nagłówek sekcji wielkimi literami. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Palette.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = modifier.padding(start = 4.dp, top = 4.dp))
}

/** Pierścień postępu z treścią w środku. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 8.dp,
    color: Color = Color.White,
    track: Color = Color.White.copy(alpha = 0.1f),
    marker: Float? = null,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val size = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), size, style = Stroke(w))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), size, style = Stroke(w, cap = StrokeCap.Round))
            if (marker != null) {
                val a = Math.toRadians((marker * 360f - 90f).toDouble())
                val r = size.width / 2
                val c = Offset(this.size.width / 2, this.size.height / 2)
                drawCircle(color, w * 0.9f, Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()))
            }
        }
        content()
    }
}

/** Liczba z przyciskami − / +. */
@Composable
fun Stepper(
    title: String,
    value: Int,
    format: (Int) -> String,
    range: IntRange,
    step: Int = 1,
    icon: ImageVector? = null,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            com.blacksingularities.opentimer.ui.components.RowIcon(icon)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        RoundIcon(Icons.Rounded.Remove, tr("Mniej"), size = 34.dp, enabled = value > range.first) { onChange((value - step).coerceIn(range)) }
        Text(format(value), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 76.dp).padding(horizontal = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundIcon(Icons.Rounded.Add, tr("Więcej"), size = 34.dp, enabled = value < range.last) { onChange((value + step).coerceIn(range)) }
    }
}

/** Sekundy jako „0:45”, „2:00”. */
fun secLabel(s: Int): String = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "$s s"

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))

// Segmenty a–g cyfry siedmiosegmentowej: góra, prawa górna, prawa dolna, dół, lewa dolna, lewa górna, środek.
private val LcdDigits = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
    '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g",
)

/** Cyfry jak na wyświetlaczu ciekłokrystalicznym: pochylone segmenty z przygaszonym „8” w tle. */
@Composable
fun LcdText(text: String, height: Dp, modifier: Modifier = Modifier, color: Color = Color.White, maxWidth: Dp = Dp.Infinity) {
    val digitW = 0.56f
    val narrowW = 0.24f
    val units = text.sumOf { (if (it == ':' || it == '.') narrowW else digitW + 0.12f).toDouble() }.toFloat()
    // Długi tekst (np. z godzinami) zmniejsza się, żeby zmieścić się w [maxWidth].
    val fit = if (maxWidth == Dp.Infinity) height else minOf(height, maxWidth / units)
    Canvas(modifier.height(fit).width(fit * units)) {
        val h = size.height
        val t = h * 0.1f
        val skew = 0.1f
        val ghost = color.copy(alpha = 0.07f)
        fun seg(x0: Float, y0: Float, x1: Float, y1: Float, col: Color) {
            // Sześciokątny segment od (x0,y0) do (x1,y1), poziomy albo pionowy.
            val p = Path()
            val sk = { x: Float, y: Float -> Offset(x + (h - y) * skew, y) }
            val pts = if (y0 == y1) listOf(
                sk(x0, y0), sk(x0 + t / 2, y0 - t / 2), sk(x1 - t / 2, y0 - t / 2), sk(x1, y0), sk(x1 - t / 2, y0 + t / 2), sk(x0 + t / 2, y0 + t / 2),
            ) else listOf(
                sk(x0, y0), sk(x0 + t / 2, y0 + t / 2), sk(x0 + t / 2, y1 - t / 2), sk(x0, y1), sk(x0 - t / 2, y1 - t / 2), sk(x0 - t / 2, y0 + t / 2),
            )
            p.moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { p.lineTo(it.x, it.y) }
            p.close()
            drawPath(p, col)
        }
        var x = 0f
        for (ch in text) {
            if (ch == ':' || ch == '.') {
                val w = h * narrowW
                val cx = x + w / 2
                val dots = if (ch == ':') listOf(h * 0.32f, h * 0.68f) else listOf(h * 0.93f)
                dots.forEach { y -> drawCircle(color, t * 0.6f, Offset(cx + (h - y) * skew, y)) }
                x += w
                continue
            }
            val w = h * digitW
            val l = x + t / 2
            val r = x + w - t / 2
            val top = t / 2
            val mid = h / 2
            val bot = h - t / 2
            val on = LcdDigits[ch].orEmpty()
            val gap = t * 0.15f
            val segs = mapOf(
                'a' to floatArrayOf(l + gap, top, r - gap, top),
                'b' to floatArrayOf(r, top + gap, r, mid - gap),
                'c' to floatArrayOf(r, mid + gap, r, bot - gap),
                'd' to floatArrayOf(l + gap, bot, r - gap, bot),
                'e' to floatArrayOf(l, mid + gap, l, bot - gap),
                'f' to floatArrayOf(l, top + gap, l, mid - gap),
                'g' to floatArrayOf(l + gap, mid, r - gap, mid),
            )
            if (ch != ' ') segs.forEach { (k, v) -> seg(v[0], v[1], v[2], v[3], if (k in on) color else ghost) }
            x += w + h * 0.12f
        }
    }
}
