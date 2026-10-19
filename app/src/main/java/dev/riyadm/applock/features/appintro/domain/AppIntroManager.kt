package dev.riyadm.applock.features.appintro.domain

import android.content.Context
import androidx.core.content.edit

object AppIntroManager {
    private const val PREF_APP_INTRO =
        "app_prefs"
    private const val PREF_INTRO_SHOWN = "intro_shown"

    fun shouldShowIntro(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREF_APP_INTRO, Context.MODE_PRIVATE)
        return !sharedPrefs.getBoolean(PREF_INTRO_SHOWN, false)
    }

    fun markIntroAsCompleted(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREF_APP_INTRO, Context.MODE_PRIVATE)
        sharedPrefs.edit { putBoolean(PREF_INTRO_SHOWN, true) }
    }
}

