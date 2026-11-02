package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.ui.theme.OceanGround
import com.andreaserick.languagecoast.ui.theme.OceanSurface
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.WaveLineColor
import kotlin.math.PI
import kotlin.math.sin

/**
 * The title bar used by every screen: a title, an optional back arrow, optional actions,
 * and a wave line underneath.
 *
 * @param subtitle Optional smaller line under the title (e.g. a coast's native name).
 * @param onBack Shows a back arrow when not null.
 * @param leading Optional content before the title (e.g. the app logo); not shown together with [onBack].
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // Top-level screens get the larger title; screens you can go back from are a level down.
    val titleStyle = if (onBack == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack == null) ScreenMargin else 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            } else if (leading != null) {
                leading()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = TextPrimary,
                    maxLines = 1,
                    // Shrinks before cutting off, so "Language Coast" fits next to the streak on small phones.
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = titleStyle.fontSize),
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            actions()
        }
        WaveLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .padding(horizontal = ScreenMargin)
        )
    }
}

/** A single wave under the header: the one bit of decoration the app allows itself. */
@Composable
fun WaveLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val waves = 5
        val amplitude = size.height / 3.2f
        val steps = 120
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            for (i in 1..steps) {
                val x = size.width * i / steps
                val y = size.height / 2 + amplitude * sin(2 * PI * waves * i / steps).toFloat()
                lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = WaveLineColor,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * The app background: deep water that is a little lighter at the top, with a faint sunlit glow in one
 * corner. Enough depth for cards to sit on, not enough to compete with them.
 */
@Composable
fun OceanBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(OceanSurface, OceanGround)))
            .drawBehind {
                val center = Offset(size.width * 0.9f, -size.width * 0.2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Sunlit.copy(alpha = 0.10f), Color.Transparent),
                        center = center,
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = center
                )
            },
        content = content
    )
}
