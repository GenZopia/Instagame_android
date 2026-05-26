package com.genzopia.Instagame.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
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
 * Double-tap step hint.
 *
 * Shows:
 *  • An instruction card with a game controller icon and clear copy
 *  • A two-pulse ripple animation that visually demonstrates the double-tap gesture
 *  • A "tap count" indicator (● ●) that lights up sequentially to show "tap twice"
 *  • The game play icon in the center of the ripple
 */
@Composable
fun DoubleTapHintIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "double_tap_hint")

    // ── Ripple 1 — first tap ──────────────────────────────────────────────────
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1_scale"
    )
    val ripple1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1_alpha"
    )

    // ── Ripple 2 — second tap (delayed by 500 ms) ─────────────────────────────
    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_scale"
    )
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_alpha"
    )

    // ── Center icon pulse ─────────────────────────────────────────────────────
    val centerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center_scale"
    )

    // ── Tap dot indicator (● ●) — lights up sequentially ─────────────────────
    var activeDot by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDot = 0; delay(300)
            activeDot = 1; delay(300)
            activeDot = 2; delay(800)   // pause before repeating
        }
    }

    // ── Glow on instruction card ──────────────────────────────────────────────
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "card_glow"
    )

    // ── Entry animation ───────────────────────────────────────────────────────
    val entryAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(entryAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {

            // ── Instruction card ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF7B2FFF).copy(alpha = glowAlpha),
                                    Color(0xFFFF6B35).copy(alpha = glowAlpha * 0.7f),
                                    Color(0xFF7B2FFF).copy(alpha = glowAlpha)
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
                    Text(text = "🎮", fontSize = 36.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Double-tap to play",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap twice on any reel\nto launch its game",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Double-tap ripple demo ────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .semantics { contentDescription = "Double-tap hint indicator" }
            ) {
                // Ripple 1 (first tap)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(ripple1Scale)
                        .alpha(ripple1Alpha)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF7B2FFF).copy(alpha = 0.8f),
                                    Color(0xFF7B2FFF).copy(alpha = 0f)
                                )
                            ),
                            CircleShape
                        )
                )

                // Ripple 2 (second tap — offset in time)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(ripple2Scale)
                        .alpha(ripple2Alpha)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF6B35).copy(alpha = 0.8f),
                                    Color(0xFFFF6B35).copy(alpha = 0f)
                                )
                            ),
                            CircleShape
                        )
                )

                // Center circle with play icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(centerScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF7B2FFF),
                                    Color(0xFFFF6B35)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tap dot indicator ─────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "tap",
                    color = Color.White.copy(alpha = if (activeDot >= 1) 1f else 0.35f),
                    fontSize = 13.sp,
                    fontWeight = if (activeDot >= 1) FontWeight.Bold else FontWeight.Normal
                )
                // Dot 1
                Box(
                    modifier = Modifier
                        .size(if (activeDot >= 1) 10.dp else 8.dp)
                        .background(
                            color = if (activeDot >= 1) Color(0xFF7B2FFF) else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
                // Dot 2
                Box(
                    modifier = Modifier
                        .size(if (activeDot >= 2) 10.dp else 8.dp)
                        .background(
                            color = if (activeDot >= 2) Color(0xFFFF6B35) else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
                Text(
                    text = "tap",
                    color = Color.White.copy(alpha = if (activeDot >= 2) 1f else 0.35f),
                    fontSize = 13.sp,
                    fontWeight = if (activeDot >= 2) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
