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

import com.opentraduko.app.core.model.HistorySession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * JSON-file backed session history. One file per session keeps reads and
 * deletes cheap without pulling in a database.
 */
class HistoryStore(private val directory: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _sessions = MutableStateFlow<List<HistorySession>>(emptyList())
    val sessions: StateFlow<List<HistorySession>> = _sessions.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val sessions = directory.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<HistorySession>(file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.startedAt }
            ?: emptyList()
        _sessions.value = sessions
    }

    suspend fun save(session: HistorySession) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        File(directory, "${session.id}.json").writeText(json.encodeToString(session))
        _sessions.update { current ->
            (current.filterNot { it.id == session.id } + session).sortedByDescending { it.startedAt }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(directory, "$id.json").delete()
        _sessions.update { current -> current.filterNot { it.id == id } }
    }

    fun session(id: String): HistorySession? = _sessions.value.firstOrNull { it.id == id }

    fun exportText(session: HistorySession): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val builder = StringBuilder()
        builder.appendLine("OpenTraduko ${session.mode} ${session.langA} -> ${session.langB}")
        builder.appendLine(dateFormat.format(Date(session.startedAt)))
        builder.appendLine()
        for (segment in session.segments) {
            builder.append("[${timeFormat.format(Date(segment.timestampMs))}] ${segment.sourceText}")
            builder.appendLine()
            builder.appendLine(segment.translatedText)
            builder.appendLine()
        }
        return builder.toString()
    }
}
