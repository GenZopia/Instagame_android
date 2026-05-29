package com.genzopia.Instagame.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Scroll step hint — three animated upward arrows with a hand illustration,
 * instructional text, and a glowing trail effect.
 *
 * Visual hierarchy:
 *   • "Step 1 of 2" pill at top
 *   • Instructional text card in the middle
 *   • Three cascading arrows at the bottom third of the screen
 */
@Composable
fun ScrollHintArrow() {
    val infiniteTransition = rememberInfiniteTransition(label = "scroll_hint")

    // Master wave offset — drives the cascading arrow animation
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    // Glow pulse on the instruction card
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Entry animation
    val entryAlpha = remember { Animatable(0f) }
    val entryOffset = remember { Animatable(30f) }
    LaunchedEffect(Unit) {
        entryAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        entryOffset.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Scroll hint arrow" }
            .alpha(entryAlpha.value)
    ) {
        // ── Instruction card — upper-center ──────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Instruction card with glowing border
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF6B35).copy(alpha = glowAlpha),
                                    Color(0xFFFFD700).copy(alpha = glowAlpha * 0.6f),
                                    Color(0xFFFF6B35).copy(alpha = glowAlpha)
                                )
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Hand + swipe icon row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "👆",
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color(0xFFFF6B35),
                                modifier = Modifier.size(20.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color(0xFFFF6B35).copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Swipe up to explore",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Scroll through game reels\njust like Instagram",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // ── Cascading arrows — bottom third ──────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-8).dp)
        ) {
            // Arrow 1 — top (faintest, leads the wave)
            val a1Alpha = cascadeAlpha(waveOffset, phase = 0.0f)
            val a1Offset = cascadeOffset(waveOffset, phase = 0.0f)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = a1Alpha * 0.35f),
                modifier = Modifier
                    .size(28.dp)
                    .alpha(a1Alpha)
                    .padding(bottom = a1Offset.dp)
            )

            // Arrow 2 — middle
            val a2Alpha = cascadeAlpha(waveOffset, phase = 0.25f)
            val a2Offset = cascadeOffset(waveOffset, phase = 0.25f)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = a2Alpha * 0.65f),
                modifier = Modifier
                    .size(36.dp)
                    .alpha(a2Alpha)
                    .padding(bottom = a2Offset.dp)
            )

            // Arrow 3 — bottom (brightest, anchors the wave)
            val a3Alpha = cascadeAlpha(waveOffset, phase = 0.5f)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color(0xFFFF6B35).copy(alpha = a3Alpha),
                modifier = Modifier
                    .size(48.dp)
                    .alpha(a3Alpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // "swipe up to continue" hint
            Text(
                text = "swipe up to continue",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/** Maps a wave phase [0,1] to an alpha value using a sine-like curve. */
private fun cascadeAlpha(wave: Float, phase: Float): Float {
    val shifted = (wave + phase) % 1f
    // Peaks at 0.5, fades at 0 and 1
    return (kotlin.math.sin(shifted * Math.PI.toFloat()) * 0.8f + 0.2f).coerceIn(0.2f, 1f)
}

/** Maps a wave phase to a small vertical offset (0–4 dp) for subtle movement. */
private fun cascadeOffset(wave: Float, phase: Float): Float {
    val shifted = (wave + phase) % 1f
    return (1f - kotlin.math.sin(shifted * Math.PI.toFloat())) * 4f
}
