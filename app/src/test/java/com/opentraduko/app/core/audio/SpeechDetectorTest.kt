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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDetectorTest {

    @Test
    fun `requires consecutive loud chunks to start speaking`() {
        val detector = SpeechDetector(onThreshold = 0.3f, offThreshold = 0.1f, onHangChunks = 3, offHangChunks = 5)
        assertFalse(detector.accept(0.5f))
        assertFalse(detector.accept(0.5f))
        assertTrue(detector.accept(0.5f))
    }

    @Test
    fun `brief noise does not trigger speech`() {
        val detector = SpeechDetector(onThreshold = 0.3f, offThreshold = 0.1f, onHangChunks = 3, offHangChunks = 5)
        assertFalse(detector.accept(0.5f))
        assertFalse(detector.accept(0.0f))
        assertFalse(detector.accept(0.5f))
        assertFalse(detector.accept(0.0f))
    }

    @Test
    fun `stays speaking through short pauses and stops after long silence`() {
        val detector = SpeechDetector(onThreshold = 0.3f, offThreshold = 0.1f, onHangChunks = 2, offHangChunks = 4)
        detector.accept(0.5f)
        assertTrue(detector.accept(0.5f))
        assertTrue(detector.accept(0.05f))
        assertTrue(detector.accept(0.05f))
        assertTrue(detector.accept(0.05f))
        assertFalse(detector.accept(0.05f))
    }
}
