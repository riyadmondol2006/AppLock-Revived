package dev.riyadm.applock.core.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val shapes = mutableListOf(
    MaterialShapes.Triangle,
    MaterialShapes.Pentagon,
    MaterialShapes.Circle,
    MaterialShapes.Arrow,
    MaterialShapes.Pill,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Heart,
    MaterialShapes.PixelTriangle,
    MaterialShapes.PixelCircle,
    MaterialShapes.Gem
).apply { shuffle() }

