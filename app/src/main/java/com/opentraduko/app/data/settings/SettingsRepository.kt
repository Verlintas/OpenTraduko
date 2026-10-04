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

package com.opentraduko.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opentraduko.app.core.model.DuplexMode
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.TextScale
import com.opentraduko.app.core.model.TranslationEngineKind
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences[KEY_CONVERSATION_LANG_A] = updated.conversationLangA.tag
            preferences[KEY_CONVERSATION_LANG_B] = updated.conversationLangB.tag
            preferences[KEY_LISTENING_SOURCE] = updated.listeningSource.tag
            preferences[KEY_LISTENING_TARGET] = updated.listeningTarget.tag
            preferences[KEY_TTS_ENABLED] = updated.ttsEnabled
            preferences[KEY_SPEECH_RATE] = updated.speechRate
            preferences[KEY_DUPLEX_MODE] = updated.duplexMode.name
            preferences[KEY_TEXT_SCALE] = updated.textScale.name
            preferences[KEY_MODEL_MIRROR] = updated.modelMirrorBaseUrl
            preferences[KEY_USE_HF_MIRROR] = updated.useHfMirror
            preferences[KEY_KEEP_SCREEN_ON] = updated.keepScreenOn
            preferences[KEY_TRANSLATION_ENGINE] = updated.translationEngine.name
            preferences[KEY_OPENAI_BASE_URL] = updated.openAiBaseUrl
            preferences[KEY_OPENAI_API_KEY] = updated.openAiApiKey
            preferences[KEY_OPENAI_MODEL] = updated.openAiModel
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            conversationLangA = Language.fromTag(this[KEY_CONVERSATION_LANG_A]) ?: defaults.conversationLangA,
            conversationLangB = Language.fromTag(this[KEY_CONVERSATION_LANG_B]) ?: defaults.conversationLangB,
            listeningSource = Language.fromTag(this[KEY_LISTENING_SOURCE]) ?: defaults.listeningSource,
            listeningTarget = Language.fromTag(this[KEY_LISTENING_TARGET]) ?: defaults.listeningTarget,
            ttsEnabled = this[KEY_TTS_ENABLED] ?: defaults.ttsEnabled,
            speechRate = this[KEY_SPEECH_RATE] ?: defaults.speechRate,
            duplexMode = this[KEY_DUPLEX_MODE]?.let { runCatching { DuplexMode.valueOf(it) }.getOrNull() }
                ?: defaults.duplexMode,
            textScale = this[KEY_TEXT_SCALE]?.let { runCatching { TextScale.valueOf(it) }.getOrNull() }
                ?: defaults.textScale,
            modelMirrorBaseUrl = this[KEY_MODEL_MIRROR]?.takeIf { it.isNotBlank() } ?: defaults.modelMirrorBaseUrl,
            useHfMirror = this[KEY_USE_HF_MIRROR] ?: defaults.useHfMirror,
            keepScreenOn = this[KEY_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            translationEngine = this[KEY_TRANSLATION_ENGINE]
                ?.let { runCatching { TranslationEngineKind.valueOf(it) }.getOrNull() }
                ?: defaults.translationEngine,
            openAiBaseUrl = this[KEY_OPENAI_BASE_URL] ?: defaults.openAiBaseUrl,
            openAiApiKey = this[KEY_OPENAI_API_KEY] ?: defaults.openAiApiKey,
            openAiModel = this[KEY_OPENAI_MODEL] ?: defaults.openAiModel,
        )
    }

    private companion object {
        val KEY_CONVERSATION_LANG_A = stringPreferencesKey("conversation_lang_a")
        val KEY_CONVERSATION_LANG_B = stringPreferencesKey("conversation_lang_b")
        val KEY_LISTENING_SOURCE = stringPreferencesKey("listening_source")
        val KEY_LISTENING_TARGET = stringPreferencesKey("listening_target")
        val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_DUPLEX_MODE = stringPreferencesKey("duplex_mode")
        val KEY_TEXT_SCALE = stringPreferencesKey("text_scale")
        val KEY_MODEL_MIRROR = stringPreferencesKey("model_mirror")
        val KEY_USE_HF_MIRROR = booleanPreferencesKey("use_hf_mirror")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val KEY_OPENAI_MODEL = stringPreferencesKey("openai_model")
    }
}
