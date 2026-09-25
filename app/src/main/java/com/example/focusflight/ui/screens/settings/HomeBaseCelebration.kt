package com.example.focusflight.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.focusflight.ui.components.FocusButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.R
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.delay

private val IataLetterSpacing = 8.sp

/**
 * The shared "you have arrived somewhere" moment behind both home-base outcomes: returning home, and
 * setting a new home base. A full screen rather than a modal card - each one is the end of a flow
 * and hands the pilot back to the Hub, which a dialog floating over the Passport can't express.
 *
 * Visual language is deliberately borrowed from [com.example.focusflight.ui.screens.arrival
 * .ArrivalCelebrationScreen]: a Midnight field, one master alpha fading every child in together, and
 * a spring "slam" that drops the hero into place.
 *
 * Type mixes all three system families the app has available (there are no bundled font files), and
 * the contrast between them *is* the design: monospace for the IATA code (this codebase's long-
 * standing marker for instrument data), an italic serif for the airport's name - used nowhere else
 * in the app, so it reads as a deliberately warm, personal note - and plain sans for the body line.
 */
@Composable
internal fun HomeBaseCelebrationScreen(
    eyebrow: String,
    iata: String,
    airportName: String,
    locationLine: String,
    supportingLine: String = "",
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    ctaText: String = "CONTINUE",
    hero: (@Composable () -> Unit)? = null,
    inlineHero: (@Composable () -> Unit)? = null
) {
    var landed by remember { mutableStateOf(false) }
    val slam by animateFloatAsState(
        targetValue = if (landed) 1f else 4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "celebration_slam"
    )
    val fade by animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = tween(300),
        label = "celebration_fade"
    )

    val view = LocalView.current
    LaunchedEffect(Unit) {
        delay(120)
        landed = true
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {
            // Haptics are a flourish, never a requirement - a device that refuses them is fine.
        }
    }

    // Back must not strand the pilot on a screen whose only exit is the button.
    BackHandler(onBack = onContinue)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Midnight)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (hero != null && inlineHero == null) {
                Box(modifier = Modifier.graphicsLayer { alpha = fade }) { hero() }
                // Without this the hero sits flush on the eyebrow text.
                Spacer(modifier = Modifier.height(Spacing.Large))
            }

            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = Haze,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = fade }
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            if (inlineHero != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        scaleX = slam
                        scaleY = slam
                        alpha = fade
                    }
                ) {
                    Text(
                        text = iata,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            lineHeight = 72.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        ),
                        color = Amber,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(Spacing.Large))

                    Box(
                        modifier = Modifier
                            .height(72.dp)
                            .width(2.dp)
                            .background(Haze.copy(alpha = 0.45f), RoundedCornerShape(1.dp))
                    )

                    Spacer(modifier = Modifier.width(Spacing.Large))

                    inlineHero()
                }
            } else {
                Text(
                    text = iata,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        lineHeight = 80.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = IataLetterSpacing
                    ),
                    color = Amber,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        scaleX = slam
                        scaleY = slam
                        alpha = fade
                        // letterSpacing also trails the final glyph, which drags a centred string
                        // visually left - nudge back by half of it so the code sits truly centred.
                        translationX = IataLetterSpacing.toPx() / 2f
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Medium))

            if (airportName.isNotBlank()) {
                Text(
                    text = airportName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal
                    ),
                    color = OffWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = fade }
                )
            }

            if (locationLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = locationLine,
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 2.sp),
                    color = Haze,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = fade }
                )
            }

            if (supportingLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Text(
                    text = supportingLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Haze,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = fade }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            FocusButton(
                text = ctaText,
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade }
            )

            Spacer(modifier = Modifier.height(Spacing.Large))
        }
    }
}

/**
 * Hero for the return-home celebration: the aircraft climbing, drifting up into place as the screen
 * arrives. Deliberately a settle rather than the arrival screen's full-height flyover - this icon
 * sits inside the content column, and a translation big enough to cross the screen would sail
 * straight through the text beneath it (a `graphicsLayer` translation moves pixels, not layout).
 */
@Composable
internal fun ReturnHomeHero() {
    val drift = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        drift.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 900,
                easing = CubicBezierEasing(0.15f, 0.55f, 0.3f, 1.0f)
            )
        )
    }

    Icon(
        imageVector = Icons.Outlined.Flight,
        contentDescription = null,
        tint = Amber,
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                translationY = drift.value.dp.toPx()
                // Nose up, as if climbing away - the same "dynamic ascension angle" tilt the
                // onboarding logo uses.
                rotationZ = -20f
            }
    )
}

/** Hero for the change-home-base celebration: the tower-and-terminal mark, in line-art gold. */
@Composable
internal fun HomeBaseSetHero(modifier: Modifier = Modifier.size(56.dp)) {
    Icon(
        painter = painterResource(R.drawable.ic_airport_tower),
        contentDescription = null,
        tint = Amber,
        modifier = modifier
    )
}
