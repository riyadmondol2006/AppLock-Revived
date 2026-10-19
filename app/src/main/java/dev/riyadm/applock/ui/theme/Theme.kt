package dev.riyadm.applock.ui.theme

import android.content.Context
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.PreferencesRepository

/**
 * Observable appearance settings. Screens that change appearance should update
 * both the repository and this holder (see [update]) so every window recomposes.
 */
object AppearanceSettings {
    var themeMode by mutableStateOf(PreferencesRepository.THEME_SYSTEM)
        private set
    var dynamicColor by mutableStateOf(true)
        private set
    var amoledBlack by mutableStateOf(false)
        private set

    private var loaded = false

    fun load(repository: AppLockRepository) {
        themeMode = repository.getThemeMode()
        dynamicColor = repository.isDynamicColorEnabled()
        amoledBlack = repository.isAmoledBlackEnabled()
        loaded = true
    }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        load(AppLockRepository(context.applicationContext))
    }

    fun update(
        repository: AppLockRepository,
        themeMode: String = this.themeMode,
        dynamicColor: Boolean = this.dynamicColor,
        amoledBlack: Boolean = this.amoledBlack
    ) {
        repository.setThemeMode(themeMode)
        repository.setDynamicColorEnabled(dynamicColor)
        repository.setAmoledBlackEnabled(amoledBlack)
        this.themeMode = themeMode
        this.dynamicColor = dynamicColor
        this.amoledBlack = amoledBlack
    }
}

@Composable
fun isAppInDarkTheme(): Boolean = when (AppearanceSettings.themeMode) {
    PreferencesRepository.THEME_LIGHT -> false
    PreferencesRepository.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLockTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    AppearanceSettings.ensureLoaded(context)

    val useDark = darkTheme ?: isAppInDarkTheme()
    val useDynamic = (dynamicColor ?: AppearanceSettings.dynamicColor) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var colorScheme = when {
        useDynamic && useDark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        useDark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    if (useDark && AppearanceSettings.amoledBlack) colorScheme = colorScheme.toAmoled()

    val shapes = Shapes(largeIncreased = RoundedCornerShape(36.0.dp))

    // enableEdgeToEdge() follows the system dark mode; keep bar icons readable when the
    // in-app theme differs from it.
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null && !view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = shapes,
        content = content,
        motionScheme = MotionScheme.expressive()
    )
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
