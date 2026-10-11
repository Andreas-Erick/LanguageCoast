package com.andreaserick.languagecoast.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.resources.Res
import com.andreaserick.languagecoast.resources.nunito
import org.jetbrains.compose.resources.Font

/** Nunito (SIL Open Font License, see docs/licenses) for headings; a single variable font file covers every weight. */
@Composable
private fun nunito() = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(Res.font.nunito, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

private val Default = Typography()

/** Headings use Nunito; body text keeps the platform's default font (Roboto on Android, San Francisco on iOS). */
@Composable
fun coastTypography(): Typography {
    val nunito = nunito()
    return Typography(
        displaySmall = Default.displaySmall.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
        headlineLarge = Default.headlineLarge.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
        headlineMedium = Default.headlineMedium.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
        headlineSmall = Default.headlineSmall.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
        titleLarge = Default.titleLarge.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
        titleMedium = Default.titleMedium.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        )
    )
}
