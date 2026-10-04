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

package com.opentraduko.app.core.pipeline

import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.text.ClauseSplitter
import com.opentraduko.app.engine.mt.TranslationEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Turns a stream of ASR partials/finals into ordered, incremental translation.
 *
 * Only clauses stable across two consecutive partials are translated, which
 * avoids translating text that the recognizer may still revise. A single worker
 * coroutine keeps translations ordered and serializes calls into the engine.
 *
 * One instance handles exactly one utterance; create a new one per utterance.
 */
class StreamingTranslationScheduler(
    private val translator: TranslationEngine,
    private val from: Language,
    private val to: Language,
    scope: CoroutineScope,
    private val onUpdate: (sourceText: String, translatedText: String, isFinal: Boolean) -> Unit,
    private val onClauseTranslated: (translation: String) -> Unit,
) {

    private sealed interface Job {
        data class Clause(val text: String) : Job

        data class Finalize(val deferred: CompletableDeferred<String>) : Job

        data object Close : Job
    }

    private val lock = Any()
    private val jobs = Channel<Job>(Channel.UNLIMITED)
    private val committedSource = StringBuilder()
    private val translated = StringBuilder()
    private var lastPartial = ""
    private var closed = false

    private val worker = scope.launch {
        for (job in jobs) {
            when (job) {
                is Job.Clause -> translateClause(job.text)
                is Job.Finalize -> {
                    val snapshot = synchronized(lock) { translated.toString() }
                    runCatching { onUpdate(committedSource.toString(), snapshot, true) }
                    job.deferred.complete(snapshot)
                    jobs.close()
                }
                Job.Close -> jobs.close()
            }
        }
    }

    fun onPartial(fullText: String) {
        synchronized(lock) {
            if (closed) return
            val committedBefore = committedSource.toString()
            val tail = if (fullText.startsWith(committedBefore)) {
                fullText.substring(committedBefore.length)
            } else {
                fullText
            }
            val stable = commonPrefix(tail, lastPartial)
            val (clauses, _) = ClauseSplitter.splitComplete(stable, from.minClauseChars)
            clauses.forEach(::enqueueLocked)
            val committedAfter = committedSource.toString()
            lastPartial = if (fullText.startsWith(committedAfter)) {
                fullText.substring(committedAfter.length)
            } else {
                fullText
            }
        }
    }

    /**
     * Finalizes the utterance: translates whatever is left and suspends until
     * all translations are done, returning the accumulated translation.
     */
    suspend fun onFinal(fullText: String): String {
        val deferred = CompletableDeferred<String>()
        synchronized(lock) {
            if (closed) return translated.toString()
            val committed = committedSource.toString()
            val tail = if (fullText.startsWith(committed)) fullText.substring(committed.length) else fullText
            val (clauses, remainder) = ClauseSplitter.splitComplete(tail.trim(), from.minClauseChars)
            clauses.forEach(::enqueueLocked)
            if (remainder.isNotBlank()) enqueueLocked(remainder.trim())
            jobs.trySend(Job.Finalize(deferred))
        }
        return deferred.await()
    }

    /** Aborts the utterance without a final translation (e.g. session stop). */
    fun dispose() {
        synchronized(lock) {
            if (closed) return
            closed = true
            jobs.trySend(Job.Close)
        }
        worker.cancel()
    }

    private fun enqueueLocked(clause: String) {
        if (clause.isBlank()) return
        if (committedSource.isNotEmpty()) committedSource.append(from.clauseJoiner)
        committedSource.append(clause)
        jobs.trySend(Job.Clause(clause))
    }

    private suspend fun translateClause(clause: String) {
        val result = runCatching { translator.translate(clause, from, to) }.getOrDefault("")
        synchronized(lock) {
            if (result.isNotBlank()) {
                if (translated.isNotEmpty()) translated.append(to.clauseJoiner)
                translated.append(result)
            }
            runCatching { onUpdate(committedSource.toString(), translated.toString(), false) }
        }
        if (result.isNotBlank()) runCatching { onClauseTranslated(result) }
    }

    private fun commonPrefix(a: String, b: String): String {
        val limit = minOf(a.length, b.length)
        var index = 0
        while (index < limit && a[index] == b[index]) index++
        return a.substring(0, index)
    }
}
