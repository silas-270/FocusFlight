package com.example.focusflight.ui.screens.challenges

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.ui.components.RingProgress
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.components.icon
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.ChallengeGold
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.LogbookInkDark
import com.example.focusflight.ui.theme.LogbookInkFaint
import com.example.focusflight.ui.theme.LogbookMarginRed
import com.example.focusflight.ui.theme.LogbookParchment
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val InitialDelayMs = 325L
private const val FlyInDurationMs = 460
private const val AnticipationDurationMs = 90
private const val SmashDurationMs = 300
private const val ImpactDurationMs = 220
private const val CardSizeDp = 220
private const val ConfettiBurstDurationMs = 2000

private enum class CelebrationPhase {
    INITIAL_DELAY,
    FLYING_IN,
    PRESENTED,
    ANTICIPATION,
    SMASHING_DOWN,
    IMPACT
}

/**
 * The completion-presentation beat (docs/challenges.md): a just-finished challenge duplicates
 * from its slot, scales up to center stage with an ambient accent glow, bursts confetti once fully
 * settled, and on tap smashes down at high speed into the completed-challenges log, morphing into a
 * paper log entry with an impact shockwave. Renders nothing when [current] is null.
 */
@Composable
internal fun ChallengeCompletionOverlay(
    current: Challenge?,
    slotRect: Rect?,
    logAnchorRect: Rect?,
    isFirstCelebration: Boolean = true,
    onFlyInStart: () -> Unit = {},
    onCelebrated: (Int) -> Unit
) {
    if (current == null) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val centerXPx = with(density) { (maxWidth / 2).toPx() }
        val centerYPx = with(density) { (maxHeight / 2).toPx() }
        val baseSlotWidthPx = slotRect?.width ?: with(density) { ((maxWidth - (Spacing.Large * 2)) / 3).toPx() }

        // Middle size between previous card size (CardSizeDp = 220.dp) and typical modal width (maxWidth - 48.dp)
        val buttonHeightPx = with(density) { 50.dp.toPx() }
        val buttonGapPx = with(density) { 16.dp.toPx() }
        val maxAvailableCardHeightPx = with(density) { maxHeight.toPx() } - with(density) { 140.dp.toPx() } - buttonHeightPx - buttonGapPx
        val modalWidthPx = with(density) { maxWidth.toPx() } - with(density) { (Spacing.Large * 2).toPx() }
        val previousCardSizePx = with(density) { CardSizeDp.dp.toPx() }
        val targetSizePx = (previousCardSizePx + modalWidthPx) / 2f
        val cardSizePx = targetSizePx.coerceAtMost(maxAvailableCardHeightPx)

        val totalGroupHeightPx = cardSizePx + buttonGapPx + buttonHeightPx
        val groupTopPx = (centerYPx - totalGroupHeightPx / 2f).coerceAtLeast(with(density) { 40.dp.toPx() })

        val centerRect = remember(maxWidth, maxHeight, cardSizePx, groupTopPx) {
            Rect(
                left = centerXPx - cardSizePx / 2f,
                top = groupTopPx,
                right = centerXPx + cardSizePx / 2f,
                bottom = groupTopPx + cardSizePx
            )
        }
        val anticipationRect = remember(centerRect) {
            val extraPx = with(density) { 12.dp.toPx() }
            Rect(
                left = centerRect.left - extraPx,
                top = centerRect.top - extraPx - with(density) { 8.dp.toPx() },
                right = centerRect.right + extraPx,
                bottom = centerRect.bottom + extraPx - with(density) { 8.dp.toPx() }
            )
        }
        val fallbackLogAnchorRect = remember(maxWidth) {
            val marginPx = with(density) { Spacing.Medium.toPx() }
            val topPx = with(density) { 260.dp.toPx() }
            val heightPx = with(density) { 88.dp.toPx() }
            val widthPx = with(density) { maxWidth.toPx() } - marginPx * 2
            Rect(marginPx, topPx, marginPx + widthPx, topPx + heightPx)
        }
        val targetLogEntryRect = remember(logAnchorRect, maxWidth) {
            if (logAnchorRect != null) {
                val topPx = logAnchorRect.bottom + with(density) { Spacing.Small.toPx() }
                val heightPx = with(density) { 88.dp.toPx() }
                val marginPx = with(density) { Spacing.Medium.toPx() }
                val widthPx = with(density) { maxWidth.toPx() } - marginPx * 2
                Rect(marginPx, topPx, marginPx + widthPx, topPx + heightPx)
            } else {
                fallbackLogAnchorRect
            }
        }

        val currentSlotRect by rememberUpdatedState(slotRect)
        val startRect = currentSlotRect ?: centerRect

        var phase by remember(current.id) { mutableStateOf(CelebrationPhase.INITIAL_DELAY) }
        val fraction = remember(current.id) { Animatable(0f) }
        var showConfetti by remember(current.id) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(current.id) {
            phase = CelebrationPhase.INITIAL_DELAY
            showConfetti = false
            fraction.snapTo(0f)

            // Wait briefly if slotRect is still measuring
            if (currentSlotRect == null) {
                withTimeoutOrNull(120) {
                    snapshotFlow { currentSlotRect }.first { it != null }
                }
            }

            // 1. Initial delay so pilot clearly sees where they are and registers their completed slot
            val delayMs = if (isFirstCelebration) InitialDelayMs else 200L
            delay(delayMs)

            // 2. Start flying to center with dynamic arc & camera overshoot
            onFlyInStart()
            phase = CelebrationPhase.FLYING_IN
            val flyInJob = launch {
                fraction.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(FlyInDurationMs, easing = CubicBezierEasing(0.05f, 0.85f, 0.15f, 1f))
                )
            }

            // 3. Fire confetti and apex flash right as the card snaps into center stage (74% through flight)
            delay((FlyInDurationMs * 0.74f).toLong())
            showConfetti = true
            flyInJob.join()

            // 4. Settled at center stage
            phase = CelebrationPhase.PRESENTED
        }

        val t = fraction.value

        val animatedRect = when (phase) {
            CelebrationPhase.INITIAL_DELAY -> startRect
            CelebrationPhase.FLYING_IN -> lerpRect(startRect, centerRect, t)
            CelebrationPhase.PRESENTED -> centerRect
            CelebrationPhase.ANTICIPATION -> lerpRect(centerRect, anticipationRect, fraction.value)
            CelebrationPhase.SMASHING_DOWN -> lerpRect(anticipationRect, targetLogEntryRect, fraction.value)
            CelebrationPhase.IMPACT -> targetLogEntryRect
        }

        val scaleMultiplier = when (phase) {
            CelebrationPhase.FLYING_IN -> 1f + 0.10f * sin((t * Math.PI).toFloat())
            CelebrationPhase.ANTICIPATION -> 1f + 0.04f * fraction.value
            else -> 1f
        }

        val startOffsetX = startRect.center.x - centerRect.center.x
        val maxTiltDeg = (startOffsetX / centerXPx).coerceIn(-1f, 1f) * 6.5f
        val currentRotation = when (phase) {
            CelebrationPhase.FLYING_IN -> maxTiltDeg * (1f - t) * cos(t * Math.PI.toFloat() * 1.5f)
            CelebrationPhase.ANTICIPATION -> -1.5f * fraction.value
            else -> 0f
        }

        val morphProgress = when (phase) {
            CelebrationPhase.INITIAL_DELAY,
            CelebrationPhase.FLYING_IN,
            CelebrationPhase.PRESENTED,
            CelebrationPhase.ANTICIPATION -> 0f
            CelebrationPhase.SMASHING_DOWN -> fraction.value
            CelebrationPhase.IMPACT -> 1f
        }

        val cardAlpha = when (phase) {
            CelebrationPhase.INITIAL_DELAY -> 0f
            CelebrationPhase.FLYING_IN,
            CelebrationPhase.PRESENTED,
            CelebrationPhase.ANTICIPATION,
            CelebrationPhase.SMASHING_DOWN -> 1f
            CelebrationPhase.IMPACT -> 0f
        }

        val scrimAlpha = when (phase) {
            CelebrationPhase.INITIAL_DELAY -> 0f
            CelebrationPhase.FLYING_IN -> fraction.value * 0.7f
            CelebrationPhase.PRESENTED,
            CelebrationPhase.ANTICIPATION -> 0.7f
            CelebrationPhase.SMASHING_DOWN -> 0.7f * (1f - fraction.value)
            CelebrationPhase.IMPACT -> 0f
        }

        fun startClosingAnimation() {
            if (phase == CelebrationPhase.PRESENTED) {
                scope.launch {
                    // Anticipation: brief slight recoil before the smash
                    phase = CelebrationPhase.ANTICIPATION
                    fraction.snapTo(0f)
                    fraction.animateTo(1f, tween(AnticipationDurationMs, easing = FastOutSlowInEasing))

                    // High-speed smash into the log
                    phase = CelebrationPhase.SMASHING_DOWN
                    fraction.snapTo(0f)
                    fraction.animateTo(
                        1f,
                        tween(SmashDurationMs, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f))
                    )

                    // Impact shockwave & commit celebration
                    phase = CelebrationPhase.IMPACT
                    onCelebrated(current.id)
                    fraction.snapTo(0f)
                    fraction.animateTo(1f, tween(ImpactDurationMs, easing = FastOutSlowInEasing))
                }
            }
        }

        // Scrim - intercepts taps and initiates the smash into the log
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Midnight.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    startClosingAnimation()
                }
        )

        // ── Accent Glow around centered card (Square shape with rounded corners matching the card, native blur) ──
        val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
        val glowPulse by infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )

        val glowAlpha = when (phase) {
            CelebrationPhase.INITIAL_DELAY -> 0f
            CelebrationPhase.FLYING_IN -> fraction.value * glowPulse
            CelebrationPhase.PRESENTED,
            CelebrationPhase.ANTICIPATION -> glowPulse
            CelebrationPhase.SMASHING_DOWN -> (1f - fraction.value).coerceAtLeast(0f) * glowPulse
            CelebrationPhase.IMPACT -> 0f
        }

        val currentScale = (animatedRect.width / baseSlotWidthPx).coerceAtLeast(1f)
        val currentCornerRadius = ((16.dp * currentScale) * (1f - morphProgress) + 8.dp * morphProgress).coerceAtLeast(6.dp)

        if (glowAlpha > 0.01f) {
            val blurRadiusDp = (32 * (currentScale / 2.5f)).coerceIn(24f, 44f).dp
            val radiusPx = with(density) { currentCornerRadius.toPx() }
            val maxSpreadPx = with(density) { blurRadiusDp.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRect = animatedRect
                val steps = 14
                for (i in steps downTo 1) {
                    val progress = i.toFloat() / steps
                    val spread = progress * maxSpreadPx
                    val alphaFactor = (1f - progress) * (1f - progress)
                    val layerAlpha = (0.28f * glowAlpha * alphaFactor).coerceIn(0f, 1f)

                    drawRoundRect(
                        color = Amber.copy(alpha = layerAlpha),
                        topLeft = Offset(baseRect.left - spread, baseRect.top - spread),
                        size = Size(baseRect.width + spread * 2f, baseRect.height + spread * 2f),
                        cornerRadius = CornerRadius(radiusPx + spread)
                    )
                }

                // Crisp accent border directly outlining the card's edge
                drawRoundRect(
                    color = Amber.copy(alpha = 0.65f * glowAlpha),
                    topLeft = baseRect.topLeft,
                    size = baseRect.size,
                    cornerRadius = CornerRadius(radiusPx),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() })
                )
            }
        }

        // ── Apex Arrival Flash (radial bloom explosion at the apex punch) ──
        val apexFlashAlpha = remember(current.id) { Animatable(0f) }
        val apexFlashRadius = remember(current.id) { Animatable(0.4f) }
        LaunchedEffect(showConfetti) {
            if (showConfetti) {
                launch {
                    apexFlashAlpha.snapTo(0.90f)
                    apexFlashAlpha.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                }
                launch {
                    apexFlashRadius.snapTo(0.4f)
                    apexFlashRadius.animateTo(1.75f, tween(480, easing = FastOutSlowInEasing))
                }
            }
        }

        if (apexFlashAlpha.value > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(centerRect.center.x, centerRect.center.y)
                val baseRadius = centerRect.width * 0.55f
                val radius = baseRadius * apexFlashRadius.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Amber.copy(alpha = apexFlashAlpha.value * 0.80f),
                            ChallengeGold.copy(alpha = apexFlashAlpha.value * 0.40f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    center = center,
                    radius = radius
                )
            }
        }

        // ── Confetti Burst (fires right at apex punch) ──
        if (showConfetti) {
            RadialConfettiBurst(
                trigger = true,
                centerPx = Offset(centerRect.center.x, centerRect.center.y),
                key = current.id
            )
        }

        // ── The Card (Exact upscaled layout of slot, morphs on smash) ──
        CelebrationCard(
            challenge = current,
            rect = animatedRect,
            alpha = cardAlpha,
            rotationZ = currentRotation,
            scaleMultiplier = scaleMultiplier,
            baseSlotWidthPx = baseSlotWidthPx,
            morphProgress = morphProgress
        )

        // ── Floating "CONGRATS" button under the card (no modal background) ──
        val buttonAlpha = when (phase) {
            CelebrationPhase.PRESENTED -> 1f
            else -> 0f
        }
        val animatedButtonAlpha by animateFloatAsState(
            targetValue = buttonAlpha,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "congrats_button_alpha"
        )

        if (animatedButtonAlpha > 0.01f) {
            val buttonTopPx = animatedRect.bottom + buttonGapPx
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedRect.left.roundToInt(), buttonTopPx.roundToInt()) }
                    .size(
                        width = with(density) { animatedRect.width.toDp() },
                        height = with(density) { buttonHeightPx.toDp() }
                    )
                    .alpha(animatedButtonAlpha)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Amber)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        startClosingAnimation()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONGRATS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    ),
                    color = DeepNavy
                )
            }
        }

        // ── Impact Shockwave at log entry site ──
        if (phase == CelebrationPhase.IMPACT) {
            val impactT = fraction.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRect = targetLogEntryRect
                val expandPx = impactT * with(density) { 70.dp.toPx() }
                val rippleRect = Rect(
                    left = baseRect.left - expandPx,
                    top = baseRect.top - expandPx * 0.35f,
                    right = baseRect.right + expandPx,
                    bottom = baseRect.bottom + expandPx * 0.35f
                )
                val shockAlpha = (1f - impactT).coerceIn(0f, 1f)
                // Golden expanding shockwave ring
                drawRoundRect(
                    color = Amber.copy(alpha = shockAlpha * 0.8f),
                    topLeft = rippleRect.topLeft,
                    size = rippleRect.size,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = (4 * (1f - impactT)).dp.toPx())
                )
                // Flash on the impacted card
                drawRoundRect(
                    color = Color.White.copy(alpha = shockAlpha * 0.40f),
                    topLeft = baseRect.topLeft,
                    size = baseRect.size,
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }
        }
    }
}

