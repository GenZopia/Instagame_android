package com.genzopia.Instagame.onboarding

import android.content.Context
import android.content.SharedPreferences

class TutorialController(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    fun shouldShowTutorial(uid: String): Boolean {
        val completedKey = "onboarding_tutorial_completed_$uid"
        val pendingKey   = "onboarding_write_pending_$uid"

        if (prefs.getBoolean(pendingKey, false)) {
            // Retry the write
            val written = prefs.edit().putBoolean(completedKey, true).commit()
            if (written) prefs.edit().remove(pendingKey).apply()
            return false // treat as complete regardless of retry outcome
        }
        return !prefs.getBoolean(completedKey, false)
    }

    fun markComplete(uid: String): Boolean {
        val key = "onboarding_tutorial_completed_$uid"
        return prefs.edit().putBoolean(key, true).commit()
    }
}
