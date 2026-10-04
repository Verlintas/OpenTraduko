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
import com.opentraduko.app.engine.mt.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns streaming ASR partials into live translation for one utterance.
 *
 * The stable prefix (part common to two consecutive partials) is translated
 * periodically and *replaces* the previous live translation, so the subtitle
 * grows with the speech. On final the whole utterance is translated once more
 * and reported as final. This works without any punctuation, which Vosk does
 * not emit.
 */
class LiveTranslationScheduler(
    private val translator: TranslationEngine,
    private val from: Language,
    private val to: Language,
    private val scope: CoroutineScope,
    private val onUpdate: (sourceText: String, translatedText: String, isFinal: Boolean) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    private val intervalMs: Long = 600L,
) {

    private val lock = Any()
    private val mutex = Mutex()

    private var lastPartial = ""
    private var stablePrefix = ""
    private var lastRequestedPrefix = ""

    @Volatile
    private var lastTranslation = ""

    private var worker: Job? = null

    @Volatile
    private var closed = false

    fun onPartial(rawText: String) {
        synchronized(lock) {
            if (closed) return
            val stable = commonPrefix(rawText, lastPartial)
            lastPartial = rawText
            if (stable.length > stablePrefix.length) stablePrefix = stable
            if (worker?.isActive != true) {
                worker = scope.launch { translateLoop() }
            }
        }
    }

    /**
     * Finalizes the utterance, translating the full text and suspending until
     * the translation is done. Returns the translation, or the last live one
     * if the engine failed.
     */
    suspend fun onFinal(finalText: String): String {
        val running = synchronized(lock) {
            closed = true
            worker.also { worker = null }
        }
        running?.cancelAndJoin()

        val text = finalText.trim()
        val result = runCatching { mutex.withLock { translator.translate(text, from, to) } }
        return if (result.isSuccess) {
            val translation = result.getOrThrow()
            lastTranslation = translation
            onUpdate(text, translation, true)
            translation
        } else {
            val error = result.exceptionOrNull()
            if (error != null && error !is CancellationException) onError(error)
            onUpdate(text, lastTranslation, true)
            lastTranslation
        }
    }

    fun dispose() {
        val running = synchronized(lock) {
            closed = true
            worker.also { worker = null }
        }
        running?.cancel()
    }

    private suspend fun translateLoop() {
        while (true) {
            delay(intervalMs)
            val prefix = synchronized(lock) {
                if (closed) return
                stablePrefix
            }
            if (prefix.isBlank() || prefix == lastRequestedPrefix) continue
            val result = runCatching { mutex.withLock { translator.translate(prefix, from, to) } }
            if (result.isSuccess) {
                lastRequestedPrefix = prefix
                lastTranslation = result.getOrThrow()
                onUpdate(prefix, lastTranslation, false)
            } else {
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                if (error != null) onError(error)
            }
        }
    }

    private fun commonPrefix(a: String, b: String): String {
        val limit = minOf(a.length, b.length)
        var index = 0
        while (index < limit && a[index] == b[index]) index++
        return a.substring(0, index)
    }
}
