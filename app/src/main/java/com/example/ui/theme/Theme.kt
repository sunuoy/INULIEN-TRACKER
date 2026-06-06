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
    primary = NatDarkPrimary,
    onPrimary = NatDarkOnPrimary,
    primaryContainer = NatDarkPrimaryContainer,
    onPrimaryContainer = NatDarkOnPrimaryContainer,
    secondary = NatDarkPrimary,
    secondaryContainer = NatDarkSecondaryContainer,
    onSecondaryContainer = NatDarkOnSecondaryContainer,
    tertiary = NatDarkPrimary,
    tertiaryContainer = NatDarkTertiaryContainer,
    onTertiaryContainer = NatDarkOnTertiaryContainer,
    background = NatDarkBg,
    onBackground = NatDarkText,
    surface = Color(0xFF232520),
    onSurface = NatDarkText,
    surfaceVariant = NatDarkTertiaryContainer,
    onSurfaceVariant = NatDarkText,
    outline = NatDarkMuted
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NatPrimary,
    onPrimary = Color.White,
    primaryContainer = NatPrimaryContainer,
    onPrimaryContainer = NatOnPrimaryContainer,
    secondary = NatTertiary,
    secondaryContainer = NatSecondaryContainer,
    onSecondaryContainer = NatOnSecondaryContainer,
    tertiary = NatTertiary,
    tertiaryContainer = NatTertiaryContainer,
    onTertiaryContainer = NatOnTertiaryContainer,
    background = NatBg,
    onBackground = NatText,
    surface = Color.White,
    onSurface = NatText,
    surfaceVariant = NatNavbarBg,
    onSurfaceVariant = NatMuted,
    outline = NatMutedText
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
