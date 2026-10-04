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

import com.opentraduko.app.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `joins space separated chinese tokens`() {
        assertEquals("今天天气不错", TextNormalizer.normalize("今天 天气 不错", Language.CHINESE))
    }

    @Test
    fun `keeps spaces between latin words`() {
        assertEquals("hello world", TextNormalizer.normalize("hello  world", Language.ENGLISH))
    }

    @Test
    fun `removes spaces before punctuation`() {
        assertEquals("hello, world", TextNormalizer.normalize("hello , world", Language.ENGLISH))
    }

    @Test
    fun `joins japanese tokens`() {
        assertEquals("こんにちは世界", TextNormalizer.normalize("こんにちは 世界", Language.JAPANESE))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals("hello", TextNormalizer.normalize("  hello  ", Language.ENGLISH))
    }
}
