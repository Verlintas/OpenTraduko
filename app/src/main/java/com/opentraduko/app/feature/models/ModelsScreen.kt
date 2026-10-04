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

package com.opentraduko.app.feature.models

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentraduko.app.R
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.data.AppContainer
import com.opentraduko.app.engine.asr.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: ModelsViewModel = viewModel(factory = ModelsViewModel.factory(container))
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val mlkitStates by viewModel.mlkitStates.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var mirror by remember(settings.modelMirrorBaseUrl) { mutableStateOf(settings.modelMirrorBaseUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                SectionHeader(stringResource(R.string.models_vosk_section))
                HintText(stringResource(R.string.models_vosk_hint))
            }
            items(Language.entries) { language ->
                VoskModelRow(
                    language = language,
                    installed = language in installed,
                    state = downloads[language.voskModelName] ?: DownloadState.Idle,
                    onDownload = { viewModel.downloadVosk(language) },
                    onDelete = { viewModel.deleteVosk(language) },
                )
                HorizontalDivider()
            }
            item {
                SectionHeader(stringResource(R.string.models_mlkit_section))
                HintText(stringResource(R.string.models_mlkit_hint))
            }
            items(Language.entries) { language ->
                MlKitModelRow(
                    language = language,
                    state = mlkitStates[language] ?: MlKitModelState.UNKNOWN,
                    onDownload = { viewModel.downloadMlKit(language) },
                )
            }
            item {
                HorizontalDivider()
                SectionHeader(stringResource(R.string.models_mirror_label))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = mirror,
                        onValueChange = { mirror = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.models_mirror_label)) },
                        supportingText = { Text(stringResource(R.string.models_mirror_hint)) },
                    )
                    OutlinedButton(
                        onClick = { viewModel.updateMirror(mirror) },
                        enabled = mirror.trim() != settings.modelMirrorBaseUrl,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            item {
                HintText(
                    text = stringResource(R.string.models_mlkit_google_note),
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun VoskModelRow(
    language: Language,
    installed: Boolean,
    state: DownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text("${language.nativeName} · ${language.englishName}") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(language.voskModelName, style = MaterialTheme.typography.bodySmall)
                when (state) {
                    is DownloadState.Downloading -> {
                        val progress = if (state.totalBytes > 0) {
                            (state.bytesRead.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${Formatter.formatShortFileSize(context, state.bytesRead)} / " +
                                Formatter.formatShortFileSize(context, state.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    DownloadState.Extracting -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.models_status_extracting), style = MaterialTheme.typography.bodySmall)
                    }
                    is DownloadState.Failed -> Text(
                        text = stringResource(R.string.models_status_failed) + ": ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    DownloadState.Idle -> Text(
                        text = if (installed) {
                            stringResource(R.string.models_status_installed)
                        } else {
                            Formatter.formatShortFileSize(context, language.voskModelSizeBytes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = {
            when {
                installed && state == DownloadState.Idle -> Row {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.models_action_delete))
                    }
                }
                state is DownloadState.Downloading || state == DownloadState.Extracting -> Unit
                else -> IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.models_action_download))
                }
            }
        },
    )
}

@Composable
private fun MlKitModelRow(
    language: Language,
    state: MlKitModelState,
    onDownload: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(language.nativeName) },
        supportingContent = {
            when (state) {
                MlKitModelState.DOWNLOADED -> Text(stringResource(R.string.models_mlkit_status_downloaded))
                MlKitModelState.MISSING -> Text(stringResource(R.string.models_mlkit_status_missing))
                MlKitModelState.DOWNLOADING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                MlKitModelState.FAILED -> Text(
                    text = stringResource(R.string.models_status_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                MlKitModelState.UNKNOWN -> Text(stringResource(R.string.models_mlkit_checking))
            }
        },
        trailingContent = {
            when (state) {
                MlKitModelState.DOWNLOADED -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                MlKitModelState.MISSING, MlKitModelState.FAILED -> IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.models_action_download))
                }
                else -> Unit
            }
        },
    )
}

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
private fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}
