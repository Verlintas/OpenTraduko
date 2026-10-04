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

package com.opentraduko.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentraduko.app.BuildConfig
import com.opentraduko.app.R
import com.opentraduko.app.core.model.DuplexMode
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.TextScale
import com.opentraduko.app.core.model.TranslationEngineKind
import com.opentraduko.app.data.AppContainer
import com.opentraduko.app.data.settings.AppSettings
import com.opentraduko.app.feature.session.SessionViewModel
import com.opentraduko.app.ui.components.LanguagePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_languages))
            LanguageSettingRow(
                title = stringResource(R.string.language_my),
                selected = settings.conversationLangA,
                onSelect = { language -> viewModel.updateSettings { it.copy(conversationLangA = language) } },
            )
            LanguageSettingRow(
                title = stringResource(R.string.language_partner),
                selected = settings.conversationLangB,
                onSelect = { language -> viewModel.updateSettings { it.copy(conversationLangB = language) } },
            )
            LanguageSettingRow(
                title = stringResource(R.string.language_source),
                selected = settings.listeningSource,
                onSelect = { language -> viewModel.updateSettings { it.copy(listeningSource = language) } },
            )
            LanguageSettingRow(
                title = stringResource(R.string.language_target),
                selected = settings.listeningTarget,
                onSelect = { language -> viewModel.updateSettings { it.copy(listeningTarget = language) } },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_translation))
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.translationEngine == TranslationEngineKind.ML_KIT,
                        onClick = {
                            viewModel.updateSettings { it.copy(translationEngine = TranslationEngineKind.ML_KIT) }
                        },
                        label = { Text(stringResource(R.string.settings_engine_mlkit)) },
                    )
                    FilterChip(
                        selected = settings.translationEngine == TranslationEngineKind.OPENAI_COMPATIBLE,
                        onClick = {
                            viewModel.updateSettings { it.copy(translationEngine = TranslationEngineKind.OPENAI_COMPATIBLE) }
                        },
                        label = { Text(stringResource(R.string.settings_engine_openai)) },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_engine_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (settings.translationEngine == TranslationEngineKind.OPENAI_COMPATIBLE) {
                OpenAiSettingsFields(
                    settings = settings,
                    onUpdate = viewModel::updateSettings,
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_speech))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tts)) },
                supportingContent = { Text(stringResource(R.string.settings_tts_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.ttsEnabled,
                        onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(ttsEnabled = enabled) } },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_speech_rate)) },
                supportingContent = {
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { rate -> viewModel.updateSettings { it.copy(speechRate = rate) } },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                    )
                },
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_duplex),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_duplex_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuplexMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.duplexMode == mode,
                            onClick = { viewModel.updateSettings { it.copy(duplexMode = mode) } },
                            label = { Text(duplexModeLabel(mode)) },
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_display))
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_text_size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextScale.entries.forEach { scale ->
                        FilterChip(
                            selected = settings.textScale == scale,
                            onClick = { viewModel.updateSettings { it.copy(textScale = scale) } },
                            label = { Text(textScaleLabel(scale)) },
                        )
                    }
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                trailingContent = {
                    Switch(
                        checked = settings.keepScreenOn,
                        onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(keepScreenOn = enabled) } },
                    )
                },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_models))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_open_models)) },
                supportingContent = { Text(stringResource(R.string.settings_open_models_desc)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickableRow(onOpenModels),
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME}") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_license)) },
                supportingContent = { Text("GPL-3.0-or-later") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_github)) },
                modifier = Modifier.clickableRow {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Verlintas/OpenTraduko")),
                    )
                },
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun LanguageSettingRow(
    title: String,
    selected: Language,
    onSelect: (Language) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            LanguagePicker(label = "", selected = selected, onSelect = onSelect)
        },
    )
}

@Composable
private fun duplexModeLabel(mode: DuplexMode): String = when (mode) {
    DuplexMode.AUTO -> stringResource(R.string.settings_duplex_auto)
    DuplexMode.HALF -> stringResource(R.string.settings_duplex_half)
    DuplexMode.FULL -> stringResource(R.string.settings_duplex_full)
}

@Composable
private fun textScaleLabel(scale: TextScale): String = when (scale) {
    TextScale.SMALL -> stringResource(R.string.settings_text_small)
    TextScale.MEDIUM -> stringResource(R.string.settings_text_medium)
    TextScale.LARGE -> stringResource(R.string.settings_text_large)
}

@Composable
private fun OpenAiSettingsFields(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(settings.openAiBaseUrl) }
    var apiKey by remember { mutableStateOf(settings.openAiApiKey) }
    var model by remember { mutableStateOf(settings.openAiModel) }
    var synced by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!synced) {
            baseUrl = settings.openAiBaseUrl
            apiKey = settings.openAiApiKey
            model = settings.openAiModel
            synced = true
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { value ->
                baseUrl = value
                onUpdate { it.copy(openAiBaseUrl = value) }
            },
            label = { Text(stringResource(R.string.settings_openai_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { value ->
                model = value
                onUpdate { it.copy(openAiModel = value) }
            },
            label = { Text(stringResource(R.string.settings_openai_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { value ->
                apiKey = value
                onUpdate { it.copy(openAiApiKey = value) }
            },
            label = { Text(stringResource(R.string.settings_openai_api_key)) },
            singleLine = true,
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (keyVisible) R.string.action_hide_api_key else R.string.action_show_api_key,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_openai_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
