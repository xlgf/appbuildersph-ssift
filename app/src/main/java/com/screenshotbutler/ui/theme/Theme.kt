package com.screenshotbutler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.screenshotbutler.R

/**
 * Butler's bento design system (from the Stitch design): sage ground, off-white rounded cards,
 * coral primary, indigo secondary, chartreuse tertiary, Manrope throughout.
 */
object Palette {
    val Coral = Color(0xFFD9694A)        // primary
    val CoralDeep = Color(0xFFA13A22)    // primary pressed / filled button
    val Indigo = Color(0xFF5B5CE2)       // secondary
    val Chartreuse = Color(0xFFD3D96B)   // tertiary
    val Olive = Color(0xFF6B6E1E)        // tertiary deep (icon button)
    val Lilac = Color(0xFFD9B8F6)
    val Sage = Color(0xFFCDD0C2)         // neutral: app background
    val Card = Color(0xFFF3F3EE)         // cards and fields
    val CardAlt = Color(0xFFE9E9E0)
    val Ink = Color(0xFF1C1C1A)          // headlines
    val Body = Color(0xFF4E403B)         // body text, warm brown-grey
    val Muted = Color(0xFF6E6E66)
    val Inverted = Color(0xFF2E302B)     // dark pill buttons
    val Rule = Color(0xFFDAD8CC)
    val Danger = Color(0xFFB3261E)
    val MonogramFill = Color(0xFF3E4636)
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Manrope variable font, bundled so it works offline. */
val Manrope = FontFamily(manrope(300), manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private val ButlerColors = lightColorScheme(
    primary = Palette.Coral, onPrimary = Color.White,
    primaryContainer = Palette.CoralDeep, onPrimaryContainer = Color.White,
    secondary = Palette.Indigo, onSecondary = Color.White,
    tertiary = Palette.Chartreuse, onTertiary = Palette.Ink,
    background = Palette.Sage, onBackground = Palette.Ink,
    surface = Palette.Card, onSurface = Palette.Ink,
    surfaceVariant = Palette.CardAlt, onSurfaceVariant = Palette.Muted,
    outline = Palette.Inverted, outlineVariant = Palette.Rule,
    error = Palette.Danger,
)

private val BentoType = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

private val BentoShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(32.dp),
)

/** The design is light-only for now; a matching dark variant can come later. */
@Composable
fun ButlerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ButlerColors, typography = BentoType, shapes = BentoShapes, content = content)
}
