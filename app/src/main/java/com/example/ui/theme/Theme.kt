package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = OceanDarkPrimary,
    onPrimary = OceanDarkOnPrimary,
    primaryContainer = OceanDarkPrimaryContainer,
    onPrimaryContainer = OceanDarkOnPrimaryContainer,
    secondary = OceanDarkPrimary,
    secondaryContainer = OceanDarkSecondaryContainer,
    onSecondaryContainer = OceanDarkOnSecondaryContainer,
    tertiary = OceanDarkPrimary,
    tertiaryContainer = OceanDarkTertiaryContainer,
    onTertiaryContainer = OceanDarkOnTertiaryContainer,
    background = OceanDarkBg,
    onBackground = OceanDarkText,
    surface = Color(0xFF191C20),
    onSurface = OceanDarkText,
    surfaceVariant = OceanDarkSecondaryContainer,
    onSurfaceVariant = OceanDarkText,
    outline = OceanDarkMuted
  )

private val LightColorScheme =
  lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    secondary = OceanSecondary,
    secondaryContainer = OceanSecondaryContainer,
    onSecondaryContainer = OceanOnSecondaryContainer,
    tertiary = OceanSecondary,
    tertiaryContainer = OceanTertiaryContainer,
    onTertiaryContainer = OceanOnTertiaryContainer,
    background = OceanBg,
    onBackground = OceanText,
    surface = Color.White,
    onSurface = OceanText,
    surfaceVariant = OceanNavbarBg,
    onSurfaceVariant = OceanMuted,
    outline = OceanMutedText
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to enforce our beautiful cohesive Natural Tones branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
