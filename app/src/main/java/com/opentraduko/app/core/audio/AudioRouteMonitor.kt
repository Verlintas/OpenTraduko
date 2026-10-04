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

package com.opentraduko.app.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether headphones are connected. With headphones the pipeline can run
 * full-duplex (TTS and recognition at the same time) because the microphone
 * does not pick up the speaker output.
 */
class AudioRouteMonitor(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _headphonesConnected = MutableStateFlow(computeHeadphonesConnected())
    val headphonesConnected: StateFlow<Boolean> = _headphonesConnected.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start() {
        audioManager?.registerAudioDeviceCallback(callback, null)
        refresh()
    }

    fun stop() {
        audioManager?.unregisterAudioDeviceCallback(callback)
    }

    private fun refresh() {
        _headphonesConnected.value = computeHeadphonesConnected()
    }

    private fun computeHeadphonesConnected(): Boolean {
        val manager = audioManager ?: return false
        val devices = runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())
        return devices.any { it.type in HEADPHONE_TYPES }
    }

    private companion object {
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
