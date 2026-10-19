package dev.riyadm.applock.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.riyadm.applock.R

val default = Typography()

/**
 * Google Sans Flex (variable: wght, ROND, opsz) — the Material 3 Expressive typeface.
 * Each style gets its own instance so weight, roundness and optical size match its role.
 */
@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(weight: Int, sizeSp: Float, roundness: Float = 0f) = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("ROND", roundness),
            FontVariation.Setting("opsz", sizeSp.coerceIn(6f, 144f)),
        )
    )
)

private fun TextStyle.withFlex(emphasized: Boolean = false): TextStyle {
    val base = fontWeight?.weight ?: 400
    val weight = if (emphasized) (base + 200).coerceAtMost(800) else base
    val roundness = if (emphasized) 100f else 0f
    return copy(
        fontFamily = googleSansFlex(weight, fontSize.value, roundness),
        fontWeight = FontWeight(weight)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography = Typography(
    displayLarge = default.displayLarge.withFlex(),
    displayMedium = default.displayMedium.withFlex(),
    displaySmall = default.displaySmall.withFlex(),
    headlineLarge = default.headlineLarge.withFlex(),
    headlineMedium = default.headlineMedium.withFlex(),
    headlineSmall = default.headlineSmall.withFlex(),
    titleLarge = default.titleLarge.withFlex(),
    titleMedium = default.titleMedium.withFlex(),
    titleSmall = default.titleSmall.withFlex(),
    bodyLarge = default.bodyLarge.withFlex(),
    bodyMedium = default.bodyMedium.withFlex(),
    bodySmall = default.bodySmall.withFlex(),
    labelLarge = default.labelLarge.withFlex(),
    labelMedium = default.labelMedium.withFlex(),
    labelSmall = default.labelSmall.withFlex(),
    displayLargeEmphasized = default.displayLarge.withFlex(emphasized = true),
    displayMediumEmphasized = default.displayMedium.withFlex(emphasized = true),
    displaySmallEmphasized = default.displaySmall.withFlex(emphasized = true),
    headlineLargeEmphasized = default.headlineLarge.withFlex(emphasized = true),
    headlineMediumEmphasized = default.headlineMedium.withFlex(emphasized = true),
    headlineSmallEmphasized = default.headlineSmall.withFlex(emphasized = true),
    titleLargeEmphasized = default.titleLarge.withFlex(emphasized = true),
    titleMediumEmphasized = default.titleMedium.withFlex(emphasized = true),
    titleSmallEmphasized = default.titleSmall.withFlex(emphasized = true),
    bodyLargeEmphasized = default.bodyLarge.withFlex(emphasized = true),
    bodyMediumEmphasized = default.bodyMedium.withFlex(emphasized = true),
    bodySmallEmphasized = default.bodySmall.withFlex(emphasized = true),
    labelLargeEmphasized = default.labelLarge.withFlex(emphasized = true),
    labelMediumEmphasized = default.labelMedium.withFlex(emphasized = true),
    labelSmallEmphasized = default.labelSmall.withFlex(emphasized = true),
)
