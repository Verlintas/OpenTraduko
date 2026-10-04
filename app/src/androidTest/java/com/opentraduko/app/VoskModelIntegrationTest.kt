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

package com.opentraduko.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.opentraduko.app.core.model.AsrEvent
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.pipeline.LiveTranslationScheduler
import com.opentraduko.app.core.text.TextNormalizer
import com.opentraduko.app.engine.asr.VoskAsrEngine
import com.opentraduko.app.engine.mt.TranslationEngine
import java.io.File
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decodes synthetic speech (assets/cn.pcm, assets/en.pcm, 16 kHz mono) through
 * the real Vosk pipeline. Skipped automatically when the models were not
 * pushed to the device, so the test is safe to run anywhere.
 */
@RunWith(AndroidJUnit4::class)
class VoskModelIntegrationTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun chineseModelTranscribesSyntheticSpeech() {
        val modelDir = File(targetContext.filesDir, "models/vosk-model-small-cn-0.22")
        assumeTrue(File(modelDir, "am/final.mdl").isFile)
        val text = transcribe(modelDir.absolutePath, "cn.pcm")
        assertTrue("unexpected result: $text", text.contains("天气") || text.contains("散步") || text.contains("出去"))
    }

    @Test
    fun englishModelTranscribesSyntheticSpeech() {
        val modelDir = File(targetContext.filesDir, "models/vosk-model-small-en-us-0.15")
        assumeTrue(File(modelDir, "am/final.mdl").isFile)
        val text = transcribe(modelDir.absolutePath, "en.pcm")
        assertTrue("unexpected result: $text", text.contains("hello") || text.contains("test"))
    }

    @Test
    fun recognizedSpeechDrivesLiveAndFinalTranslations() {
        val modelDir = File(targetContext.filesDir, "models/vosk-model-small-en-us-0.15")
        assumeTrue(File(modelDir, "am/final.mdl").isFile)
        val translator = object : TranslationEngine {
            override suspend fun translate(text: String, from: Language, to: Language): String =
                "[zh]$text"

            override suspend fun ensureModels(from: Language, to: Language): Result<Unit> =
                Result.success(Unit)

            override fun close() = Unit
        }
        val updates = Collections.synchronizedList(
            mutableListOf<Triple<String, String, Boolean>>(),
        )
        runBlocking {
            val scheduler = LiveTranslationScheduler(
                translator = translator,
                from = Language.ENGLISH,
                to = Language.CHINESE,
                scope = this,
                onUpdate = { source, translated, isFinal ->
                    updates.add(Triple(source, translated, isFinal))
                },
                intervalMs = 50,
            )
            val engine = VoskAsrEngine(modelDir.absolutePath)
            try {
                val samples = readPcmAsset("en.pcm")
                val chunkSize = 800
                var offset = 0
                while (offset < samples.size) {
                    val size = minOf(chunkSize, samples.size - offset)
                    delay(30)
                    when (val event = engine.feed(samples.copyOfRange(offset, offset + size))) {
                        is AsrEvent.Partial -> {
                            val text = TextNormalizer.normalize(event.text, Language.ENGLISH)
                            if (text.isNotBlank()) scheduler.onPartial(text)
                        }
                        is AsrEvent.Final -> {
                            val text = TextNormalizer.normalize(event.text, Language.ENGLISH)
                            if (text.isNotBlank()) scheduler.onFinal(text)
                        }
                        is AsrEvent.Error -> throw AssertionError(event.message)
                    }
                    offset += size
                }
                val final = engine.finishUtterance()
                if (final is AsrEvent.Final && final.text.isNotBlank()) {
                    scheduler.onFinal(TextNormalizer.normalize(final.text, Language.ENGLISH))
                }
            } finally {
                engine.close()
            }
        }
        synchronized(updates) {
            assertTrue("no live translation", updates.any { !it.third && it.second.isNotBlank() })
            assertTrue("no final translation", updates.any { it.third && it.second.contains("zh") })
        }
    }

    private fun readPcmAsset(asset: String): ShortArray {
        val bytes = testContext.assets.open(asset).use { it.readBytes() }
        val samples = ShortArray(bytes.size / 2)
        for (index in samples.indices) {
            val low = bytes[index * 2].toInt() and 0xFF
            val high = bytes[index * 2 + 1].toInt()
            samples[index] = ((high shl 8) or low).toShort()
        }
        return samples
    }

    private fun transcribe(modelPath: String, asset: String): String {
        val engine = VoskAsrEngine(modelPath)
        try {
            val samples = readPcmAsset(asset)
            var lastText = ""
            val chunkSize = 800
            var offset = 0
            while (offset < samples.size) {
                val size = minOf(chunkSize, samples.size - offset)
                val event = engine.feed(samples.copyOfRange(offset, offset + size))
                when (event) {
                    is AsrEvent.Partial -> lastText = event.text
                    is AsrEvent.Final -> lastText = event.text
                    is AsrEvent.Error -> throw AssertionError(event.message)
                }
                offset += size
            }
            val final = engine.finishUtterance()
            if (final is AsrEvent.Final && final.text.isNotBlank()) lastText = final.text
            return lastText
        } finally {
            engine.close()
        }
    }
}
