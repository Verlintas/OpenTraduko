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

package com.opentraduko.app.engine.asr

import com.opentraduko.app.core.model.AsrEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.vosk.Model as VoskModel
import org.vosk.Recognizer

/**
 * Vosk streaming recognizer running fully on device. The model directory must
 * contain an unpacked vosk-model-small-* directory.
 */
class VoskAsrEngine(modelPath: String) : AsrEngine {

    override val sampleRate: Float = 16000f

    private val model = VoskModel(modelPath)
    private val recognizer = Recognizer(model, sampleRate)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var closed = false

    @Synchronized
    override fun feed(samples: ShortArray): AsrEvent {
        if (closed) return AsrEvent.Partial("")
        val bytes = toLittleEndian(samples)
        return if (recognizer.acceptWaveForm(bytes, bytes.size)) {
            val result = json.decodeFromString<VoskResult>(recognizer.result)
            AsrEvent.Final(result.text.trim())
        } else {
            val partial = json.decodeFromString<VoskResult>(recognizer.partialResult)
            AsrEvent.Partial(partial.partial.trim())
        }
    }

    @Synchronized
    override fun finishUtterance(): AsrEvent {
        if (closed) return AsrEvent.Final("")
        val result = json.decodeFromString<VoskResult>(recognizer.finalResult)
        return AsrEvent.Final(result.text.trim())
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { recognizer.close() }
        runCatching { model.close() }
    }

    private fun toLittleEndian(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        var index = 0
        for (sample in samples) {
            bytes[index++] = (sample.toInt() and 0xFF).toByte()
            bytes[index++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Serializable
    private data class VoskResult(
        val text: String = "",
        val partial: String = "",
    )
}
