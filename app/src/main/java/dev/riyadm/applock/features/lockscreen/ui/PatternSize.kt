package dev.riyadm.applock.features.lockscreen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.data.repository.HardeningPreferences

/** Largest side of the pattern grid for the "Pattern size" setting. Hit targets scale with it. */
fun patternMaxSize(size: String): Dp = when (size) {
    HardeningPreferences.PATTERN_SMALL -> 260.dp
    HardeningPreferences.PATTERN_LARGE -> 380.dp
    else -> 320.dp
}

@Composable
internal fun rememberPatternMaxSize(): Dp {
    val context = LocalContext.current
    return remember(context) { patternMaxSize(HardeningPreferences(context).patternSize) }
}
