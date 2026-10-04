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

package com.opentraduko.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClauseSplitterTest {

    @Test
    fun `splits chinese sentence at hard boundary`() {
        val (clauses, remainder) = ClauseSplitter.splitComplete("今天天气不错。我们出去", 6)
        assertEquals(listOf("今天天气不错。"), clauses)
        assertEquals("我们出去", remainder)
    }

    @Test
    fun `keeps incomplete tail untranslated`() {
        val (clauses, remainder) = ClauseSplitter.splitComplete("今天天气", 6)
        assertTrue(clauses.isEmpty())
        assertEquals("今天天气", remainder)
    }

    @Test
    fun `splits english sentence period before whitespace`() {
        val (clauses, remainder) = ClauseSplitter.splitComplete("Hello world. How are", 24)
        assertEquals(listOf("Hello world."), clauses)
        assertEquals(" How are", remainder)
    }

    @Test
    fun `does not split decimals`() {
        val (clauses, remainder) = ClauseSplitter.splitComplete("the value is 3.14 exactly", 24)
        assertTrue(clauses.isEmpty())
        assertEquals("the value is 3.14 exactly", remainder)
    }

    @Test
    fun `soft comma splits only after enough characters`() {
        val (shortClauses, _) = ClauseSplitter.splitComplete("first, second", 6)
        assertEquals(listOf("first,"), shortClauses)

        val (longClauses, _) = ClauseSplitter.splitComplete("first, second", 10)
        assertTrue(longClauses.isEmpty())
    }

    @Test
    fun `forces a split for long unpunctuated text`() {
        val longText = "啊".repeat(80)
        val (clauses, _) = ClauseSplitter.splitComplete(longText, 6, maxClauseChars = 60)
        assertEquals(1, clauses.size)
        assertEquals(60, clauses.first().length)
    }
}
