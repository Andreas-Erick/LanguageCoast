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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.theme.AbyssBlue
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import kotlin.math.PI
import kotlin.math.sin

/**
 * The title bar used by every screen: a title, an optional back arrow, optional actions,
 * and a faint wave line underneath.
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
                .padding(start = if (onBack == null) 24.dp else 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp)
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
                .height(12.dp)
                .padding(horizontal = 24.dp)
        )
    }
}

/** A faint hand-drawn-looking wave, used as a divider under headers. */
@Composable
fun WaveLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val waves = 6
        val amplitude = size.height / 3
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            val steps = 120
            for (i in 1..steps) {
                val x = size.width * i / steps
                val y = size.height / 2 + amplitude * sin(2 * PI * waves * i / steps).toFloat()
                lineTo(x, y)
            }
        }
        drawPath(path, color = SandBeige.copy(alpha = 0.18f), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** The app background: a gentle gradient from [DeepOceanBlue] at the surface to [AbyssBlue] below. */
@Composable
fun OceanBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepOceanBlue, AbyssBlue))),
        content = content
    )
}
