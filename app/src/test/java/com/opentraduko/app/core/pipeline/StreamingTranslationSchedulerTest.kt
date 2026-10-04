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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTranslationSchedulerTest {

    private class FakeTranslator : TranslationEngine {
        val translated = mutableListOf<String>()

        override suspend fun translate(text: String, from: Language, to: Language): String {
            translated.add(text)
            return "[$text]"
        }

        override suspend fun ensureModels(from: Language, to: Language): Result<Unit> =
            Result.success(Unit)

        override fun close() = Unit
    }

    private class Recorder {
        val updates = mutableListOf<Triple<String, String, Boolean>>()
        val clauses = mutableListOf<String>()
    }

    private fun CoroutineScope.scheduler(
        translator: FakeTranslator,
        recorder: Recorder,
        from: Language = Language.CHINESE,
        to: Language = Language.ENGLISH,
    ) = StreamingTranslationScheduler(
        translator = translator,
        from = from,
        to = to,
        scope = this,
        onUpdate = { source, translated, isFinal ->
            recorder.updates.add(Triple(source, translated, isFinal))
        },
        onClauseTranslated = { recorder.clauses.add(it) },
    )

    @Test
    fun `translates only stable clauses while speech continues`() = runTest {
        val translator = FakeTranslator()
        val recorder = Recorder()
        val scheduler = backgroundScope.scheduler(translator, recorder)

        scheduler.onPartial("今天天气")
        scheduler.onPartial("今天天气不错。")
        runCurrent()
        assertTrue(translator.translated.isEmpty())

        scheduler.onPartial("今天天气不错。我们")
        runCurrent()
        assertEquals(listOf("今天天气不错。"), translator.translated)
        assertEquals(listOf("[今天天气不错。]"), recorder.clauses)

        scheduler.onPartial("今天天气不错。我们出去吧。")
        runCurrent()
        assertEquals(listOf("今天天气不错。"), translator.translated)
    }

    @Test
    fun `final translation includes every clause in order`() = runTest {
        val translator = FakeTranslator()
        val recorder = Recorder()
        val scheduler = backgroundScope.scheduler(translator, recorder)

        scheduler.onPartial("今天天气不错。我们出去吧。")
        runCurrent()
        val result = scheduler.onFinal("今天天气不错。我们出去吧。")

        assertEquals("[今天天气不错。] [我们出去吧。]", result)
        assertEquals(listOf("今天天气不错。", "我们出去吧。"), translator.translated)
        assertTrue(recorder.updates.any { it.third && it.second == result })
    }

    @Test
    fun `final without partials is translated once`() = runTest {
        val translator = FakeTranslator()
        val recorder = Recorder()
        val scheduler = backgroundScope.scheduler(translator, recorder)

        val result = scheduler.onFinal("你好世界。")

        assertEquals("[你好世界。]", result)
        assertEquals(listOf("你好世界。"), translator.translated)
    }

    @Test
    fun `revisions in partials do not produce stale translations`() = runTest {
        val translator = FakeTranslator()
        val recorder = Recorder()
        val scheduler = backgroundScope.scheduler(translator, recorder)

        scheduler.onPartial("I have a dre")
        scheduler.onPartial("I have a dream. Today")
        runCurrent()
        assertTrue(translator.translated.isEmpty())

        scheduler.onPartial("I have a dream. Today I fly.")
        runCurrent()
        assertEquals(listOf("I have a dream."), translator.translated)
    }
}
