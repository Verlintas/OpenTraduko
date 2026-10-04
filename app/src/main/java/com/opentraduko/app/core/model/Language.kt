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

package com.opentraduko.app.core.model

import java.util.Locale

/**
 * A language supported by the offline pipeline. The tag doubles as the ML Kit
 * translation language code; Vosk models follow the vosk-model-small-* naming.
 */
enum class Language(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val voskModelName: String,
    val voskModelSizeBytes: Long,
    val minClauseChars: Int,
) {
    CHINESE("zh", "中文", "Chinese", "vosk-model-small-cn-0.22", 44_040_192L, 6),
    ENGLISH("en", "English", "English", "vosk-model-small-en-us-0.15", 41_943_040L, 24),
    JAPANESE("ja", "日本語", "Japanese", "vosk-model-small-ja-0.22", 50_331_648L, 8),
    KOREAN("ko", "한국어", "Korean", "vosk-model-small-ko-0.22", 85_983_232L, 8),
    SPANISH("es", "Español", "Spanish", "vosk-model-small-es-0.42", 40_894_464L, 18),
    FRENCH("fr", "Français", "French", "vosk-model-small-fr-0.22", 43_000_000L, 18),
    GERMAN("de", "Deutsch", "German", "vosk-model-small-de-0.15", 47_185_920L, 18),
    RUSSIAN("ru", "Русский", "Russian", "vosk-model-small-ru-0.22", 47_185_920L, 18),
    ;

    val voskModelFile: String get() = "$voskModelName.zip"

    /**
     * Optional community mirror hosted on hf-mirror.com, which is reachable
     * and fast from mainland China. Only languages with a known mirror have a
     * non-null value.
     */
    val hfMirrorUrl: String?
        get() = when (this) {
            CHINESE -> "https://hf-mirror.com/guloooovoooo/vosk-model-small-cn/resolve/main/vosk-model-small-cn.zip"
            ENGLISH -> "https://hf-mirror.com/ambind/vosk-model-small-en-us-0.15/resolve/main/vosk-model-small-en-us-0.15_c_.zip"
            else -> null
        }

    val isCjk: Boolean get() = this == CHINESE || this == JAPANESE || this == KOREAN

    /** Separator used when composing several translated clauses into one text. */
    val clauseJoiner: String get() = if (isCjk) "" else " "

    val locale: Locale
        get() = when (this) {
            CHINESE -> Locale.SIMPLIFIED_CHINESE
            ENGLISH -> Locale.US
            JAPANESE -> Locale.JAPAN
            KOREAN -> Locale.KOREA
            SPANISH -> Locale.forLanguageTag("es-ES")
            FRENCH -> Locale.FRANCE
            GERMAN -> Locale.GERMANY
            RUSSIAN -> Locale.forLanguageTag("ru-RU")
        }

    companion object {
        fun fromTag(tag: String?): Language? = entries.firstOrNull { it.tag == tag }
    }
}
