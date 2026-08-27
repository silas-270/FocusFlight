package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Haze
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

// Margin line x-position in dp
private val LogbookMarginDp = 36.dp

// Every logbook row used to draw its own 600-speck "paper grain" texture from scratch
// (profiled via `adb shell dumpsys gfxinfo`: with a long flight history, rows entering
// view during a fast fling were costing 6-13ms each in the draw phase, compounding into
// 70-80ms frames). The speckle pattern is decorative noise, not tied to any particular
// flight, so it's baked into one shared bitmap a single time and tiled across every row
// as a shader brush — one cheap drawRect instead of 600 drawCircle calls per row.
//
// Building that bitmap still costs a few tens of ms of Bitmap/Canvas work, and a plain
// `by lazy` pays that cost synchronously on the UI thread the first time any row is
// drawn — which showed up as its own 70-80ms hitch right as Flight History scrolled
// into view. AccountViewModel.init kicks `warm()` off on a background thread as soon as
// the screen opens, so by the time a real scroll gesture reaches the logbook the bitmap
// is already built; `brush` still falls back to building it inline (thread-safe via the
// default `lazy` mode) if something reads it first.
internal object PaperGrainTexture {
    val brush: Brush by lazy {
        val tileWidthPx = 480
        val tileHeightPx = 176
        val bitmap = android.graphics.Bitmap.createBitmap(
            tileWidthPx,
            tileHeightPx,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rng = Random(0xDEADBEEFL)
        repeat(600) {
            val fx = rng.nextFloat()
            val fy = rng.nextFloat()
            val falpha = rng.nextFloat()
            val argb = if (falpha > 0.6f) {
                Color(0xFF8B6914).copy(alpha = falpha * 0.08f) // warm dark speck
            } else {
                Color(0xFFFFFFE0).copy(alpha = falpha * 0.18f) // lighter highlight
            }
            paint.color = argb.toArgb()
            canvas.drawCircle(
                fx * tileWidthPx,
                fy * tileHeightPx,
                0.5f + falpha * 1.0f,
                paint
            )
        }
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    fun warm() {
        brush
    }
}

// ── Shared paper palette (used by both LogPaperCard's own drawing and callers' content) ────
internal val LogbookInkDark   = Color(0xFF1A1208)   // near-black ink
internal val LogbookInkMid    = Color(0xFF6B5033)   // warm sepia mid-tone
internal val LogbookInkFaint  = Color(0xFFB09870)   // faded sepia labels
internal val LogbookMarginRed = Color(0xFFCC1C1C)   // bright red margin / rubber-stamp color

/**
 * The logbook's paper-card chrome — parchment texture, ruled lines, red margin line, and the
 * numbered margin stamp — factored out of what was originally [LogbookEntry]'s entire body so a
 * second log (the Achievements screen's "Challenges completed" log, modeled on this one per
 * docs/design/achievements.md) can render as the same physical logbook instead of re-implementing
 * this Canvas work or inventing a second visual language. [content] fills the row to the right of
 * the margin stamp, e.g. [LogbookEntry]'s three data rows or `ChallengeCompletionEntry`'s own.
 */
@Composable
internal fun LogPaperCard(
    entryNumber: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // ── Paper palette (local to the Canvas drawing below) ──────────────────
    val parchment      = Color(0xFFF5E6C0)   // aged cream
    val parchmentDark  = Color(0xFFEDD89A)   // slightly more yellowed patch
    val ruleBlue       = Color(0xFF8EB4D4).copy(alpha = 0.55f)   // classic ink-blue lines
    val marginRed      = LogbookMarginRed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        // ── Paper texture Canvas (fills full card) ─────────────────────────
        val density = LocalDensity.current
        val marginPx     = with(density) { LogbookMarginDp.toPx() }
        val rowHeightPx  = with(density) { 22.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                // Own render node: content is static per entry (grain points are remembered
                // by flight.id), so caching it here means scrolling repositions the cached
                // raster instead of re-issuing ~600 drawCircle calls per row every frame.
                .graphicsLayer { }
        ) {
            // 1. Parchment base fill
            drawRect(color = parchment)

            // 2. Subtle warm gradient patch (upper-left yellowing effect)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(parchmentDark.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.3f),
                    radius = size.width * 0.55f
                )
            )

            // 3. Paper grain — shared speckle texture tiled across every row
            drawRect(brush = PaperGrainTexture.brush)

            // 4. Faint vignette edges (paper edge darkening)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF8B6914).copy(alpha = 0.12f),
                        Color.Transparent,
                        Color(0xFF8B6914).copy(alpha = 0.08f)
                    )
                )
            )

            // 5. Blue ruled lines (classic notebook style)
            for (i in 1..3) {
                drawLine(
                    color = ruleBlue,
                    start = Offset(0f, rowHeightPx * i),
                    end   = Offset(size.width, rowHeightPx * i),
                    strokeWidth = 0.9f
                )
            }

            // 6. Red margin line
            drawLine(
                color = marginRed,
                start = Offset(marginPx, 0f),
                end   = Offset(marginPx, size.height),
                strokeWidth = 2.0f
            )
        }

        // ── Content overlay ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(start = 0.dp, end = com.example.focusflight.ui.theme.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Margin: entry number ──
            Box(
                modifier = Modifier.width(LogbookMarginDp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(entryNumber),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    ),
                    color = marginRed.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.width(10.dp))

            content()
        }
    }
}

@Composable
internal fun LogbookEntry(flight: FlightLog, entryNumber: Int) {
    val dateStr = remember(flight.completedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(flight.completedAt))
    }
    val hoursInt = flight.durationMin / 60
    val minutesInt = flight.durationMin % 60
    val durationStr = String.format(Locale.US, "%02dh%02dm", hoursInt, minutesInt)
    val distanceStr = com.example.focusflight.util.formatMiles(flight.distanceKm)

    val inkDark   = LogbookInkDark
    val inkMid    = LogbookInkMid
    val inkFaint  = LogbookInkFaint
    val marginRed = LogbookMarginRed

    LogPaperCard(entryNumber = entryNumber) {
        // ── Main data columns ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: DATE on left, flight number stamp on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = inkFaint
                )
                // Flight number — rubber-stamp style
                Box(
                    modifier = Modifier
                        .border(1.dp, marginRed.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = flight.flightNumber,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = marginRed.copy(alpha = 0.75f)
                    )
                }
            }

            // Row 2: ORIGIN ··✈·· DEST
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = flight.originIata,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp
                    ),
                    color = inkDark
                )
                Text(
                    text = "·  ·  ·  ✈  ·  ·  ·",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 0.sp
                    ),
                    color = inkMid.copy(alpha = 0.45f)
                )
                Text(
                    text = flight.destIata,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp
                    ),
                    color = inkDark
                )
            }

            // Row 3: DIST | TIME
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LogbookDataCell(label = "DIST", value = distanceStr, labelColor = inkFaint, valueColor = inkMid)
                LogbookDataCell(label = "TIME", value = durationStr, labelColor = inkFaint, valueColor = inkMid)
            }
        }
    }
}

@Composable
internal fun LogbookDataCell(
    label: String,
    value: String,
    labelColor: Color = Haze.copy(alpha = 0.6f),
    valueColor: Color = Amber
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 1.5.sp
            ),
            color = labelColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = valueColor
        )
    }
}