/**
 * Exact quadratic duplicate of the slot card layout ([FilledSlot]), upscaled to center stage,
 * morphing into a logbook entry format when smashing down into the completed log.
 */
@Composable
private fun CelebrationCard(
    challenge: Challenge,
    rect: Rect,
    alpha: Float,
    baseSlotWidthPx: Float,
    morphProgress: Float = 0f,
    rotationZ: Float = 0f,
    scaleMultiplier: Float = 1f
) {
    if (alpha <= 0.001f) return

    val density = LocalDensity.current
    val slotLabelText = if (challenge.type == ChallengeType.SET_COMPLETION) {
        "${challenge.visitedSetMembers.size}/${challenge.setTotalMembers}"
    } else {
        "100%"
    }

    val dateStr = remember(challenge.completedAt, challenge.startedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(
            Date(challenge.completedAt ?: challenge.startedAt ?: System.currentTimeMillis())
        )
    }

    val widthDp = with(density) { rect.width.toDp() }
    val heightDp = with(density) { rect.height.toDp() }
    val scale = (rect.width / baseSlotWidthPx).coerceAtLeast(1f)

    // Corner radius proportionally scaled from the slot's 16.dp corner radius
    val cornerRadius = ((16.dp * scale) * (1f - morphProgress) + 8.dp * morphProgress).coerceAtLeast(6.dp)
    val bgColor = lerpColor(DeepNavy, LogbookParchment, morphProgress)

    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(width = widthDp, height = heightDp)
            .graphicsLayer {
                this.rotationZ = rotationZ
                this.scaleX = scaleMultiplier
                this.scaleY = scaleMultiplier
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
    ) {
        // ── Slot Layout (EXACT proportional match to FilledSlot, quadratic upscaled) ──
        if (morphProgress < 0.85f) {
            val slotAlpha = (1f - morphProgress * 1.5f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(slotAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Dimmed challenge icon watermark behind the ring - matches FilledSlot's Spacing.Large (24.dp)
                Icon(
                    imageVector = challenge.icon(),
                    contentDescription = null,
                    tint = Haze.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.Large * scale)
                )
                // Full circular progress ring in accent Amber - matches FilledSlot's Spacing.Small (8.dp) and 5.dp stroke
                RingProgress(
                    progress = 1f,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.Small * scale),
                    strokeWidth = 5.dp * scale,
                    fillColor = Amber
                ) {
                    val baseFontSize = when {
                        slotLabelText.length <= 3 -> 15f
                        slotLabelText.length == 4 -> 13f
                        slotLabelText.length == 5 -> 11.5f
                        else -> 10f
                    }
                    Text(
                        text = slotLabelText,
                        color = OffWhite,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp * scale),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (baseFontSize * scale).sp
                        )
                    )
                }
            }
        }

        // ── Logbook Paper Preview Layout (revealed as card smashes down) ──
        if (morphProgress > 0.25f) {
            val logAlpha = ((morphProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(logAlpha)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Red vertical margin line
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .background(LogbookMarginRed.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            color = LogbookInkFaint
                        )
                        Box(
                            modifier = Modifier
                                .border(1.dp, LogbookMarginRed.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = challengeTypeLabel(challenge.type),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp
                                ),
                                color = LogbookMarginRed.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Text(
                        text = challenge.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = LogbookInkDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun lerpRect(start: Rect, end: Rect, t: Float): Rect = Rect(
    left = start.left + (end.left - start.left) * t,
    top = start.top + (end.top - start.top) * t,
    right = start.right + (end.right - start.right) * t,
    bottom = start.bottom + (end.bottom - start.bottom) * t
)

private data class BurstParticle(
    val angleRad: Float,
    val velocityPx: Float,
    val fallDurationMs: Int,
    val delayMs: Int,
    val color: Color,
    val sizePx: Float,
    val rotationSpeedDegPerMs: Float
)

private val ConfettiColors = listOf(
    ChallengeGold,
    Amber,
    Green,
    OffWhite,
    Color(0xFFFFD54F),
    Color(0xFFFFB74D)
)

/**
 * Adapted from `ChallengeOutcomeScreen`'s `ConfettiOverlay` - same `Animatable`-driven,
 * particle-data-class-plus-`remember`, single-`Canvas`-with-`rotate` approach, but launched
 * outward from [centerPx] in every direction instead of falling from off-screen. [key] resets the
 * particle set and re-fires the burst for a new challenge (that composable isn't reused directly
 * here since it's `private` to its own file and shaped for full-screen rain, not a burst behind a
 * card).
 */
@Composable
private fun RadialConfettiBurst(trigger: Boolean, centerPx: Offset, key: Any) {
    if (!trigger) return

    val particles = remember(key) {
        List(75) { i ->
            val baseAngle = (i.toFloat() / 75f) * (2f * Math.PI.toFloat())
            val jitter = (Random.nextFloat() - 0.5f) * 0.35f
            BurstParticle(
                angleRad = baseAngle + jitter,
                velocityPx = Random.nextInt(320, 800).toFloat(),
                fallDurationMs = Random.nextInt(1000, ConfettiBurstDurationMs),
                delayMs = Random.nextInt(0, 140),
                color = ConfettiColors[Random.nextInt(ConfettiColors.size)],
                sizePx = Random.nextInt(6, 15).toFloat(),
                rotationSpeedDegPerMs = Random.nextFloat() * 0.8f - 0.4f
            )
        }
    }
    val elapsedMs = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        elapsedMs.snapTo(0f)
        elapsedMs.animateTo(
            targetValue = ConfettiBurstDurationMs.toFloat(),
            animationSpec = tween(durationMillis = ConfettiBurstDurationMs, easing = LinearEasing)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = elapsedMs.value
        particles.forEach { p ->
            val localElapsed = now - p.delayMs
            if (localElapsed < 0f) return@forEach
            val t = (localElapsed / p.fallDurationMs).coerceIn(0f, 1f)
            if (t >= 1f) return@forEach // Fully finished and faded out

            // Eases outward then settles
            val outwardEase = 1f - (1f - t) * (1f - t)
            val travel = p.velocityPx * outwardEase

            // Gravity gently pulls the confetti down
            val gravityDrop = 120f * t * t

            val x = centerPx.x + cos(p.angleRad) * travel
            val y = centerPx.y + sin(p.angleRad) * travel + gravityDrop

            // Smooth fadeout in the second half of life down to 0
            val alpha = if (t < 0.45f) {
                1f
            } else {
                (1f - (t - 0.45f) / 0.55f).coerceIn(0f, 1f)
            }
            if (alpha <= 0.01f) return@forEach

            rotate(degrees = localElapsed * p.rotationSpeedDegPerMs, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.sizePx / 2f, y - p.sizePx / 2f),
                    size = Size(p.sizePx, p.sizePx * 1.6f)
                )
            }
        }
    }
}
