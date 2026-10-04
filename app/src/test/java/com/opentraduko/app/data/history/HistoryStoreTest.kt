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

package com.opentraduko.app.data.history

import com.opentraduko.app.core.model.HistorySegment
import com.opentraduko.app.core.model.HistorySession
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun session() = HistorySession(
        id = "20260101-120000",
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_060_000L,
        mode = "LISTENING",
        langA = "en",
        langB = "zh",
        segments = listOf(
            HistorySegment(
                timestampMs = 1_700_000_010_000L,
                sourceLang = "en",
                targetLang = "zh",
                sourceText = "hello world",
                translatedText = "你好世界",
            ),
        ),
    )

    @Test
    fun `persists sessions across store instances`() = runTest {
        val directory = temporaryFolder.newFolder("history")
        val store = HistoryStore(directory)
        store.save(session())
        assertEquals(1, store.sessions.value.size)

        val reloaded = HistoryStore(directory)
        reloaded.load()
        assertEquals(1, reloaded.sessions.value.size)
        assertEquals("20260101-120000", reloaded.sessions.value.first().id)
    }

    @Test
    fun `export contains source and translation`() = runTest {
        val store = HistoryStore(temporaryFolder.newFolder("history-export"))
        val text = store.exportText(session())
        assertTrue(text.contains("hello world"))
        assertTrue(text.contains("你好世界"))
    }

    @Test
    fun `delete removes session and file`() = runTest {
        val directory = temporaryFolder.newFolder("history-delete")
        val store = HistoryStore(directory)
        store.save(session())
        store.delete("20260101-120000")
        assertTrue(store.sessions.value.isEmpty())
        assertFalse(File(directory, "20260101-120000.json").exists())
    }
}
