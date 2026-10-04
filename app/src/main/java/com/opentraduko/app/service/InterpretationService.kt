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

package com.opentraduko.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.opentraduko.app.OpenTradukoApp
import com.opentraduko.app.core.model.DuplexMode
import com.opentraduko.app.core.model.Language
import com.opentraduko.app.core.model.Mode
import com.opentraduko.app.core.model.SessionConfig
import com.opentraduko.app.core.pipeline.ControllerStatus
import com.opentraduko.app.core.pipeline.InterpretationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service hosting an interpretation session so it keeps running
 * with the screen off. All state lives in [com.opentraduko.app.core.pipeline.InterpretationController].
 */
class InterpretationService : Service() {

    private val container get() = (application as OpenTradukoApp).container

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> container.controller.pause()
            ACTION_RESUME -> container.controller.resume()
            ACTION_STOP -> {
                container.controller.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> if (!container.controller.state.value.running) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        val mode = runCatching { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }
            .getOrDefault(Mode.LISTENING)
        val langA = Language.fromTag(intent.getStringExtra(EXTRA_LANG_A)) ?: Language.CHINESE
        val langB = Language.fromTag(intent.getStringExtra(EXTRA_LANG_B)) ?: Language.ENGLISH
        val ttsEnabled = intent.getBooleanExtra(EXTRA_TTS_ENABLED, true)
        val duplexMode = runCatching { DuplexMode.valueOf(intent.getStringExtra(EXTRA_DUPLEX_MODE) ?: "") }
            .getOrDefault(DuplexMode.AUTO)
        val speechRate = intent.getFloatExtra(EXTRA_SPEECH_RATE, 1.0f)
        val config = SessionConfig(mode, langA, langB, ttsEnabled, duplexMode, speechRate)

        val startingState = InterpretationState(
            running = true,
            paused = false,
            mode = mode,
            langA = langA,
            langB = langB,
            ttsEnabled = ttsEnabled,
            status = ControllerStatus.LoadingModel,
        )
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.build(this, startingState),
            foregroundServiceType(),
        )
        container.controller.start(config)
        observeState()
    }

    private fun observeState() {
        if (observing) return
        observing = true
        serviceScope.launch {
            container.controller.state.collect { state ->
                if (!state.running) {
                    ServiceCompat.stopForeground(this@InterpretationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                runCatching {
                    NotificationManagerCompat.from(this@InterpretationService)
                        .notify(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this@InterpretationService, state))
                }
            }
        }
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

    companion object {
        const val ACTION_START = "com.opentraduko.app.action.START"
        const val ACTION_PAUSE = "com.opentraduko.app.action.PAUSE"
        const val ACTION_RESUME = "com.opentraduko.app.action.RESUME"
        const val ACTION_STOP = "com.opentraduko.app.action.STOP"

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_LANG_A = "lang_a"
        private const val EXTRA_LANG_B = "lang_b"
        private const val EXTRA_TTS_ENABLED = "tts_enabled"
        private const val EXTRA_DUPLEX_MODE = "duplex_mode"
        private const val EXTRA_SPEECH_RATE = "speech_rate"

        fun start(context: Context, config: SessionConfig) {
            val intent = Intent(context, InterpretationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, config.mode.name)
                .putExtra(EXTRA_LANG_A, config.langA.tag)
                .putExtra(EXTRA_LANG_B, config.langB.tag)
                .putExtra(EXTRA_TTS_ENABLED, config.ttsEnabled)
                .putExtra(EXTRA_DUPLEX_MODE, config.duplexMode.name)
                .putExtra(EXTRA_SPEECH_RATE, config.speechRate)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            context.startService(Intent(context, InterpretationService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, InterpretationService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, InterpretationService::class.java).setAction(ACTION_STOP))
        }
    }
}
