package dev.riyadm.applock.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Display = ImageVector.Builder(
    name = "Display",
    defaultWidth = 16.dp,
    defaultHeight = 16.dp,
    viewportWidth = 16f,
    viewportHeight = 16f
).apply {
    path(
        fill = SolidColor(Color.Black)
    ) {
        moveTo(0f, 4f)
        reflectiveCurveToRelative(0f, -2f, 2f, -2f)
        horizontalLineToRelative(12f)
        reflectiveCurveToRelative(2f, 0f, 2f, 2f)
        verticalLineToRelative(6f)
        reflectiveCurveToRelative(0f, 2f, -2f, 2f)
        horizontalLineToRelative(-4f)
        quadToRelative(0f, 1f, 0.25f, 1.5f)
        horizontalLineTo(11f)
        arcToRelative(
            0.5f, 0.5f, 0f,
            isMoreThanHalf = false,
            isPositiveArc = true,
            dx1 = 0f,
            dy1 = 1f
        )
        horizontalLineTo(5f)
        arcToRelative(
            0.5f, 0.5f, 0f,
            isMoreThanHalf = false,
            isPositiveArc = true,
            dx1 = 0f,
            dy1 = -1f
        )
        horizontalLineToRelative(0.75f)
        quadTo(6f, 13f, 6f, 12f)
        horizontalLineTo(2f)
        reflectiveCurveToRelative(-2f, 0f, -2f, -2f)
        close()
        moveToRelative(1.398f, -0.855f)
        arcToRelative(
            0.76f,
            0.76f,
            0f,
            isMoreThanHalf = false,
            isPositiveArc = false,
            dx1 = -0.254f,
            dy1 = 0.302f
        )
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 1f, y1 = 4.01f)
        verticalLineTo(10f)
        curveToRelative(0f, 0.325f, 0.078f, 0.502f, 0.145f, 0.602f)
        quadToRelative(0.105f, 0.156f, 0.302f, 0.254f)
        arcToRelative(
            1.5f,
            1.5f,
            0f,
            isMoreThanHalf = false,
            isPositiveArc = false,
            dx1 = 0.538f,
            dy1 = 0.143f
        )
        lineTo(2.01f, 11f)
        horizontalLineTo(14f)
        curveToRelative(0.325f, 0f, 0.502f, -0.078f, 0.602f, -0.145f)
        arcToRelative(
            0.76f,
            0.76f,
            0f,
            isMoreThanHalf = false,
            isPositiveArc = false,
            dx1 = 0.254f,
            dy1 = -0.302f
        )
        arcToRelative(
            1.5f, 1.5f, 0f,
            isMoreThanHalf = false,
            isPositiveArc = false,
            dx1 = 0.143f,
            dy1 = -0.538f
        )
        lineTo(15f, 9.99f)
        verticalLineTo(4f)
        curveToRelative(0f, -0.325f, -0.078f, -0.502f, -0.145f, -0.602f)
        arcToRelative(
            0.76f, 0.76f, 0f,
            isMoreThanHalf = false,
            isPositiveArc = false,
            dx1 = -0.302f,
            dy1 = -0.254f
        )
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 13.99f, y1 = 3f)
        horizontalLineTo(2f)
        curveToRelative(-0.325f, 0f, -0.502f, 0.078f, -0.602f, 0.145f)
    }
}.build()
