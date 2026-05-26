package com.genzopia.Instagame.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val OrangeAccent = Color(0xFFFF6B35)
private val PurpleAccent = Color(0xFF7B2FFF)
private val GoldColor = Color(0xFFFFD700)

/**
 * "Well Done" completion screen with:
 *  • Confetti particle burst
 *  • Pop-in card animation
 *  • Three feature highlight chips
 *  • Countdown progress bar before auto-dismiss
 */
@Composable
fun TutorialCompletionScreen(
    onDismiss: () -> Unit,
    autoDismissMs: Long = 4000L
) {
    // ── Countdown progress (1.0 → 0.0 over autoDismissMs) ────────────────────
    var countdownProgress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        val steps = 100
        val stepMs = autoDismissMs / steps
        repeat(steps) {
            delay(stepMs)
            countdownProgress = 1f - (it + 1f) / steps
        }
        onDismiss()
    }

    // ── Card pop-in ───────────────────────────────────────────────────────────
    val cardScale = remember { Animatable(0.7f) }
    val cardAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        cardAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        cardScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    // ── Star pulse ────────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "completion")
    val starScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_scale"
    )
    val starRotation by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_rot"
    )

    // ── Confetti animation driver ─────────────────────────────────────────────
    val confettiProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti"
    )

    // Pre-generate stable confetti particles
    val particles = remember {
        List(40) {
            ConfettiParticle(
                angle = Random.nextFloat() * 360f,
                speed = Random.nextFloat() * 0.4f + 0.3f,
                size = Random.nextFloat() * 8f + 4f,
                color = listOf(
                    OrangeAccent, PurpleAccent, GoldColor,
                    Color(0xFF00E5FF), Color(0xFF69FF47), Color(0xFFFF4081)
                ).random(),
                startDelay = Random.nextFloat() * 0.5f,
                rotationSpeed = Random.nextFloat() * 720f - 360f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        // ── Confetti layer ────────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.35f   // burst origin — above the card

            particles.forEach { p ->
                val t = ((confettiProgress - p.startDelay).coerceAtLeast(0f) / (1f - p.startDelay))
                    .coerceIn(0f, 1f)
                val dist = t * p.speed * size.width * 0.55f
                val x = cx + cos(Math.toRadians(p.angle.toDouble())).toFloat() * dist
                val y = cy + sin(Math.toRadians(p.angle.toDouble())).toFloat() * dist +
                        t * t * size.height * 0.15f  // gravity
                val alpha = (1f - t * 1.2f).coerceIn(0f, 1f)

                rotate(degrees = p.rotationSpeed * t, pivot = Offset(x, y)) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size / 2f)
                    )
                }
            }
        }

        // ── Card ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .scale(cardScale.value)
                .alpha(cardAlpha.value)
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF12122A))
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Star badge
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(starScale)
                    .background(OrangeAccent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(OrangeAccent.copy(alpha = 0.08f), CircleShape)
                )
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = GoldColor,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Well Done! 🎉",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You've completed the tutorial",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Enjoy the games! 🎮",
                color = OrangeAccent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Feature chips ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureChip(emoji = "📱", text = "Swipe up to discover new games")
                FeatureChip(emoji = "👆👆", text = "Double-tap any reel to play")
                FeatureChip(emoji = "❤️", text = "Like & follow your favourites")
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── CTA button ────────────────────────────────────────────────────
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Let's Play!",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Countdown bar ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Closing automatically…",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(countdownProgress)
                            .height(3.dp)
                            .background(OrangeAccent.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureChip(emoji: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val startDelay: Float,
    val rotationSpeed: Float
)
