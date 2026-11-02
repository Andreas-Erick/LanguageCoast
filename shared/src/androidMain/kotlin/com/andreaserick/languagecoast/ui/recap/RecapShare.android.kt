package com.andreaserick.languagecoast.ui.recap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Draws the recap as a 1080×1350 card in the app's colours and opens the share sheet with it. */
@Composable
actual fun rememberRecapSharer(): ((RecapShare) -> Unit)? {
    val context = LocalContext.current
    return { recap -> shareRecap(context, recap) }
}

private const val WIDTH = 1080
private const val HEIGHT = 1350
private const val OCEAN_GROUND = 0xFF0C2536.toInt()
private const val OCEAN_SURFACE = 0xFF123349.toInt()
private const val SURFACE_CARD = 0xFF153449.toInt()
private const val SUNLIT = 0xFFF3C969.toInt()
private const val TEXT_PRIMARY = 0xFFF5F2EA.toInt()
private const val TEXT_SECONDARY = 0xFFA9BDCB.toInt()

private fun shareRecap(context: Context, recap: RecapShare) {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), OCEAN_SURFACE, OCEAN_GROUND, Shader.TileMode.CLAMP)
    })

    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_SECONDARY; textSize = 36f; typeface = Typeface.DEFAULT_BOLD }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_PRIMARY; textSize = 72f; typeface = Typeface.DEFAULT_BOLD }
    val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_PRIMARY; textSize = 44f }
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUNLIT; textSize = 96f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_SECONDARY; textSize = 34f; textAlign = Paint.Align.CENTER }
    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE_CARD }
    val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUNLIT }

    val margin = 72f
    // The sun-on-horizon mark, as on the app's icon.
    canvas.drawCircle(WIDTH - margin - 48f, margin + 48f, 44f, sun)
    canvas.drawRect(WIDTH - margin - 110f, margin + 48f, WIDTH - margin + 14f, margin + 58f, Paint().apply { color = OCEAN_SURFACE })

    canvas.drawText("LANGUAGE COAST", margin, margin + 40f, label)
    canvas.drawText(recap.title, margin, margin + 130f, title)
    drawWrapped(canvas, recap.headline, margin, margin + 210f, WIDTH - 2 * margin, headline, 56f)

    // Four stat tiles in a 2×2 grid.
    val top = margin + 360f
    val gap = 24f
    val tileWidth = (WIDTH - 2 * margin - gap) / 2
    val tileHeight = 260f
    recap.stats.take(4).forEachIndexed { index, (value, text) ->
        val left = margin + (index % 2) * (tileWidth + gap)
        val tileTop = top + (index / 2) * (tileHeight + gap)
        canvas.drawRoundRect(RectF(left, tileTop, left + tileWidth, tileTop + tileHeight), 40f, 40f, card)
        canvas.drawText(value, left + tileWidth / 2, tileTop + 140f, big)
        canvas.drawText(text, left + tileWidth / 2, tileTop + 205f, small)
    }

    var y = top + 2 * tileHeight + gap + 90f
    val coastText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_PRIMARY; textSize = 40f }
    recap.coasts.take(4).forEach { line ->
        canvas.drawText(line, margin, y, coastText)
        y += 60f
    }

    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(folder, "language-coast-${recap.title.lowercase().replace(' ', '-')}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "${recap.title} on Language Coast: ${recap.headline}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share your month").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

/** Draws [text] from ([x], [y]) wrapping at [width], one line every [lineHeight]. */
private fun drawWrapped(canvas: Canvas, text: String, x: Float, y: Float, width: Float, paint: Paint, lineHeight: Float) {
    var line = ""
    var currentY = y
    for (word in text.split(" ")) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) > width && line.isNotEmpty()) {
            canvas.drawText(line, x, currentY, paint)
            currentY += lineHeight
            line = word
        } else {
            line = candidate
        }
    }
    if (line.isNotEmpty()) canvas.drawText(line, x, currentY, paint)
}



