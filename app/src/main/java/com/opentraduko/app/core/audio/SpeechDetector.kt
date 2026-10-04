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

package com.opentraduko.app.core.audio

/**
 * Hysteresis based voice activity detector fed with normalized audio levels.
 * Used for the UI speaking indicator, not for segmentation (Vosk endpoints).
 */
class SpeechDetector(
    private val onThreshold: Float = 0.25f,
    private val offThreshold: Float = 0.12f,
    private val onHangChunks: Int = 3,
    private val offHangChunks: Int = 10,
) {
    var isSpeaking: Boolean = false
        private set

    private var onCount = 0
    private var offCount = 0

    fun accept(level: Float): Boolean {
        if (isSpeaking) {
            if (level < offThreshold) {
                offCount++
                if (offCount >= offHangChunks) {
                    isSpeaking = false
                    onCount = 0
                }
            } else {
                offCount = 0
            }
        } else {
            if (level >= onThreshold) {
                onCount++
                if (onCount >= onHangChunks) {
                    isSpeaking = true
                    offCount = 0
                }
            } else {
                onCount = 0
            }
        }
        return isSpeaking
    }

    fun reset() {
        isSpeaking = false
        onCount = 0
        offCount = 0
    }
}
