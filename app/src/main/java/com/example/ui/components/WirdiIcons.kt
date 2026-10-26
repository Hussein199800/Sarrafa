package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.domain.model.DhikrCategory

/**
 * Custom monoline vector icons crafted specifically for Wirdi.
 * Every icon uses a uniform 1.75dp stroke width with rounded caps and joins (StrokeCap.Round, StrokeJoin.Round),
 * without using any Apple proprietary assets (SF Symbols) or heavy external icon libraries.
 */
object WirdiIcons {

    private const val STROKE = 1.75f

    private fun monolineIcon(
        name: String,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    fun gateIconFor(category: DhikrCategory): ImageVector = when (category) {
        DhikrCategory.GATE_ISTIGHFAR -> IstighfarDrop
        DhikrCategory.GATE_HAMD -> AdhkarLeaf
        DhikrCategory.GATE_HAWQALA -> HawqalaShield
        DhikrCategory.GATE_TASBIH -> TasbihBeads
        DhikrCategory.GATE_SALAWAT -> SparkleStar
        else -> AdhkarLeaf
    }

    val HomeHorizon: ImageVector by lazy {
        monolineIcon("HomeHorizon") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 17f)
                lineTo(21f, 17f)
                moveTo(6f, 20.5f)
                lineTo(18f, 20.5f)
                moveTo(7f, 17f)
                curveTo(7f, 14.2f, 9.2f, 12f, 12f, 12f)
                curveTo(14.8f, 12f, 17f, 14.2f, 17f, 17f)
                moveTo(12f, 6.5f)
                lineTo(12f, 8.8f)
                moveTo(6.5f, 9.5f)
                lineTo(8.1f, 11.1f)
                moveTo(17.5f, 9.5f)
                lineTo(15.9f, 11.1f)
            }
        }
    }

    val AdhkarLeaf: ImageVector by lazy {
        monolineIcon("AdhkarLeaf") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20.5f)
                curveTo(12f, 20.5f, 4.5f, 15.2f, 4.5f, 9.5f)
                curveTo(4.5f, 6.7f, 6.7f, 4.5f, 9.3f, 4.5f)
                curveTo(10.8f, 4.5f, 12f, 5.4f, 12f, 6.6f)
                curveTo(12f, 5.4f, 13.2f, 4.5f, 14.7f, 4.5f)
                curveTo(17.3f, 4.5f, 19.5f, 6.7f, 19.5f, 9.5f)
                curveTo(19.5f, 15.2f, 12f, 20.5f, 12f, 20.5f)
                close()
            }
        }
    }

    val IstighfarDrop: ImageVector by lazy {
        monolineIcon("IstighfarDrop") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(12f, 3.5f, 6f, 10.2f, 6f, 14.5f)
                curveTo(6f, 17.8f, 8.7f, 20.5f, 12f, 20.5f)
                curveTo(15.3f, 20.5f, 18f, 17.8f, 18f, 14.5f)
                curveTo(18f, 10.2f, 12f, 3.5f, 12f, 3.5f)
                close()
                moveTo(9.5f, 15f)
                curveTo(9.5f, 16.4f, 10.6f, 17.5f, 12f, 17.5f)
            }
        }
    }

    val HawqalaShield: ImageVector by lazy {
        monolineIcon("HawqalaShield") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                lineTo(19f, 6.5f)
                verticalLineTo(11.8f)
                curveTo(19f, 16.2f, 15.9f, 19.4f, 12f, 20.8f)
                curveTo(8.1f, 19.4f, 5f, 16.2f, 5f, 11.8f)
                verticalLineTo(6.5f)
                close()
                moveTo(9.5f, 12.2f)
                lineTo(11.3f, 14f)
                lineTo(14.8f, 10.2f)
            }
        }
    }

    val BookLibrary: ImageVector by lazy {
        monolineIcon("BookLibrary") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 6.5f)
                curveTo(10.2f, 5.2f, 7.5f, 4.8f, 4f, 5.2f)
                verticalLineTo(18.5f)
                curveTo(7.5f, 18.1f, 10.2f, 18.5f, 12f, 19.8f)
                curveTo(13.8f, 18.5f, 16.5f, 18.1f, 20f, 18.5f)
                verticalLineTo(5.2f)
                curveTo(16.5f, 4.8f, 13.8f, 5.2f, 12f, 6.5f)
                close()
                moveTo(12f, 6.5f)
                verticalLineTo(19.5f)
            }
        }
    }

    val MoreCircles: ImageVector by lazy {
        monolineIcon("MoreCircles") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12f)
                curveTo(5f, 11.2f, 5.6f, 10.5f, 6.5f, 10.5f)
                curveTo(7.4f, 10.5f, 8f, 11.2f, 8f, 12f)
                curveTo(8f, 12.8f, 7.4f, 13.5f, 6.5f, 13.5f)
                curveTo(5.6f, 13.5f, 5f, 12.8f, 5f, 12f)
                close()

                moveTo(10.5f, 12f)
                curveTo(10.5f, 11.2f, 11.1f, 10.5f, 12f, 10.5f)
                curveTo(12.9f, 10.5f, 13.5f, 11.2f, 13.5f, 12f)
                curveTo(13.5f, 12.8f, 12.9f, 13.5f, 12f, 13.5f)
                curveTo(11.1f, 13.5f, 10.5f, 12.8f, 10.5f, 12f)
                close()

                moveTo(16f, 12f)
                curveTo(16f, 11.2f, 16.6f, 10.5f, 17.5f, 10.5f)
                curveTo(18.4f, 10.5f, 19f, 11.2f, 19f, 12f)
                curveTo(19f, 12.8f, 18.4f, 13.5f, 17.5f, 13.5f)
                curveTo(16.6f, 13.5f, 16f, 12.8f, 16f, 12f)
                close()
            }
        }
    }

    val Compass: ImageVector by lazy {
        monolineIcon("Compass") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                close()
                moveTo(15.5f, 8.5f)
                lineTo(13.5f, 13.5f)
                lineTo(8.5f, 15.5f)
                lineTo(10.5f, 10.5f)
                close()
            }
        }
    }

    val TasbihBeads: ImageVector by lazy {
        monolineIcon("TasbihBeads") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
                curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
                close()
                moveTo(12f, 8f)
                curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f)
                curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f)
                curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f)
                curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f)
                close()
            }
        }
    }

    val FocusSession: ImageVector by lazy {
        monolineIcon("FocusSession") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Corner focus brackets + center serene circle
                moveTo(4f, 8.5f)
                verticalLineTo(5.5f)
                curveTo(4f, 4.7f, 4.7f, 4f, 5.5f, 4f)
                horizontalLineTo(8.5f)

                moveTo(15.5f, 4f)
                horizontalLineTo(18.5f)
                curveTo(19.3f, 4f, 20f, 4.7f, 20f, 5.5f)
                verticalLineTo(8.5f)

                moveTo(20f, 15.5f)
                verticalLineTo(18.5f)
                curveTo(20f, 19.3f, 19.3f, 20f, 18.5f, 20f)
                horizontalLineTo(15.5f)

                moveTo(8.5f, 20f)
                horizontalLineTo(5.5f)
                curveTo(4.7f, 20f, 4f, 19.3f, 4f, 18.5f)
                verticalLineTo(15.5f)

                moveTo(12f, 8.5f)
                curveTo(13.9f, 8.5f, 15.5f, 10.1f, 15.5f, 12f)
                curveTo(15.5f, 13.9f, 13.9f, 15.5f, 12f, 15.5f)
                curveTo(10.1f, 15.5f, 8.5f, 13.9f, 8.5f, 12f)
                curveTo(8.5f, 10.1f, 10.1f, 8.5f, 12f, 8.5f)
                close()
            }
        }
    }

    val EyeDim: ImageVector by lazy {
        monolineIcon("EyeDim") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 12f)
                curveTo(5.5f, 7.5f, 8.5f, 6f, 12f, 6f)
                curveTo(15.5f, 6f, 18.5f, 7.5f, 21f, 12f)
                curveTo(18.5f, 16.5f, 15.5f, 18f, 12f, 18f)
                curveTo(8.5f, 18f, 5.5f, 16.5f, 3f, 12f)
                close()
                moveTo(12f, 9.5f)
                curveTo(13.4f, 9.5f, 14.5f, 10.6f, 14.5f, 12f)
                curveTo(14.5f, 13.4f, 13.4f, 14.5f, 12f, 14.5f)
                curveTo(10.6f, 14.5f, 9.5f, 13.4f, 9.5f, 12f)
                curveTo(9.5f, 10.6f, 10.6f, 9.5f, 12f, 9.5f)
                close()
            }
        }
    }

    val PauseCircle: ImageVector by lazy {
        monolineIcon("PauseCircle") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
                curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
                close()
                moveTo(10f, 9f)
                verticalLineTo(15f)
                moveTo(14f, 9f)
                verticalLineTo(15f)
            }
        }
    }

    val LockShield: ImageVector by lazy {
        monolineIcon("LockShield") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 11f)
                horizontalLineTo(17f)
                curveTo(18.1f, 11f, 19f, 11.9f, 19f, 13f)
                verticalLineTo(18f)
                curveTo(19f, 19.1f, 18.1f, 20f, 17f, 20f)
                horizontalLineTo(7f)
                curveTo(5.9f, 20f, 5f, 19.1f, 5f, 18f)
                verticalLineTo(13f)
                curveTo(5f, 11.9f, 5.9f, 11f, 7f, 11f)
                close()
                moveTo(8.5f, 11f)
                verticalLineTo(7.5f)
                curveTo(8.5f, 5.6f, 10.1f, 4f, 12f, 4f)
                curveTo(13.9f, 4f, 15.5f, 5.6f, 15.5f, 7.5f)
                verticalLineTo(11f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        monolineIcon("Copy") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 9f)
                horizontalLineTo(18f)
                curveTo(19.1f, 9f, 20f, 9.9f, 20f, 11f)
                verticalLineTo(18f)
                curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f)
                horizontalLineTo(9f)
                curveTo(7.9f, 20f, 7f, 19.1f, 7f, 18f)
                verticalLineTo(11f)
                curveTo(7f, 9.9f, 7.9f, 9f, 9f, 9f)
                close()
                moveTo(15f, 6f)
                horizontalLineTo(6f)
                curveTo(4.9f, 6f, 4f, 6.9f, 4f, 8f)
                verticalLineTo(15f)
            }
        }
    }

    val Share: ImageVector by lazy {
        monolineIcon("Share") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                verticalLineTo(15f)
                moveTo(8f, 7.5f)
                lineTo(12f, 3.5f)
                lineTo(16f, 7.5f)
                moveTo(5f, 13f)
                verticalLineTo(18.5f)
                curveTo(5f, 19.6f, 5.9f, 20.5f, 7f, 20.5f)
                horizontalLineTo(17f)
                curveTo(18.1f, 20.5f, 19f, 19.6f, 19f, 18.5f)
                verticalLineTo(13f)
            }
        }
    }

    val Calendar: ImageVector by lazy {
        monolineIcon("Calendar") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 5f)
                horizontalLineTo(18f)
                curveTo(19.1f, 5f, 20f, 5.9f, 20f, 7f)
                verticalLineTo(18f)
                curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f)
                horizontalLineTo(6f)
                curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f)
                verticalLineTo(7f)
                curveTo(4f, 5.9f, 4.9f, 5f, 6f, 5f)
                close()
                moveTo(8f, 3f)
                verticalLineTo(6.5f)
                moveTo(16f, 3f)
                verticalLineTo(6.5f)
                moveTo(4f, 10f)
                horizontalLineTo(20f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        monolineIcon("Clock") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                close()
                moveTo(12f, 7.5f)
                verticalLineTo(12f)
                lineTo(15.5f, 14f)
            }
        }
    }

    val SettingsSliders: ImageVector by lazy {
        monolineIcon("SettingsSliders") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 17f)
                horizontalLineTo(20f)
                moveTo(9f, 5f)
                verticalLineTo(9f)
                moveTo(15f, 10f)
                verticalLineTo(14f)
                moveTo(10f, 15f)
                verticalLineTo(19f)
            }
        }
    }

    val InfoCircle: ImageVector by lazy {
        monolineIcon("InfoCircle") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                close()
                moveTo(12f, 11.5f)
                verticalLineTo(16.5f)
                moveTo(12f, 8f)
                verticalLineTo(8.2f)
            }
        }
    }

    val ChevronStart: ImageVector by lazy {
        monolineIcon("ChevronStart") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14.5f, 6.5f)
                lineTo(9f, 12f)
                lineTo(14.5f, 17.5f)
            }
        }
    }

    val CheckMark: ImageVector by lazy {
        monolineIcon("CheckMark") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.5f, 12.5f)
                lineTo(10f, 17f)
                lineTo(18.5f, 7.5f)
            }
        }
    }

    val RefreshArc: ImageVector by lazy {
        monolineIcon("RefreshArc") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.5f, 12f)
                curveTo(19.5f, 16.14f, 16.14f, 19.5f, 12f, 19.5f)
                curveTo(7.86f, 19.5f, 4.5f, 16.14f, 4.5f, 12f)
                curveTo(4.5f, 7.86f, 7.86f, 4.5f, 12f, 4.5f)
                curveTo(14.8f, 4.5f, 17.2f, 6.0f, 18.5f, 8.2f)
                moveTo(18.5f, 4.5f)
                verticalLineTo(8.5f)
                horizontalLineTo(14.5f)
            }
        }
    }

    val UncheckedCircle: ImageVector by lazy {
        monolineIcon("UncheckedCircle") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
                curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
                close()
            }
        }
    }

    val SparkleStar: ImageVector by lazy {
        monolineIcon("SparkleStar") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(12.5f, 8.2f, 15.8f, 11.5f, 20.5f, 12f)
                curveTo(15.8f, 12.5f, 12.5f, 15.8f, 12f, 20.5f)
                curveTo(11.5f, 15.8f, 8.2f, 12.5f, 3.5f, 12f)
                curveTo(8.2f, 11.5f, 11.5f, 8.2f, 12f, 3.5f)
                close()
            }
        }
    }

    val PaletteSpark: ImageVector by lazy {
        monolineIcon("PaletteSpark") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 4.5f)
                curveTo(11.5f, 5.2f, 8.8f, 8.8f, 9.5f, 13.0f)
                curveTo(10.1f, 16.5f, 13.2f, 19.0f, 16.8f, 19.0f)
                curveTo(14.5f, 20.3f, 11.5f, 20.4f, 8.9f, 19.0f)
                curveTo(5.0f, 16.8f, 3.6f, 11.9f, 5.8f, 8.0f)
                curveTo(7.8f, 4.5f, 11.8f, 3.2f, 15.5f, 4.5f)
                close()
            }
        }
    }

    val HeartDua: ImageVector by lazy {
        monolineIcon("HeartDua") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20f)
                curveTo(12f, 20f, 4.5f, 14.8f, 4.5f, 9.2f)
                curveTo(4.5f, 6.6f, 6.5f, 4.5f, 9.0f, 4.5f)
                curveTo(10.5f, 4.5f, 11.5f, 5.3f, 12f, 6.4f)
                curveTo(12.5f, 5.3f, 13.5f, 4.5f, 15.0f, 4.5f)
                curveTo(17.5f, 4.5f, 19.5f, 6.6f, 19.5f, 9.2f)
                curveTo(19.5f, 14.8f, 12f, 20f, 12f, 20f)
                close()
            }
        }
    }
}
