package com.andreaserick.languagecoast.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.R

/** Nunito (SIL Open Font License, see docs/licenses) for headings; a single variable font file covers every weight. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Nunito = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(R.font.nunito, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

private val Default = Typography()

// Headings use Nunito; body text keeps the Material 3 default (Roboto).
val Typography = Typography(
    displaySmall = Default.displaySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    titleLarge = Default.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    titleMedium = Default.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
