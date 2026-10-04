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

package com.opentraduko.app.engine.mt

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.opentraduko.app.core.model.Language
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device translation backed by ML Kit translation models. Models are
 * downloaded once per language pair and then work offline.
 */
class MlKitTranslationEngine : TranslationEngine {

    private val clients = mutableMapOf<String, Translator>()
    private val lock = Any()

    private fun client(from: Language, to: Language): Translator = synchronized(lock) {
        clients.getOrPut("${from.tag}:${to.tag}") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(from.tag)
                    .setTargetLanguage(to.tag)
                    .build(),
            )
        }
    }

    override suspend fun translate(text: String, from: Language, to: Language): String {
        if (from == to || text.isBlank()) return text
        val translator = client(from, to)
        return try {
            translator.translate(text).await()
        } catch (t: Throwable) {
            translator.downloadModelIfNeeded().await()
            translator.translate(text).await()
        }
    }

    override suspend fun ensureModels(from: Language, to: Language): Result<Unit> {
        if (from == to) return Result.success(Unit)
        return runCatching { client(from, to).downloadModelIfNeeded().await() }
    }

    override fun close() {
        val snapshot = synchronized(lock) {
            val values = clients.values.toList()
            clients.clear()
            values
        }
        snapshot.forEach { runCatching { it.close() } }
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
