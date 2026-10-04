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

import com.opentraduko.app.core.model.DuplexMode
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.TextScale
import com.opentraduko.app.core.model.TranslationEngineKind

data class AppSettings(
    val conversationLangA: Language = Language.CHINESE,
    val conversationLangB: Language = Language.ENGLISH,
    val listeningSource: Language = Language.ENGLISH,
    val listeningTarget: Language = Language.CHINESE,
    val ttsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val duplexMode: DuplexMode = DuplexMode.AUTO,
    val textScale: TextScale = TextScale.MEDIUM,
    val modelMirrorBaseUrl: String = DEFAULT_MIRROR,
    val useHfMirror: Boolean = false,
    val keepScreenOn: Boolean = true,
    val translationEngine: TranslationEngineKind = TranslationEngineKind.ML_KIT,
    val openAiBaseUrl: String = DEFAULT_OPENAI_BASE_URL,
    val openAiApiKey: String = "",
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
) {
    companion object {
        const val DEFAULT_MIRROR = "https://alphacephei.com/vosk/models"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_OPENAI_MODEL = "deepseek-chat"
    }
}
