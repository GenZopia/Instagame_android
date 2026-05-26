package com.genzopia.Instagame.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private val OrangeAccent = Color(0xFFFF6B35)
private val PurpleAccent = Color(0xFF7B2FFF)

@Composable
fun OnboardingOverlay(
    step: TutorialStep,
    onScrollStepAdvance: () -> Unit,
    onDoubleTapComplete: () -> Unit,
    onSingleTapDismiss: () -> Unit,
    onTimeout: () -> Unit
) {
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val resetTimer = { lastInteractionMs = System.currentTimeMillis() }

    // 30-second inactivity auto-dismiss (Requirement 6.1)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            if (System.currentTimeMillis() - lastInteractionMs >= 30_000) {
                onTimeout()
                break
            }
        }
    }

    // Guard: only fire onScrollStepAdvance once per swipe gesture
    var scrollAdvanceFired by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }

    // Step index for progress bar
    val stepIndex = when (step) {
        is TutorialStep.Scroll -> 0
        is TutorialStep.DoubleTap -> 1
    }
    val totalSteps = 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .semantics { contentDescription = "Onboarding tutorial overlay" }
            // ── Tap / double-tap handler ──────────────────────────────────────
            .pointerInput(step) {
                detectTapGestures(
                    onTap = {
                        resetTimer()
                        when (step) {
                            is TutorialStep.Scroll -> onScrollStepAdvance()
                            is TutorialStep.DoubleTap -> onSingleTapDismiss()
                        }
                    },
                    onDoubleTap = {
                        resetTimer()
                        if (step is TutorialStep.DoubleTap) onDoubleTapComplete()
                    }
                )
            }
            // ── Swipe passthrough for Scroll step ─────────────────────────────
            .then(
                if (step is TutorialStep.Scroll)
                    Modifier.pointerInput("scroll_detect") {
                        detectVerticalDragGestures(
                            onDragStart = {
                                scrollAdvanceFired = false
                                resetTimer()
                            },
                            onVerticalDrag = { _, dragAmount ->
                                // Upward swipe threshold — do NOT consume so pager scrolls too
                                if (!scrollAdvanceFired && abs(dragAmount) >= swipeThresholdPx) {
                                    scrollAdvanceFired = true
                                    onScrollStepAdvance()
                                }
                            }
                        )
                    }
                else Modifier   // DoubleTap step: block all swipes
            )
    ) {
        // ── Top bar: step progress + skip ────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Step label
                Text(
                    text = "Step ${stepIndex + 1} of $totalSteps",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                // Skip button
                TextButton(onClick = {
                    resetTimer()
                    onSingleTapDismiss()
                }) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Segmented progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(totalSteps) { index ->
                    val isActive = index == stepIndex
                    val isDone = index < stepIndex
                    val barColor = when {
                        isDone -> OrangeAccent
                        isActive -> OrangeAccent
                        else -> Color.White.copy(alpha = 0.25f)
                    }
                    // Animate the active bar width
                    val widthFraction = remember(isActive) { Animatable(if (isActive) 0f else 1f) }
                    LaunchedEffect(isActive) {
                        if (isActive) widthFraction.animateTo(
                            1f,
                            tween(300, easing = FastOutSlowInEasing)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isActive) widthFraction.value else if (isDone) 1f else 0f)
                                .height(3.dp)
                                .background(barColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // ── Step content with crossfade ───────────────────────────────────────
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 })
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 8 })
            },
            label = "step_content"
        ) { currentStep ->
            when (currentStep) {
                is TutorialStep.Scroll -> ScrollHintArrow()
                is TutorialStep.DoubleTap -> DoubleTapHintIndicator()
            }
        }

        // ── Step dots at bottom ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                val isActive = index == stepIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 10.dp else 6.dp)
                        .background(
                            color = if (isActive) OrangeAccent else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
