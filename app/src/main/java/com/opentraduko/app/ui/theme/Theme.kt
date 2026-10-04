/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of OpenTraduko.
 *
 * OpenTraduko is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * OpenTraduko is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * OpenTraduko. If not, see <https://www.gnu.org/licenses/>.
 */

package com.opentraduko.app.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2E0),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E0),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF456179),
    onTertiary = Color.White,
    surface = Color(0xFFFAFDFB),
    surfaceVariant = Color(0xFFDAE5E1),
    background = Color(0xFFFAFDFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5C5),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFA7F2E0),
    secondary = Color(0xFFB0CCC5),
    onSecondary = Color(0xFF1B3530),
    secondaryContainer = Color(0xFF324B46),
    onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = Color(0xFFADCAE5),
    onTertiary = Color(0xFF143349),
    background = Color(0xFF191C1B),
    surface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFF3F4946),
)

@Composable
fun OpenTradukoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
