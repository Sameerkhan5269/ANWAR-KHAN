package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SolarExtendedColors(
    val debitBlue: Color,
    val creditRed: Color,
    val solarAmber: Color,
    val solarAmberContainer: Color,
    val emerald: Color,
    val emeraldContainer: Color,
    val navyHeader: Color,
    val gridHeader: Color,
    val hairlineBorder: Color
)

val LocalSolarColors = staticCompositionLocalOf {
    SolarExtendedColors(
        debitBlue = BusyDebitBlue,
        creditRed = BusyCreditRed,
        solarAmber = SolarAmber,
        solarAmberContainer = SolarAmberSoft,
        emerald = EmeraldSuccess,
        emeraldContainer = EmeraldSoft,
        navyHeader = DeepNavy,
        gridHeader = BusyGridHeaderLight,
        hairlineBorder = SlateBorderLight
    )
}

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F9),
    onPrimaryContainer = DeepNavy,
    secondary = SolarAmberDark,
    onSecondary = Color.White,
    secondaryContainer = SolarAmberSoft,
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldSoft,
    onTertiaryContainer = Color(0xFF064E3B),
    background = LightCanvas,
    onBackground = SlateInk,
    surface = LightCard,
    onSurface = SlateInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = SlateBorderLight,
    error = BusyCreditRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = SolarAmber,
    onPrimary = DeepNavy,
    primaryContainer = NavyContainer,
    onPrimaryContainer = Color(0xFFE2ECFF),
    secondary = SolarAmber,
    onSecondary = DeepNavy,
    secondaryContainer = SolarAmberDarkBg,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = EmeraldDarkBg,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = DarkCanvas,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkCard,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = SlateBorderDark,
    error = BusyCreditRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFECACA)
)

private val NBSolarShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun NBSolarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        SolarExtendedColors(
            debitBlue = BusyDebitBlueDark,
            creditRed = BusyCreditRedDark,
            solarAmber = SolarAmber,
            solarAmberContainer = SolarAmberDarkBg,
            emerald = Color(0xFF34D399),
            emeraldContainer = EmeraldDarkBg,
            navyHeader = Color(0xFF091224),
            gridHeader = BusyGridHeaderDark,
            hairlineBorder = SlateBorderDark
        )
    } else {
        SolarExtendedColors(
            debitBlue = BusyDebitBlue,
            creditRed = BusyCreditRed,
            solarAmber = SolarAmberDark,
            solarAmberContainer = SolarAmberSoft,
            emerald = EmeraldSuccess,
            emeraldContainer = EmeraldSoft,
            navyHeader = DeepNavy,
            gridHeader = BusyGridHeaderLight,
            hairlineBorder = SlateBorderLight
        )
    }

    CompositionLocalProvider(LocalSolarColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NBSolarTypography,
            shapes = NBSolarShapes,
            content = content
        )
    }
}
