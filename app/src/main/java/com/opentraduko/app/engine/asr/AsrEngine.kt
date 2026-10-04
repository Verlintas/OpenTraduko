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

package com.opentraduko.app.engine.asr

import com.opentraduko.app.core.model.AsrEvent
import java.io.Closeable

/**
 * Streaming speech recognizer fed with 16 kHz mono PCM. Implementations are
 * not required to be thread-safe; the pipeline feeds from one coroutine only.
 */
interface AsrEngine : Closeable {
    fun feed(samples: ShortArray): AsrEvent

    /** Flushes the pending utterance and returns its final transcription. */
    fun finishUtterance(): AsrEvent

    /** Number of samples per second expected by [feed]. */
    val sampleRate: Float
}
