package com.genzopia.Instagame.onboarding

import ReelViewModel
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.genzopia.Instagame.reelview.compose.ReelScreen
import com.google.firebase.auth.FirebaseAuth

/**
 * Wraps [ReelScreen] and shows the one-time onboarding tutorial overlay on top.
 *
 * Tutorial flow:
 *  1. SCROLL step  — animated arrow; user swipes → reel actually scrolls to next page.
 *  2. DOUBLE-TAP step — ripple indicator; user double-taps → game actually launches.
 *  3. COMPLETION screen — "Well Done! Enjoy the games 🎮" card auto-dismisses after 3 s.
 */
@Composable
fun OnboardingTutorialHost(viewModel: ReelViewModel) {
    val context: Context = LocalContext.current
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val controller = remember { TutorialController(context) }

    // Eligibility: null uid → never show; absent/false key → show
    val shouldShow = remember(uid) {
        uid != null && controller.shouldShowTutorial(uid)
    }

    // ── Tutorial state ────────────────────────────────────────────────────────
    var tutorialVisible by remember { mutableStateOf(shouldShow) }
    var currentStep by remember { mutableStateOf<TutorialStep>(TutorialStep.Scroll) }
    var showCompletion by remember { mutableStateOf(false) }

    // ── Scroll action registered by ReelScreen once its pager is ready ────────
    // OnboardingOverlay calls scrollToNext() when the user swipes during Scroll step.
    var scrollToNext by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ── Current reel's gameId (updated by ReelScreen on every page change) ────
    var currentGameId by remember { mutableStateOf("") }

    // ── Lifecycle: re-show overlay when user returns to this screen ──────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-read SharedPreferences directly — never rely on the stale
                // `shouldShow` snapshot. If the user completed it (e.g. just
                // returned from the launched game), isComplete() returns true
                // and we leave the overlay hidden.
                if (uid != null && !TutorialController.isComplete(context, uid)) {
                    tutorialVisible = true
                    currentStep = TutorialStep.Scroll
                }
                // If already complete: ensure overlay stays hidden
                if (uid != null && TutorialController.isComplete(context, uid)) {
                    tutorialVisible = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Reel feed ─────────────────────────────────────────────────────────
        ReelScreen(
            viewModel = viewModel,
            onScrollActionReady = { action -> scrollToNext = action },
            onCurrentReelChanged = { gameId -> currentGameId = gameId }
        )

        // ── Tutorial overlay (Scroll + DoubleTap steps) ───────────────────────
        AnimatedVisibility(
            visible = tutorialVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            OnboardingOverlay(
                step = currentStep,

                // Scroll step: advance step AND actually scroll the reel
                onScrollStepAdvance = {
                    scrollToNext?.invoke()              // animate pager to next page
                    currentStep = TutorialStep.DoubleTap
                },

                // Double-tap step: mark complete FIRST (sync write), then launch game
                onDoubleTapComplete = {
                    // Write synchronously so isComplete() returns true immediately,
                    // even before ON_RESUME fires when returning from the game.
                    val written = uid?.let { controller.markComplete(it) } ?: false
                    if (!written) {
                        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("onboarding_write_pending_$uid", true)
                            .apply()
                    }
                    // Hide overlay before launching so ON_RESUME sees it as hidden
                    tutorialVisible = false
                    showCompletion = true

                    if (currentGameId.isNotEmpty()) {
                        val intent = Intent(
                            context,
                            com.genzopia.Instagame.webgl_gameloading.Game_mode::class.java
                        ).apply { putExtra("game_id", currentGameId) }
                        context.startActivity(intent)
                    }
                },

                // Single tap during DoubleTap step: do nothing — user must double-tap to complete
                onSingleTapDismiss = {
                    // no-op: overlay stays visible, navigation is still blocked
                },

                // 30-second inactivity: reset to scroll step so user sees guidance again
                onTimeout = {
                    currentStep = TutorialStep.Scroll
                    tutorialVisible = true
                }
            )
        }

        // ── Completion screen ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showCompletion,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            TutorialCompletionScreen(
                onDismiss = { showCompletion = false }
            )
        }
    }
}
