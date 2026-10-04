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

package com.opentraduko.app.feature.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.opentraduko.app.core.model.HistorySession
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.Mode
import com.opentraduko.app.core.model.SessionConfig
import com.opentraduko.app.core.pipeline.InterpretationState
import com.opentraduko.app.data.AppContainer
import com.opentraduko.app.data.settings.AppSettings
import com.opentraduko.app.service.InterpretationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    val controllerState: StateFlow<InterpretationState> = container.controller.state

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val historySessions: StateFlow<List<HistorySession>> = container.history.sessions

    fun start(mode: Mode) {
        val current = settings.value
        val config = when (mode) {
            Mode.CONVERSATION -> SessionConfig(
                mode = mode,
                langA = current.conversationLangA,
                langB = current.conversationLangB,
                ttsEnabled = current.ttsEnabled,
                duplexMode = current.duplexMode,
                speechRate = current.speechRate,
            )
            Mode.LISTENING -> SessionConfig(
                mode = mode,
                langA = current.listeningSource,
                langB = current.listeningTarget,
                ttsEnabled = current.ttsEnabled,
                duplexMode = current.duplexMode,
                speechRate = current.speechRate,
            )
        }
        InterpretationService.start(getApplication(), config)
    }

    fun stop() = InterpretationService.stop(getApplication())

    fun pause() = InterpretationService.pause(getApplication())

    fun resume() = InterpretationService.resume(getApplication())

    fun toggleTts() = container.controller.toggleTts()

    fun activateLanguage(language: Language) = container.controller.activateLanguage(language)

    fun switchActiveLanguage() = container.controller.switchActiveLanguage()

    fun stopSpeaking() = container.controller.stopSpeaking()

    fun isModelInstalled(language: Language): Boolean = container.modelManager.isInstalled(language)

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun exportText(sessionId: String): String? =
        container.history.session(sessionId)?.let { container.history.exportText(it) }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch { container.history.delete(sessionId) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                )
                SessionViewModel(application, container)
            }
        }
    }
}
