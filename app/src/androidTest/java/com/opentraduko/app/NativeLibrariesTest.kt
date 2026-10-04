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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the native library packaging that the offline pipeline depends on:
 * Vosk ships libvosk.so and uses JNA to bind to it.
 */
@RunWith(AndroidJUnit4::class)
class NativeLibrariesTest {

    @Test
    fun voskNativeLibraryLoads() {
        System.loadLibrary("vosk")
    }

    @Test
    fun jnaIsAvailable() {
        val pointerSize = com.sun.jna.Native.POINTER_SIZE
        assertTrue(pointerSize > 0)
    }
}
