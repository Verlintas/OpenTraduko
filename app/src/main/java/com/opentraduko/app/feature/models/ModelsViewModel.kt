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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.data.AppContainer
import com.opentraduko.app.data.settings.AppSettings
import com.opentraduko.app.engine.asr.DownloadState
import com.opentraduko.app.engine.mt.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MlKitModelState { UNKNOWN, MISSING, DOWNLOADING, DOWNLOADED, FAILED }

class ModelsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    val downloads: StateFlow<Map<String, DownloadState>> = container.modelManager.downloads

    private val _installed = MutableStateFlow(container.modelManager.installedLanguages())
    val installed: StateFlow<List<Language>> = _installed.asStateFlow()

    private val _mlkitStates = MutableStateFlow<Map<Language, MlKitModelState>>(emptyMap())
    val mlkitStates: StateFlow<Map<Language, MlKitModelState>> = _mlkitStates.asStateFlow()

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        refreshMlKit()
    }

    fun downloadVosk(language: Language) {
        viewModelScope.launch {
            container.modelManager.download(language)
            refreshInstalled()
        }
    }

    fun deleteVosk(language: Language) {
        container.modelManager.delete(language)
        refreshInstalled()
    }

    fun refreshInstalled() {
        _installed.value = container.modelManager.installedLanguages()
    }

    fun refreshMlKit() {
        viewModelScope.launch {
            Language.entries.forEach { language ->
                val downloaded = runCatching {
                    RemoteModelManager.getInstance()
                        .isModelDownloaded(TranslateRemoteModel.Builder(language.tag).build())
                        .await()
                }.getOrDefault(false)
                _mlkitStates.update {
                    it + (language to if (downloaded) MlKitModelState.DOWNLOADED else MlKitModelState.MISSING)
                }
            }
        }
    }

    fun downloadMlKit(language: Language) {
        _mlkitStates.update { it + (language to MlKitModelState.DOWNLOADING) }
        viewModelScope.launch {
            val success = runCatching {
                RemoteModelManager.getInstance()
                    .download(
                        TranslateRemoteModel.Builder(language.tag).build(),
                        DownloadConditions.Builder().build(),
                    )
                    .await()
            }.isSuccess
            _mlkitStates.update {
                it + (language to if (success) MlKitModelState.DOWNLOADED else MlKitModelState.FAILED)
            }
        }
    }

    fun updateMirror(url: String) {
        viewModelScope.launch {
            container.settings.update { it.copy(modelMirrorBaseUrl = url.trim()) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                )
                ModelsViewModel(application, container)
            }
        }
    }
}
