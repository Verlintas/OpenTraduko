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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Captures microphone audio as 16 kHz mono 16-bit PCM and emits 50 ms chunks.
 * Devices that cannot record at 16 kHz fall back to 48 kHz with 3x decimation,
 * keeping the pipeline sample rate at 16 kHz.
 *
 * [muted] drops chunks at the source (used for half-duplex echo protection);
 * the recorder itself keeps running so resuming has no click/startup delay.
 */
class AudioCapture {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_MS = 50
        private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000
    }

    private val _audio = MutableSharedFlow<ShortArray>(extraBufferCapacity = 64)
    val audio: SharedFlow<ShortArray> = _audio.asSharedFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile
    var muted: Boolean = false

    @Volatile
    private var running = false

    private var recorder: AudioRecord? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val (record, decimation) = createRecorder() ?: return false
        recorder = record
        running = true
        record.startRecording()
        thread = Thread({ loop(record, decimation) }, "opentraduko-audio").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        _level.value = 0f
    }

    private fun createRecorder(): Pair<AudioRecord, Int>? {
        val candidates = listOf(16000 to 1, 48000 to 3)
        for ((rate, decimation) in candidates) {
            val result = runCatching {
                val minBuffer = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuffer <= 0) return@runCatching null
                val bufferSize = max(minBuffer, rate * 2 * CHUNK_MS / 1000 * 2)
                val source = MediaRecorder.AudioSource.VOICE_RECOGNITION
                var record = AudioRecord(
                    source,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                }
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    null
                } else {
                    record to decimation
                }
            }.getOrElse { null }
            if (result != null) return result
        }
        return null
    }

    private fun loop(record: AudioRecord, decimation: Int) {
        val rawChunk = ShortArray(CHUNK_SAMPLES * decimation)
        while (running) {
            val read = runCatching { record.read(rawChunk, 0, rawChunk.size) }.getOrDefault(0)
            if (read <= 0) continue
            updateLevel(rawChunk, read)
            if (muted) continue
            val chunk = if (decimation == 1) rawChunk.copyOf(read) else decimate(rawChunk, read, decimation)
            if (chunk.isNotEmpty()) _audio.tryEmit(chunk)
        }
    }

    private fun updateLevel(buffer: ShortArray, length: Int) {
        var sum = 0.0
        for (i in 0 until length) {
            val v = buffer[i] / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / length)
        val db = 20 * log10(rms + 1e-6)
        val normalized = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
        _level.value = max(normalized, _level.value * 0.75f)
    }

    private fun decimate(input: ShortArray, length: Int, factor: Int): ShortArray {
        val outputLength = length / factor
        val output = ShortArray(outputLength)
        for (i in 0 until outputLength) {
            val base = i * factor
            var sum = 0
            for (j in 0 until factor) sum += input[base + j]
            output[i] = (sum / factor).toShort()
        }
        return output
    }
}
