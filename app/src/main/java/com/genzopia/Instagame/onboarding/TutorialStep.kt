package com.genzopia.Instagame.onboarding

sealed class TutorialStep {
    object Scroll : TutorialStep()
    object DoubleTap : TutorialStep()
}
