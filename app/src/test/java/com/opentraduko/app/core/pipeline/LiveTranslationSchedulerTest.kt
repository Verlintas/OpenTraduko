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
import java.io.IOException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTranslationSchedulerTest {

    private class FakeTranslator : TranslationEngine {
        val calls = mutableListOf<String>()
        var failNext = false

        override suspend fun translate(text: String, from: Language, to: Language): String {
            calls.add(text)
            if (failNext) {
                failNext = false
                throw IOException("boom")
            }
            return "[$text]"
        }

        override suspend fun ensureModels(from: Language, to: Language): Result<Unit> =
            Result.success(Unit)

        override fun close() = Unit
    }

    @Test
    fun `translates growing stable prefix and replaces it live`() = runTest {
        val translator = FakeTranslator()
        val updates = mutableListOf<Triple<String, String, Boolean>>()
        val scheduler = LiveTranslationScheduler(
            translator = translator,
            from = Language.CHINESE,
            to = Language.ENGLISH,
            scope = this,
            onUpdate = { source, translated, isFinal -> updates.add(Triple(source, translated, isFinal)) },
            intervalMs = 500,
        )

        scheduler.onPartial("你好")
        scheduler.onPartial("你好世界")
        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf("你好"), translator.calls)

        scheduler.onPartial("你好世界再见")
        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf("你好", "你好世界"), translator.calls)

        val result = scheduler.onFinal("你好世界再见")
        assertEquals("[你好世界再见]", result)
        assertTrue(updates.last().third)
        assertEquals("你好世界再见", updates.last().first)
    }

    @Test
    fun `does not translate the unstable tail`() = runTest {
        val translator = FakeTranslator()
        val scheduler = LiveTranslationScheduler(
            translator = translator,
            from = Language.ENGLISH,
            to = Language.CHINESE,
            scope = this,
            onUpdate = { _, _, _ -> },
            intervalMs = 200,
        )

        scheduler.onPartial("abc")
        scheduler.onPartial("abcdef")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("abc"), translator.calls)

        scheduler.dispose()
    }

    @Test
    fun `reports errors and keeps the last live translation on final`() = runTest {
        val translator = FakeTranslator()
        val errors = mutableListOf<Throwable>()
        val scheduler = LiveTranslationScheduler(
            translator = translator,
            from = Language.ENGLISH,
            to = Language.CHINESE,
            scope = this,
            onUpdate = { _, _, _ -> },
            onError = { errors.add(it) },
            intervalMs = 200,
        )

        scheduler.onPartial("hello")
        scheduler.onPartial("hello world")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("hello"), translator.calls)

        translator.failNext = true
        val result = scheduler.onFinal("hello world!")
        assertEquals("[hello]", result)
        assertTrue(errors.isNotEmpty())
    }
}
