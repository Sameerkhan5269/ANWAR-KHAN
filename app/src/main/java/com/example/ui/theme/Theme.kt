package com.example.ui.theme

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
    val hairlineBorder: Color,
    val gridBorderStrong: Color = GridBorderStrong,
    val maroonPrimary: Color = MaroonPrimary,
    val limeGreen: Color = LimeGreen
)

val LocalSolarColors = staticCompositionLocalOf {
    SolarExtendedColors(
        debitBlue = BusyDebitBlue,
        creditRed = BusyCreditRed,
        solarAmber = MaroonPrimary,
        solarAmberContainer = MaroonContainerLight,
        emerald = LimeGreen,
        emeraldContainer = LimeSoft,
        navyHeader = MaroonPrimary,
        gridHeader = BusyGridHeaderLight,
        hairlineBorder = SlateBorderLight,
        gridBorderStrong = GridBorderStrong,
        maroonPrimary = MaroonPrimary,
        limeGreen = LimeGreen
    )
}

private val LightColorScheme = lightColorScheme(
    primary = MaroonPrimary,
    onPrimary = Color.White,
    primaryContainer = MaroonContainerLight,
    onPrimaryContainer = MaroonDark,
    secondary = LimeGreen,
    onSecondary = Color.White,
    secondaryContainer = LimeSoft,
    onSecondaryContainer = Color(0xFF2B4D12),
    tertiary = LimeGreenDark,
    onTertiary = Color.White,
    tertiaryContainer = LimeSoft,
    onTertiaryContainer = Color(0xFF1F3B0B),
    background = LightCanvas,
    onBackground = SlateInk,
    surface = LightCard,
    onSurface = SlateInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = GridBorderStrong,
    error = BusyCreditRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE57373),
    onPrimary = Color(0xFF3E0B0A),
    primaryContainer = MaroonContainerDark,
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = LimeGreen,
    onSecondary = Color(0xFF132207),
    secondaryContainer = LimeDarkBg,
    onSecondaryContainer = Color(0xFFDCEDC8),
    tertiary = Color(0xFF9CCC65),
    onTertiary = Color(0xFF132207),
    tertiaryContainer = LimeDarkBg,
    onTertiaryContainer = Color(0xFFDCEDC8),
    background = DarkCanvas,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkCard,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = SlateBorderDark,
    error = BusyCreditRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFECACA)
)

private val NBSolarShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun NBSolarTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        SolarExtendedColors(
            debitBlue = BusyDebitBlueDark,
            creditRed = BusyCreditRedDark,
            solarAmber = Color(0xFFE57373),
            solarAmberContainer = MaroonContainerDark,
            emerald = LimeGreen,
            emeraldContainer = LimeDarkBg,
            navyHeader = MaroonDark,
            gridHeader = BusyGridHeaderDark,
            hairlineBorder = SlateBorderDark,
            gridBorderStrong = SlateBorderDark,
            maroonPrimary = MaroonPrimary,
            limeGreen = LimeGreen
        )
    } else {
        SolarExtendedColors(
            debitBlue = BusyDebitBlue,
            creditRed = BusyCreditRed,
            solarAmber = MaroonPrimary,
            solarAmberContainer = MaroonContainerLight,
            emerald = LimeGreen,
            emeraldContainer = LimeSoft,
            navyHeader = MaroonPrimary,
            gridHeader = BusyGridHeaderLight,
            hairlineBorder = SlateBorderLight,
            gridBorderStrong = GridBorderStrong,
            maroonPrimary = MaroonPrimary,
            limeGreen = LimeGreen
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
