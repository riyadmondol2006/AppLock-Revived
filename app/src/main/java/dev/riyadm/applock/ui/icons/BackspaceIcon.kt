package dev.riyadm.applock.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Backspace: ImageVector = ImageVector.Builder(
    name = "Backspace",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color(0xFF0F172A)),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(12f, 9.75f)
        lineTo(14.25f, 12f)
        moveTo(14.25f, 12f)
        lineTo(16.5f, 14.25f)
        moveTo(14.25f, 12f)
        lineTo(16.5f, 9.75f)
        moveTo(14.25f, 12f)
        lineTo(12f, 14.25f)
        moveTo(9.42051f, 19.1705f)
        lineTo(3.04551f, 12.7955f)
        curveTo(2.60617f, 12.3562f, 2.60617f, 11.6438f, 3.04551f, 11.2045f)
        lineTo(9.42051f, 4.82951f)
        curveTo(9.63149f, 4.61853f, 9.91764f, 4.5f, 10.216f, 4.5f)
        lineTo(19.5f, 4.5f)
        curveTo(20.7427f, 4.5f, 21.75f, 5.50736f, 21.75f, 6.75f)
        verticalLineTo(17.25f)
        curveTo(21.75f, 18.4926f, 20.7427f, 19.5f, 19.5f, 19.5f)
        horizontalLineTo(10.216f)
        curveTo(9.91764f, 19.5f, 9.63149f, 19.3815f, 9.42051f, 19.1705f)
        close()
    }
}.build()
