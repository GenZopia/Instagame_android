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
            val written = prefs.edit().putBoolean(completedKey, true).commit()
            if (written) prefs.edit().remove(pendingKey).apply()
            return false
        }
        return !prefs.getBoolean(completedKey, false)
    }

    fun markComplete(uid: String): Boolean {
        val key = "onboarding_tutorial_completed_$uid"
        return prefs.edit().putBoolean(key, true).commit()
    }

    companion object {
        /** Quick check usable from any context (e.g. MainActivity) without full instantiation. */
        @JvmStatic
        fun isComplete(context: Context, uid: String): Boolean {
            val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("onboarding_tutorial_completed_$uid", false)
        }
    }
}
