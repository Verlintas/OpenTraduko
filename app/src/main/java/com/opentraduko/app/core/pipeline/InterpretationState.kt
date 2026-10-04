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

package com.opentraduko.app.core.pipeline

import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.LiveSegment
import com.opentraduko.app.core.model.Mode

enum class ControllerError {
    MODEL_MISSING,
    MODEL_LOAD_FAILED,
    AUDIO_FAILED,
}

sealed interface ControllerStatus {
    data object LoadingModel : ControllerStatus

    data object Ready : ControllerStatus

    data class Error(val reason: ControllerError) : ControllerStatus
}

data class InterpretationState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val mode: Mode = Mode.LISTENING,
    val activeLang: Language = Language.CHINESE,
    val langA: Language = Language.CHINESE,
    val langB: Language = Language.ENGLISH,
    val segments: List<LiveSegment> = emptyList(),
    val level: Float = 0f,
    val speaking: Boolean = false,
    val ttsEnabled: Boolean = true,
    val ttsSpeaking: Boolean = false,
    val ttsReady: Boolean = true,
    val fullDuplex: Boolean = false,
    val status: ControllerStatus? = null,
)
