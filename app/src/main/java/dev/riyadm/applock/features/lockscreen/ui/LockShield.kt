package dev.riyadm.applock.features.lockscreen.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.doOnPreDraw
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.ui.theme.AppearanceSettings

/**
 * The opaque "shield" a lock window shows in its very first frame, before the Compose lock
 * screen has been composed and measured, so nothing of the locked app can show through.
 */
internal object LockShield {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Upper bound for waiting on the shield frame before the lock UI is attached anyway. */
    const val ATTACH_FALLBACK_MS = 100L

    /** The lock screen's surface colour, resolved the same way as [dev.riyadm.applock.ui.theme.AppLockTheme]. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun surfaceColor(context: Context): Int {
        AppearanceSettings.ensureLoaded(context)
        val dark = when (AppearanceSettings.themeMode) {
            PreferencesRepository.THEME_LIGHT -> false
            PreferencesRepository.THEME_DARK -> true
            else -> context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        if (dark && AppearanceSettings.amoledBlack) return android.graphics.Color.BLACK
        val dynamic = AppearanceSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val scheme = when {
            dynamic && dark -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> expressiveLightColorScheme()
        }
        return scheme.surface.toArgb()
    }

    /**
     * Runs [action] once on the main thread right after this view's first frame has been handed
     * to the compositor (or after [fallbackMs] if that signal never comes), passing whether the
     * frame-commit signal was seen.
     */
    fun afterFirstFrame(
        view: View,
        fallbackMs: Long = ATTACH_FALLBACK_MS,
        action: (committed: Boolean) -> Unit
    ) {
        var done = false
        lateinit var fallback: Runnable
        fun run(committed: Boolean) {
            if (done) return
            done = true
            mainHandler.removeCallbacks(fallback)
            action(committed)
        }
        fallback = Runnable { run(false) }
        view.doOnPreDraw {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.viewTreeObserver.registerFrameCommitCallback { run(true) }
            } else {
                view.post { run(true) }
            }
        }
        mainHandler.postDelayed(fallback, fallbackMs)
    }
}
