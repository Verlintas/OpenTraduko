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

import com.opentraduko.app.core.model.Language
import com.opentraduko.app.data.settings.SettingsRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * BYOK translation against any OpenAI-compatible chat completions endpoint:
 * DeepSeek, Qwen/DashScope compatible mode, SiliconFlow, Moonshot, a local
 * Ollama server, OpenAI itself, and so on. Settings are read on every call so
 * changes apply on the next utterance without restarting the engine.
 */
class OpenAiCompatibleTranslationEngine(
    private val settings: SettingsRepository,
) : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun translate(text: String, from: Language, to: Language): String {
        if (from == to || text.isBlank()) return text
        val preferences = settings.current()
        val apiKey = preferences.openAiApiKey.trim()
        if (apiKey.isBlank()) throw IOException("missing API key")
        val baseUrl = preferences.openAiBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("missing API base URL")
        val model = preferences.openAiModel.trim().ifBlank { DEFAULT_MODEL }

        val payload = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage("system", systemPrompt(from, to)),
                ChatMessage("user", text),
            ),
        )
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(ChatRequest.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).awaitResponse().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        json.decodeFromString(ChatResponse.serializer(), body).error?.message
                    }.getOrNull()
                    throw IOException("HTTP ${response.code}${detail?.let { ": $it" }.orEmpty()}")
                }
                val content = json.decodeFromString(ChatResponse.serializer(), body)
                    .choices.firstOrNull()?.message?.content?.trim()
                content?.takeIf { it.isNotEmpty() } ?: throw IOException("empty translation response")
            }
        }
    }

    override suspend fun ensureModels(from: Language, to: Language): Result<Unit> {
        val preferences = settings.current()
        return when {
            preferences.openAiApiKey.isBlank() ->
                Result.failure(IOException("missing API key"))
            preferences.openAiBaseUrl.isBlank() ->
                Result.failure(IOException("missing API base URL"))
            else -> Result.success(Unit)
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun systemPrompt(from: Language, to: Language): String =
        "You are a simultaneous interpretation engine. Translate the user's text " +
            "from ${from.englishName} (${from.tag}) to ${to.englishName} (${to.tag}). " +
            "Output only the translation, without explanations, notes or quotes. " +
            "Preserve numbers, names and formatting. If the text is already in the target " +
            "language, return it unchanged."

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.0,
        val stream: Boolean = false,
    )

    @Serializable
    private data class ChatChoice(val message: ChatMessage? = null)

    @Serializable
    private data class ChatResponse(
        val choices: List<ChatChoice> = emptyList(),
        val error: ApiError? = null,
    )

    @Serializable
    private data class ApiError(
        val message: String? = null,
        @SerialName("type") val type: String? = null,
    )

    private companion object {
        const val DEFAULT_MODEL = "deepseek-chat"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
