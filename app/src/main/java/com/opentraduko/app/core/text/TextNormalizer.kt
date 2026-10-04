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

/**
 * Vosk emits space separated tokens and no punctuation, e.g. Chinese comes out
 * as "今天 天气 不错". This class restores natural spacing before the text is
 * displayed or translated.
 */
object TextNormalizer {

    private val CJK_CHARS = "\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}"
    private val SPACE_BETWEEN_CJK = Regex("(?<=[$CJK_CHARS])\\s+(?=[$CJK_CHARS])")
    private val SPACE_BEFORE_PUNCT = Regex("\\s+([,.!?;:，。！？；：、])")
    private val MULTI_SPACE = Regex(" {2,}")

    fun normalize(text: String, language: Language): String {
        var result = text.trim()
        if (language == Language.CHINESE || language == Language.JAPANESE) {
            result = SPACE_BETWEEN_CJK.replace(result, "")
        }
        result = SPACE_BEFORE_PUNCT.replace(result, "$1")
        result = MULTI_SPACE.replace(result, " ")
        return result.trim()
    }
}
