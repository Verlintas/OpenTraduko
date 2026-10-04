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

package com.opentraduko.app.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
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
fun ConversationScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val state by viewModel.controllerState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val requestPermission = rememberPermissionRequester()

    KeepScreenOn(enabled = state.running && settings.keepScreenOn)

    val myLang = settings.conversationLangA
    val partnerLang = settings.conversationLangB
    val scale = settings.textScale.multiplier

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val runningConversation = state.running && state.mode == Mode.CONVERSATION
            if (runningConversation) {
                ConversationPanel(
                    segment = state.segments.lastOrNull { it.sourceLang == partnerLang },
                    language = partnerLang,
                    rotated = true,
                    active = state.activeLang == partnerLang,
                    level = if (state.activeLang == partnerLang) state.level else 0f,
                    speaking = state.activeLang == partnerLang && state.speaking,
                    dimmed = state.paused,
                    textScale = scale,
                    onTap = { viewModel.activateLanguage(partnerLang) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                SessionControls(
                    state = state,
                    onStart = { requestPermission { viewModel.start(Mode.CONVERSATION) } },
                    onPauseResume = { if (state.paused) viewModel.resume() else viewModel.pause() },
                    onStop = viewModel::stop,
                    onToggleTts = viewModel::toggleTts,
                    onSwitchLanguage = viewModel::switchActiveLanguage,
                    allowSwitch = true,
                )
                ConversationPanel(
                    segment = state.segments.lastOrNull { it.sourceLang == myLang },
                    language = myLang,
                    rotated = false,
                    active = state.activeLang == myLang,
                    level = if (state.activeLang == myLang) state.level else 0f,
                    speaking = state.activeLang == myLang && state.speaking,
                    dimmed = state.paused,
                    textScale = scale,
                    onTap = { viewModel.activateLanguage(myLang) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                if (state.running) {
                    RunningOtherModeBanner(state = state, onStop = viewModel::stop)
                }
                ConversationIdle(
                    myLang = myLang,
                    partnerLang = partnerLang,
                    modelInstalled = viewModel.isModelInstalled(myLang) && viewModel.isModelInstalled(partnerLang),
                    onSelectMy = { language -> viewModel.updateSettings { it.copy(conversationLangA = language) } },
                    onSelectPartner = { language -> viewModel.updateSettings { it.copy(conversationLangB = language) } },
                    onStart = { requestPermission { viewModel.start(Mode.CONVERSATION) } },
                    onOpenModels = onOpenModels,
                )
            }
        }
    }
}

@Composable
private fun ConversationPanel(
    segment: LiveSegment?,
    language: Language,
    rotated: Boolean,
    active: Boolean,
    level: Float,
    speaking: Boolean,
    dimmed: Boolean,
    textScale: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .background(background)
            .clickable(onClick = onTap)
            .alpha(if (dimmed) 0.55f else 1f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .then(if (rotated) Modifier.rotate(180f) else Modifier),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = segment?.sourceText.orEmpty(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = (22 * textScale).sp,
                    lineHeight = (30 * textScale).sp,
                ),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = segment?.translatedText.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (18 * textScale).sp,
                    lineHeight = (26 * textScale).sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        if (active) {
            LevelMeter(
                level = level,
                speaking = speaking,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SessionControls(
    state: InterpretationState,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onToggleTts: () -> Unit,
    onSwitchLanguage: () -> Unit,
    allowSwitch: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.running) {
                FilledIconButton(onClick = onStart, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.action_start), modifier = Modifier.size(30.dp))
                }
            } else {
                FilledIconButton(onClick = onPauseResume, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(
                            if (state.paused) R.string.action_resume else R.string.action_pause,
                        ),
                    )
                }
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
                }
                IconButton(onClick = onToggleTts) {
                    Icon(
                        imageVector = if (state.ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = stringResource(
                            if (state.ttsEnabled) R.string.action_tts_on else R.string.action_tts_off,
                        ),
                    )
                }
                if (allowSwitch) {
                    IconButton(onClick = onSwitchLanguage, enabled = !state.paused) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.action_switch_speaker))
                    }
                }
                if (state.ttsSpeaking) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
        state.running && !state.fullDuplex -> Text(
            text = stringResource(R.string.hint_headphones),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.running -> Text(
            text = stringResource(R.string.hint_tap_panel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RunningOtherModeBanner(state: InterpretationState, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(
                if (state.mode == Mode.LISTENING) R.string.banner_listening_running else R.string.banner_conversation_running,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onStop) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
        }
    }
}

@Composable
private fun ConversationIdle(
    myLang: Language,
    partnerLang: Language,
    modelInstalled: Boolean,
    onSelectMy: (Language) -> Unit,
    onSelectPartner: (Language) -> Unit,
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
            text = stringResource(R.string.conversation_idle_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        LanguagePicker(
            label = stringResource(R.string.language_my),
            selected = myLang,
            onSelect = onSelectMy,
            modifier = Modifier.fillMaxWidth(),
        )
        LanguagePicker(
            label = stringResource(R.string.language_partner),
            selected = partnerLang,
            onSelect = onSelectPartner,
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
