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

package com.opentraduko.app.engine.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.opentraduko.app.core.model.Language
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * System TTS engine. Initialization is asynchronous; [prepare] waits for the
 * engine and selects the requested voice.
 */
class AndroidTtsEngine(context: Context) : TtsEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val initialization = CompletableFuture<Boolean>()
    private val activeUtterances = AtomicInteger(0)

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var languageReady = false

    private val tts = TextToSpeech(context.applicationContext) { status ->
        initialization.complete(status == TextToSpeech.SUCCESS)
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                finishUtterance()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishUtterance()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishUtterance()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finishUtterance()
            }
        })
    }

    override suspend fun prepare(language: Language, speechRate: Float): Boolean {
        val initialized = withTimeoutOrNull(4000) {
            withContext(Dispatchers.IO) { initialization.get() }
        } ?: return false
        if (!initialized) return false
        return withContext(Dispatchers.Main) {
            tts.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
            val result = tts.setLanguage(language.locale)
            languageReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            languageReady
        }
    }

    override fun speak(text: String, language: Language) {
        if (text.isBlank() || !languageReady) return
        mainHandler.post {
            val utteranceId = "opentraduko-${System.nanoTime()}"
            activeUtterances.incrementAndGet()
            val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result == TextToSpeech.ERROR) finishUtterance()
        }
    }

    override fun stop() {
        activeUtterances.set(0)
        _speaking.value = false
        mainHandler.post { runCatching { tts.stop() } }
    }

    override fun shutdown() {
        activeUtterances.set(0)
        _speaking.value = false
        mainHandler.post { runCatching { tts.shutdown() } }
    }

    private fun finishUtterance() {
        if (activeUtterances.decrementAndGet() <= 0) {
            activeUtterances.set(0)
            _speaking.value = false
        }
    }
}
