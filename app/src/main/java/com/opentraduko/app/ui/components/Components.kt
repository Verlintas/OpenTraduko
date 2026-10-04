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

package com.opentraduko.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.opentraduko.app.core.model.Language
import kotlin.math.roundToInt

/**
 * Requests RECORD_AUDIO (and POST_NOTIFICATIONS on API 33+) before [action].
 * Returns a lambda that runs the action once permission is granted.
 */
@Composable
fun rememberPermissionRequester(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }
    return remember(context) {
        { action ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                action()
            } else {
                pendingAction = action
                val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(permissions.toTypedArray())
            }
        }
    }
}

@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun LevelMeter(
    level: Float,
    speaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val bars = 28
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
        val activeBars = (level * bars).roundToInt().coerceIn(0, bars)
        for (index in 0 until bars) {
            val fraction = (index + 1f) / bars
            val barHeight = size.height * (0.25f + 0.75f * fraction)
            val color = if (index < activeBars) activeColor else inactiveColor
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
fun LanguagePicker(
    label: String,
    selected: Language,
    enabled: Boolean = true,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val buttonText = if (label.isBlank()) selected.nativeName else "$label · ${selected.nativeName}"
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(buttonText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text("${language.nativeName} · ${language.englishName}") },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                )
            }
        }
    }
}


