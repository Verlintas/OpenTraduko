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

import android.content.Context
import com.opentraduko.app.core.audio.AudioCapture
import com.opentraduko.app.core.audio.AudioRouteMonitor
import com.opentraduko.app.core.audio.SpeechDetector
import com.opentraduko.app.core.model.AsrEvent
import com.opentraduko.app.core.model.DuplexMode
import com.opentraduko.app.core.model.HistorySegment
import com.opentraduko.app.core.model.HistorySession
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.LiveSegment
import com.opentraduko.app.core.model.Mode
import com.opentraduko.app.core.model.SessionConfig
import com.opentraduko.app.data.history.HistoryStore
import com.opentraduko.app.engine.asr.AsrEngine
import com.opentraduko.app.engine.asr.AsrModelManager
import com.opentraduko.app.engine.asr.VoskAsrEngine
import com.opentraduko.app.engine.mt.MlKitTranslationEngine
import com.opentraduko.app.engine.mt.TranslationEngine
import com.opentraduko.app.engine.tts.AndroidTtsEngine
import com.opentraduko.app.engine.tts.TtsEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates one interpretation session: microphone -> Vosk -> incremental
 * translation -> subtitles/TTS. Owned by the application container and driven
 * by the foreground service and UI.
 */
class InterpretationController(
    context: Context,
    private val historyStore: HistoryStore,
    private val modelManager: AsrModelManager,
    private val scope: CoroutineScope,
) {

    private val capture = AudioCapture()
    private val routeMonitor = AudioRouteMonitor(context)
    private val tts: TtsEngine = AndroidTtsEngine(context)
    private val speechDetector = SpeechDetector()
    private val translator: TranslationEngine = MlKitTranslationEngine()

    private val _state = MutableStateFlow(InterpretationState())
    val state: StateFlow<InterpretationState> = _state.asStateFlow()

    private val utteranceLock = Any()
    private val ttsLock = Any()

    private var config: SessionConfig? = null
    private var asr: AsrEngine? = null
    private var sessionScope: CoroutineScope? = null
    private var currentUtterance: Utterance? = null

    private var segmentCounter = 0L
    private var ttsEnabled = true
    private var preparedTtsLanguage: Language? = null

    @Volatile
    private var gateByTts = false

    @Volatile
    private var switching = false

    @Volatile
    private var paused = false

    private var sessionSegments = mutableListOf<HistorySegment>()
    private var sessionStartedAt = 0L
    private var sessionId = ""
    private var sessionMode = Mode.LISTENING
    private var sessionLangA = Language.CHINESE
    private var sessionLangB = Language.ENGLISH

    fun start(config: SessionConfig) {
        if (_state.value.running) return
        this.config = config
        ttsEnabled = config.ttsEnabled
        sessionSegments = mutableListOf()
        sessionStartedAt = System.currentTimeMillis()
        sessionId = SESSION_ID_FORMAT.format(Date(sessionStartedAt))
        sessionMode = config.mode
        sessionLangA = config.langA
        sessionLangB = config.langB
        _state.value = InterpretationState(
            running = true,
            mode = config.mode,
            activeLang = config.langA,
            langA = config.langA,
            langB = config.langB,
            ttsEnabled = config.ttsEnabled,
            status = ControllerStatus.LoadingModel,
        )
        val session = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        sessionScope = session
        session.launch { runSession(session) }
    }

    fun pause() {
        if (!_state.value.running || _state.value.paused) return
        paused = true
        speechDetector.reset()
        _state.update { it.copy(paused = true, speaking = false, level = 0f) }
    }

    fun resume() {
        if (!_state.value.running || !_state.value.paused) return
        paused = false
        speechDetector.reset()
        _state.update { it.copy(paused = false) }
    }

    fun toggleTts() {
        ttsEnabled = !ttsEnabled
        if (!ttsEnabled) {
            tts.stop()
            releaseGate()
        }
        _state.update { it.copy(ttsEnabled = ttsEnabled) }
    }

    fun stopSpeaking() {
        tts.stop()
        releaseGate()
    }

    fun stop() = stopInternal(keepStatus = false)

    fun switchActiveLanguage() {
        val currentConfig = config ?: return
        val stateNow = _state.value
        if (stateNow.mode != Mode.CONVERSATION) return
        val next = if (stateNow.activeLang == currentConfig.langA) currentConfig.langB else currentConfig.langA
        activateLanguage(next)
    }

    fun activateLanguage(language: Language) {
        val currentConfig = config ?: return
        val stateNow = _state.value
        if (stateNow.mode != Mode.CONVERSATION || switching) return
        if (language == stateNow.activeLang) return
        if (language != currentConfig.langA && language != currentConfig.langB) return
        val session = sessionScope ?: return
        session.launch {
            switching = true
            try {
                flushCurrentUtterance()
                _state.update { it.copy(status = ControllerStatus.LoadingModel, activeLang = language) }
                val old = asr
                asr = null
                withContext(Dispatchers.IO) { runCatching { old?.close() } }
                if (!modelManager.isInstalled(language)) {
                    fail(ControllerError.MODEL_MISSING)
                    return@launch
                }
                val engine = withContext(Dispatchers.IO) {
                    runCatching { VoskAsrEngine(modelManager.modelDir(language).absolutePath) }.getOrNull()
                }
                if (engine == null) {
                    fail(ControllerError.MODEL_LOAD_FAILED)
                    return@launch
                }
                asr = engine
                speechDetector.reset()
                _state.update { it.copy(status = ControllerStatus.Ready) }
                scope.launch(Dispatchers.IO) { translator.ensureModels(language, targetOf(language)) }
            } finally {
                switching = false
            }
        }
    }

    private suspend fun runSession(session: CoroutineScope) {
        routeMonitor.start()
        session.launch {
            routeMonitor.headphonesConnected.collect { refreshFullDuplexState() }
        }
        session.launch {
            capture.level.collect { level ->
                val speaking = speechDetector.accept(level)
                val isPaused = paused
                _state.update { it.copy(level = level, speaking = speaking && !isPaused) }
            }
        }
        session.launch {
            tts.speaking.collect { speaking -> onTtsSpeakingChanged(speaking) }
        }
        if (!startPipeline()) return
        capture.audio.collect { samples -> onAudio(samples) }
    }

    private suspend fun startPipeline(): Boolean {
        val currentConfig = config ?: return false
        if (!modelManager.isInstalled(currentConfig.langA)) {
            fail(ControllerError.MODEL_MISSING)
            return false
        }
        val engine = withContext(Dispatchers.IO) {
            runCatching { VoskAsrEngine(modelManager.modelDir(currentConfig.langA).absolutePath) }.getOrNull()
        }
        if (engine == null) {
            fail(ControllerError.MODEL_LOAD_FAILED)
            return false
        }
        asr = engine
        scope.launch(Dispatchers.IO) { translator.ensureModels(currentConfig.langA, targetOf(currentConfig.langA)) }
        if (ttsEnabled) scope.launch { prepareTts(targetOf(currentConfig.langA)) }
        if (!capture.start()) {
            fail(ControllerError.AUDIO_FAILED)
            return false
        }
        refreshFullDuplexState()
        _state.update { it.copy(status = ControllerStatus.Ready) }
        return true
    }

    private fun onAudio(samples: ShortArray) {
        if (paused || switching || gateByTts) return
        val engine = asr ?: return
        val event = runCatching { engine.feed(samples) }
            .getOrElse { AsrEvent.Error(it.message ?: "asr failure") }
        when (event) {
            is AsrEvent.Partial -> onPartial(event.text)
            is AsrEvent.Final -> {
                val session = sessionScope ?: return
                session.launch { onFinal(event.text) }
            }
            is AsrEvent.Error -> _state.update {
                it.copy(status = ControllerStatus.Error(ControllerError.MODEL_LOAD_FAILED))
            }
        }
    }

    private fun onPartial(text: String) {
        if (text.isBlank()) return
        val activeLang = _state.value.activeLang
        val utterance = synchronized(utteranceLock) {
            currentUtterance ?: createUtterance(activeLang, targetOf(activeLang)).also {
                currentUtterance = it
            }
        }
        utterance.sourceText = text
        utterance.scheduler.onPartial(text)
        updateLive(utterance, isFinal = false)
    }

    private suspend fun onFinal(text: String) {
        val activeLang = _state.value.activeLang
        val utterance = synchronized(utteranceLock) {
            val pending = currentUtterance
                ?: if (text.isNotBlank()) createUtterance(activeLang, targetOf(activeLang)) else null
            currentUtterance = null
            pending
        } ?: return
        finishUtterance(utterance, text)
    }

    private suspend fun finishUtterance(utterance: Utterance, finalText: String) {
        utterance.sourceText = finalText
        val translatedText = runCatching { utterance.scheduler.onFinal(finalText) }
            .getOrDefault(utterance.translatedText)
        utterance.translatedText = translatedText
        updateLive(utterance, isFinal = true)
        if (finalText.isNotBlank()) {
            sessionSegments.add(
                HistorySegment(
                    timestampMs = System.currentTimeMillis(),
                    sourceLang = utterance.sourceLang.tag,
                    targetLang = utterance.targetLang.tag,
                    sourceText = finalText,
                    translatedText = translatedText,
                ),
            )
        }
        if (ttsEnabled && translatedText.isNotBlank() && !fullDuplexEnabled()) {
            scope.launch {
                val ready = prepareTts(utterance.targetLang)
                if (!ready) return@launch
                gateByTts = true
                capture.muted = true
                tts.speak(translatedText, utterance.targetLang)
                delay(GATE_WATCHDOG_MS)
                if (gateByTts && !tts.speaking.value) releaseGate()
            }
        }
    }

    private fun createUtterance(source: Language, target: Language): Utterance {
        val id = ++segmentCounter
        val scheduler = StreamingTranslationScheduler(
            translator = translator,
            from = source,
            to = target,
            scope = scope,
            onUpdate = { _, translatedText, _ ->
                val current = synchronized(utteranceLock) { currentUtterance }
                if (current?.id == id) {
                    current.translatedText = translatedText
                    updateLive(current, isFinal = false)
                }
            },
            onClauseTranslated = { translation -> maybeSpeakClause(translation, target) },
        )
        return Utterance(
            id = id,
            sourceLang = source,
            targetLang = target,
            startedAtMs = System.currentTimeMillis(),
            scheduler = scheduler,
        )
    }

    private fun maybeSpeakClause(translation: String, target: Language) {
        if (!ttsEnabled || translation.isBlank() || !fullDuplexEnabled()) return
        scope.launch {
            val ready = prepareTts(target)
            if (ready) tts.speak(translation, target)
        }
    }

    private suspend fun prepareTts(language: Language): Boolean {
        synchronized(ttsLock) { if (preparedTtsLanguage == language) return true }
        val ready = tts.prepare(language, config?.speechRate ?: 1.0f)
        synchronized(ttsLock) { if (ready) preparedTtsLanguage = language }
        if (!ready) _state.update { it.copy(ttsReady = false) }
        return ready
    }

    private fun onTtsSpeakingChanged(speaking: Boolean) {
        _state.update { it.copy(ttsSpeaking = speaking) }
        if (!speaking && gateByTts) {
            scope.launch {
                delay(GATE_RELEASE_DELAY_MS)
                if (!tts.speaking.value) releaseGate()
            }
        }
    }

    private fun releaseGate() {
        gateByTts = false
        capture.muted = false
    }

    private fun fullDuplexEnabled(): Boolean {
        val currentConfig = config ?: return false
        return when (currentConfig.duplexMode) {
            DuplexMode.FULL -> true
            DuplexMode.HALF -> false
            DuplexMode.AUTO -> routeMonitor.headphonesConnected.value
        }
    }

    private fun refreshFullDuplexState() {
        _state.update { it.copy(fullDuplex = fullDuplexEnabled()) }
    }

    private fun targetOf(source: Language): Language {
        val currentState = _state.value
        return if (currentState.mode == Mode.CONVERSATION) {
            if (source == currentState.langA) currentState.langB else currentState.langA
        } else {
            currentState.langB
        }
    }

    private suspend fun flushCurrentUtterance() {
        val utterance = synchronized(utteranceLock) {
            currentUtterance?.also { currentUtterance = null }
        } ?: return
        val flush = runCatching { asr?.finishUtterance() }.getOrNull()
        if (flush is AsrEvent.Final && flush.text.isNotBlank()) {
            finishUtterance(utterance, flush.text)
        } else {
            utterance.scheduler.dispose()
        }
    }

    private fun updateLive(utterance: Utterance, isFinal: Boolean) {
        val segment = LiveSegment(
            id = utterance.id,
            sourceLang = utterance.sourceLang,
            targetLang = utterance.targetLang,
            sourceText = utterance.sourceText,
            translatedText = utterance.translatedText,
            isFinal = isFinal,
            startedAtMs = utterance.startedAtMs,
        )
        _state.update { current ->
            val segments = current.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == utterance.id }
            if (index >= 0) segments[index] = segment else segments.add(segment)
            val trimmed = if (segments.size > MAX_LIVE_SEGMENTS) segments.takeLast(MAX_LIVE_SEGMENTS) else segments
            current.copy(segments = trimmed)
        }
    }

    private fun fail(reason: ControllerError) {
        _state.update { it.copy(status = ControllerStatus.Error(reason)) }
        stopInternal(keepStatus = true)
    }

    private fun stopInternal(keepStatus: Boolean) {
        sessionScope?.cancel()
        sessionScope = null
        capture.stop()
        releaseGate()
        paused = false
        switching = false
        synchronized(utteranceLock) {
            currentUtterance?.scheduler?.dispose()
            currentUtterance = null
        }
        val engine = asr
        asr = null
        if (engine != null) scope.launch(Dispatchers.IO) { runCatching { engine.close() } }
        translator.close()
        tts.stop()
        synchronized(ttsLock) { preparedTtsLanguage = null }
        routeMonitor.stop()
        speechDetector.reset()
        saveSession()
        val previous = _state.value
        _state.value = InterpretationState(
            mode = previous.mode,
            langA = previous.langA,
            langB = previous.langB,
            status = if (keepStatus) previous.status else null,
        )
    }

    private fun saveSession() {
        if (sessionSegments.isEmpty()) return
        val session = HistorySession(
            id = sessionId,
            startedAt = sessionStartedAt,
            endedAt = System.currentTimeMillis(),
            mode = sessionMode.name,
            langA = sessionLangA.tag,
            langB = sessionLangB.tag,
            segments = sessionSegments.toList(),
        )
        scope.launch { runCatching { historyStore.save(session) } }
    }

    private class Utterance(
        val id: Long,
        val sourceLang: Language,
        val targetLang: Language,
        val startedAtMs: Long,
        val scheduler: StreamingTranslationScheduler,
    ) {
        @Volatile
        var sourceText: String = ""

        @Volatile
        var translatedText: String = ""
    }

    private companion object {
        const val MAX_LIVE_SEGMENTS = 60
        const val GATE_RELEASE_DELAY_MS = 300L
        const val GATE_WATCHDOG_MS = 2000L
        val SESSION_ID_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
