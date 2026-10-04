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

package com.opentraduko.app.core.model

import kotlinx.serialization.Serializable

enum class Mode { CONVERSATION, LISTENING }

enum class DuplexMode { AUTO, HALF, FULL }

enum class TextScale(val multiplier: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.25f),
}

data class SessionConfig(
    val mode: Mode,
    val langA: Language,
    val langB: Language,
    val ttsEnabled: Boolean,
    val duplexMode: DuplexMode,
    val speechRate: Float,
)

/** A segment currently on screen or being recognized. */
data class LiveSegment(
    val id: Long,
    val sourceLang: Language,
    val targetLang: Language,
    val sourceText: String,
    val translatedText: String,
    val isFinal: Boolean,
    val startedAtMs: Long,
)

@Serializable
data class HistorySegment(
    val timestampMs: Long,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val translatedText: String,
)

@Serializable
data class HistorySession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val mode: String,
    val langA: String,
    val langB: String,
    val segments: List<HistorySegment>,
)
