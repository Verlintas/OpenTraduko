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

package com.opentraduko.app.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentraduko.app.R
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.LiveSegment
import com.opentraduko.app.core.model.Mode
import com.opentraduko.app.core.pipeline.ControllerError
import com.opentraduko.app.core.pipeline.ControllerStatus
import com.opentraduko.app.core.pipeline.InterpretationState
import com.opentraduko.app.data.AppContainer
import com.opentraduko.app.feature.session.SessionViewModel
import com.opentraduko.app.ui.components.KeepScreenOn
import com.opentraduko.app.ui.components.LanguagePicker
import com.opentraduko.app.ui.components.LevelMeter
import com.opentraduko.app.ui.components.rememberPermissionRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val state by viewModel.controllerState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val requestPermission = rememberPermissionRequester()

    KeepScreenOn(enabled = state.running && settings.keepScreenOn)

    val sourceLang = settings.listeningSource
    val targetLang = settings.listeningTarget
    val scale = settings.textScale.multiplier
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.listening_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${sourceLang.nativeName} → ${targetLang.nativeName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.running && state.mode == Mode.LISTENING) {
                        IconButton(onClick = viewModel::toggleTts) {
                            Icon(
                                imageVector = if (state.ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = stringResource(
                                    if (state.ttsEnabled) R.string.action_tts_on else R.string.action_tts_off,
                                ),
                            )
                        }
                        IconButton(onClick = { if (state.paused) viewModel.resume() else viewModel.pause() }) {
                            Icon(
                                imageVector = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = stringResource(
                                    if (state.paused) R.string.action_resume else R.string.action_pause,
                                ),
                            )
                        }
                        IconButton(onClick = viewModel::stop) {
                            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val runningListening = state.running && state.mode == Mode.LISTENING
            if (runningListening) {
                if (state.segments.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.listening_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.segments, key = { it.id }) { segment ->
                            ListeningItem(segment = segment, textScale = scale)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    LaunchedEffect(state.segments.size) {
                        if (state.segments.isNotEmpty()) {
                            listState.animateScrollToItem(state.segments.lastIndex)
                        }
                    }
                }
                BottomBar(state = state, viewModel = viewModel)
            } else {
                if (state.running) {
                    RunningOtherModeBanner(state = state, onStop = viewModel::stop)
                }
                ListeningIdle(
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    modelInstalled = viewModel.isModelInstalled(sourceLang),
                    onSelectSource = { language -> viewModel.updateSettings { it.copy(listeningSource = language) } },
                    onSelectTarget = { language -> viewModel.updateSettings { it.copy(listeningTarget = language) } },
                    onStart = { requestPermission { viewModel.start(Mode.LISTENING) } },
                    onOpenModels = onOpenModels,
                )
            }
        }
    }
}

@Composable
private fun ListeningItem(segment: LiveSegment, textScale: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = segment.sourceText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (17 * textScale).sp,
                lineHeight = (24 * textScale).sp,
            ),
            color = if (segment.isFinal) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = segment.translatedText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (17 * textScale).sp,
                lineHeight = (24 * textScale).sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BottomBar(state: InterpretationState, viewModel: SessionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LevelMeter(
            level = state.level,
            speaking = state.speaking,
            modifier = Modifier.fillMaxWidth(),
        )
        StatusLine(state)
    }
}

@Composable
private fun StatusLine(state: InterpretationState) {
    val text = when (val status = state.status) {
        ControllerStatus.LoadingModel -> stringResource(R.string.status_preparing)
        ControllerStatus.Ready -> null
        is ControllerStatus.Error -> when (status.reason) {
            ControllerError.MODEL_MISSING -> stringResource(R.string.error_model_missing)
            ControllerError.MODEL_LOAD_FAILED -> stringResource(R.string.error_model_load_failed)
            ControllerError.AUDIO_FAILED -> stringResource(R.string.error_audio_failed)
        }
        null -> null
    }
    when {
        text != null -> Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        !state.ttsReady -> Text(
            text = stringResource(R.string.error_tts_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        !state.translationReady -> Text(
            text = translationStatusText(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        state.running && !state.fullDuplex -> Text(
            text = stringResource(R.string.hint_headphones),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun translationStatusText(state: InterpretationState): String {
    val base = stringResource(R.string.status_translation_not_ready)
    val detail = state.translationError?.takeIf { it.isNotBlank() }
    return if (detail == null) base else "$base ($detail)"
}

@Composable
private fun RunningOtherModeBanner(state: InterpretationState, onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(
                if (state.mode == Mode.CONVERSATION) R.string.banner_conversation_running else R.string.banner_listening_running,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onStop) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
        }
    }
}

@Composable
private fun ListeningIdle(
    sourceLang: Language,
    targetLang: Language,
    modelInstalled: Boolean,
    onSelectSource: (Language) -> Unit,
    onSelectTarget: (Language) -> Unit,
    onStart: () -> Unit,
    onOpenModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.listening_idle_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        LanguagePicker(
            label = stringResource(R.string.language_source),
            selected = sourceLang,
            onSelect = onSelectSource,
            modifier = Modifier.fillMaxWidth(),
        )
        LanguagePicker(
            label = stringResource(R.string.language_target),
            selected = targetLang,
            onSelect = onSelectTarget,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!modelInstalled) {
            Text(
                text = stringResource(R.string.error_model_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenModels) { Text(stringResource(R.string.home_models)) }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, enabled = modelInstalled) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_start))
        }
    }
}
