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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.ui.theme.AbyssBlue
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MidnightOcean
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import com.andreaserick.languagecoast.ui.theme.SurfLightBlue
import com.andreaserick.languagecoast.ui.theme.WaveTeal
import kotlin.math.PI
import kotlin.math.sin

/**
 * The title bar used by every screen: a title, an optional back arrow, optional actions,
 * and a layered coastal wave line underneath.
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack == null) 24.dp else 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SandBeige)
                }
            } else if (leading != null) {
                leading()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SandBeige,
                    maxLines = 1,
                    // Shrinks before cutting off, so "Language Coast" fits next to the streak badge on small phones.
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 20.sp,
                        maxFontSize = MaterialTheme.typography.headlineMedium.fontSize
                    ),
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = SandMuted)
                }
            }
            actions()
        }
        WaveLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .padding(horizontal = 24.dp)
        )
    }
}

/** A dual-wave divider under headers, evoking gentle ocean swells. */
@Composable
fun WaveLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val waves = 5
        val amplitude = size.height / 3.2f
        val primaryPath = Path().apply {
            moveTo(0f, size.height / 2)
            val steps = 120
            for (i in 1..steps) {
                val x = size.width * i / steps
                val y = size.height / 2 + amplitude * sin(2 * PI * waves * i / steps).toFloat()
                lineTo(x, y)
            }
        }
        val secondaryPath = Path().apply {
            moveTo(0f, size.height / 2)
            val steps = 120
            for (i in 1..steps) {
                val x = size.width * i / steps
                val y = size.height / 2 + (amplitude * 0.65f) * sin(2 * PI * waves * i / steps + 1.1).toFloat()
                lineTo(x, y)
            }
        }
        drawPath(
            path = secondaryPath,
            color = SurfLightBlue.copy(alpha = 0.14f),
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = primaryPath,
            color = SandBeige.copy(alpha = 0.26f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * The app background: a deep ocean gradient from [DeepOceanBlue] through [MidnightOcean] to [AbyssBlue],
 * with subtle sunlit water reflections near the surface.
 */
@Composable
fun OceanBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepOceanBlue, MidnightOcean, AbyssBlue)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WaveTeal.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.85f, size.height * 0.08f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SandBeige.copy(alpha = 0.07f), androidx.compose.ui.graphics.Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.28f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.15f, size.height * 0.28f)
            )
        }
        content()
    }
}
