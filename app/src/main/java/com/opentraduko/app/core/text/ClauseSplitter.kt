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

/**
 * Splits streaming ASR text into complete clauses that are safe to translate
 * while speech continues. The trailing fragment is returned as the remainder.
 */
object ClauseSplitter {

    private val HARD_BOUNDARIES = charArrayOf(
        '。', '！', '？', '；', '…', '\n',
        '!', '?', ';',
    )

    private val SOFT_BOUNDARIES = charArrayOf('，', ',', '、')

    fun splitComplete(
        text: String,
        minClauseChars: Int,
        maxClauseChars: Int = 60,
    ): Pair<List<String>, String> {
        val clauses = mutableListOf<String>()
        val buffer = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            buffer.append(c)
            val boundary = when {
                c in HARD_BOUNDARIES -> true
                c == '.' && isSentencePeriod(text, i) -> true
                c in SOFT_BOUNDARIES && buffer.length >= minClauseChars -> true
                buffer.length >= maxClauseChars -> true
                else -> false
            }
            if (boundary) {
                val clause = buffer.toString().trim()
                if (clause.isNotEmpty()) clauses.add(clause)
                buffer.clear()
            }
            i++
        }
        return clauses to buffer.toString()
    }

    private fun isSentencePeriod(text: String, index: Int): Boolean {
        if (index == text.length - 1) return true
        val next = text[index + 1]
        if (next.isDigit() && index > 0 && text[index - 1].isDigit()) return false
        return next.isWhitespace()
    }
}
