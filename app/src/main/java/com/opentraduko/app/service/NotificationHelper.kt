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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.opentraduko.app.MainActivity
import com.opentraduko.app.R
import com.opentraduko.app.core.pipeline.InterpretationState

object NotificationHelper {

    const val CHANNEL_ID = "interpretation"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: InterpretationState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText(context, state))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (state.paused) {
            builder.addAction(
                0,
                context.getString(R.string.notification_resume),
                serviceAction(context, InterpretationService.ACTION_RESUME, 1),
            )
        } else {
            builder.addAction(
                0,
                context.getString(R.string.notification_pause),
                serviceAction(context, InterpretationService.ACTION_PAUSE, 2),
            )
        }
        builder.addAction(
            0,
            context.getString(R.string.notification_stop),
            serviceAction(context, InterpretationService.ACTION_STOP, 3),
        )
        return builder.build()
    }

    private fun statusText(context: Context, state: InterpretationState): String = when {
        state.paused -> context.getString(R.string.notification_status_paused)
        state.ttsSpeaking -> context.getString(R.string.notification_status_speaking)
        else -> context.getString(R.string.notification_status_listening)
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, InterpretationService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
