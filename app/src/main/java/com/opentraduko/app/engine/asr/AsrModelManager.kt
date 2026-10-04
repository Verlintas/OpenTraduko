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

import android.util.Log
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.data.settings.SettingsRepository
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadState {
    data object Idle : DownloadState

    data class Downloading(val bytesRead: Long, val totalBytes: Long) : DownloadState

    data object Extracting : DownloadState

    data class Failed(val message: String) : DownloadState
}

/**
 * Downloads and unpacks Vosk recognition models into app-private storage.
 * Models are fetched from [SettingsRepository] mirror base URL so users behind
 * slow/unreachable hosts can point at a mirror.
 */
class AsrModelManager(
    filesDir: File,
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
) {

    private val modelsDir = File(filesDir, "models")

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    fun modelDir(language: Language): File = File(modelsDir, language.voskModelName)

    fun isInstalled(language: Language): Boolean =
        File(modelDir(language), "am/final.mdl").isFile

    fun installedLanguages(): List<Language> = Language.entries.filter { isInstalled(it) }

    fun downloadState(language: Language): DownloadState =
        _downloads.value[language.voskModelName] ?: DownloadState.Idle

    suspend fun download(language: Language): Boolean = withContext(Dispatchers.IO) {
        val modelName = language.voskModelName
        try {
            modelsDir.mkdirs()
            val base = settings.current().modelMirrorBaseUrl.trimEnd('/')
            val url = "$base/${language.voskModelFile}"
            val archive = File(modelsDir, "$modelName.zip.download")
            archive.delete()

            setState(modelName, DownloadState.Downloading(0, language.voskModelSizeBytes))
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: language.voskModelSizeBytes
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            setState(modelName, DownloadState.Downloading(readTotal, total))
                        }
                    }
                }
            }

            setState(modelName, DownloadState.Extracting)
            modelDir(language).deleteRecursively()
            unzip(archive, modelsDir)
            archive.delete()
            if (!isInstalled(language)) throw IOException("model archive is incomplete")
            setState(modelName, DownloadState.Idle)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "failed to download $modelName", t)
            setState(modelName, DownloadState.Failed(t.message ?: "download failed"))
            false
        }
    }

    fun delete(language: Language) {
        modelDir(language).deleteRecursively()
    }

    private fun setState(modelName: String, state: DownloadState) {
        _downloads.update { it + (modelName to state) }
    }

    private fun unzip(archive: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalFile
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name.contains("__MACOSX") || name.endsWith("/")) {
                    zip.closeEntry()
                    continue
                }
                val outputFile = File(targetDir, name)
                if (!outputFile.canonicalPath.startsWith(canonicalTarget.path + File.separator)) {
                    throw IOException("unsafe zip entry: $name")
                }
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private companion object {
        const val TAG = "AsrModelManager"
    }
}
