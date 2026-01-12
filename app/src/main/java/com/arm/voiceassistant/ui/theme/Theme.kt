/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Defines the custom light color scheme for the app using Arm brand colors.
 */
val ArmLightColorScheme = lightColorScheme(
    primary = ArmColors.ArmBlue,
    onPrimary = ArmColors.ArmLightBlue,
    primaryContainer = ArmColors.ArmBlack,
    onPrimaryContainer = ArmColors.ArmLightGray,
    inversePrimary = ArmColors.ArmLightBlue,
    secondary = DarkBlueGray,
    onSecondary = DarkBlueGray,
    secondaryContainer = ArmColors.ArmLightGray,
    onSecondaryContainer = ArmColors.ArmBlack,
    tertiary = ArmColors.ArmOrange,
    error = ArmColors.ArmRed,
    onError = ArmColors.ArmLightGray
)

/**
 * Defines the custom dark color scheme for the app using Arm brand colors.
 */
val ArmDarkColorScheme = darkColorScheme(
    primary = ArmColors.ArmBlack,
    onPrimary = ArmColors.ArmBlue,
    primaryContainer = DarkBlueGray,
    onPrimaryContainer = ArmColors.ArmLightGray,
    inversePrimary = ArmColors.ArmLightBlue,
    secondary = DarkBlueGray,
    onSecondary = ArmColors.ArmLightGray,
    secondaryContainer = ArmColors.ArmBlue,
    onSecondaryContainer = ArmColors.ArmLightGray,
    tertiary = ArmColors.ArmOrange,
    error = ArmColors.ArmRed,
    onError = ArmColors.ArmLightGray
)

/**
 * Applies a background color based on the current theme.
 */
fun Modifier.backgroundColorModifier(): Modifier = composed {
    val mod =
        if (isSystemInDarkTheme()) {
            Modifier.background(color = MaterialTheme.colorScheme.primary)
        } else {
            Modifier.background(brush = Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.inversePrimary)))
        }
    this then mod
}

/**
 * Applies the app's custom Material3 theme.
 * @param darkTheme Whether to use the dark color scheme (default: system setting)
 * @param dynamicColor Whether to use dynamic colors on Android 12+ (default: false)
 * @param content The composable UI content to apply the theme to
 */
@Composable
fun VoiceAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> ArmDarkColorScheme
        else -> ArmLightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.secondary.toArgb()
            window.navigationBarColor = colorScheme.secondary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
