package com.andreaserick.languagecoast.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.resources.Res
import com.andreaserick.languagecoast.resources.nunito
import org.jetbrains.compose.resources.Font

/** Nunito (SIL Open Font License, see docs/licenses); a single variable font file covers every weight. */
@Composable
private fun nunito() = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(Res.font.nunito, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

/**
 * The app's type scale, all in Nunito. Scripts Nunito doesn't cover (Arabic, Chinese and so on) fall
 * back to the platform's font, so a card in any language still shows.
 *
 * Screens should use these styles rather than their own font sizes, which is what keeps the app consistent.
 */
@Composable
fun coastTypography(): Typography {
    val family = nunito()

    fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: TextUnit = 0.sp) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing
    )

    return Typography(
        displayLarge = style(48, 56, FontWeight.ExtraBold, (-0.5).sp),
        displayMedium = style(40, 48, FontWeight.ExtraBold, (-0.5).sp),
        displaySmall = style(34, 42, FontWeight.ExtraBold, (-0.3).sp),
        headlineLarge = style(30, 38, FontWeight.ExtraBold, (-0.3).sp),
        headlineMedium = style(26, 32, FontWeight.ExtraBold, (-0.3).sp),
        headlineSmall = style(22, 28, FontWeight.ExtraBold),
        titleLarge = style(20, 26, FontWeight.ExtraBold),
        titleMedium = style(17, 23, FontWeight.ExtraBold),
        titleSmall = style(15, 20, FontWeight.Bold),
        bodyLarge = style(16, 24, FontWeight.Medium),
        bodyMedium = style(14, 20, FontWeight.SemiBold),
        bodySmall = style(13, 18, FontWeight.SemiBold),
        labelLarge = style(16, 22, FontWeight.ExtraBold),
        labelMedium = style(14, 20, FontWeight.Bold),
        labelSmall = style(12, 16, FontWeight.Bold)
    )
}
