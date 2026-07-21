package dev.vinkyv.tvcontrol.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val play_pause: ImageVector
  get() {
    if (_play_pause != null) {
      return _play_pause!!
    }
    _play_pause =
      ImageVector.Builder(
          name = "play_pause",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(5f, 16.2f)
            verticalLineTo(7.8f)
            lineTo(11f, 12f)
            lineTo(5f, 16.2f)
            close()
            moveTo(13f, 16f)
            verticalLineTo(8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(8f)
            horizontalLineTo(13f)
            close()
            moveToRelative(4f, 0f)
            verticalLineTo(8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(8f)
            horizontalLineTo(17f)
            close()
          }
        }
        .build()
    return _play_pause!!
  }

private var _play_pause: ImageVector? = null
