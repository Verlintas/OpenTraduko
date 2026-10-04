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

package com.opentraduko.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opentraduko.app.OpenTradukoApp
import com.opentraduko.app.feature.conversation.ConversationScreen
import com.opentraduko.app.feature.history.HistoryDetailScreen
import com.opentraduko.app.feature.history.HistoryScreen
import com.opentraduko.app.feature.home.HomeScreen
import com.opentraduko.app.feature.listening.ListeningScreen
import com.opentraduko.app.feature.models.ModelsScreen
import com.opentraduko.app.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CONVERSATION = "conversation"
    const val LISTENING = "listening"
    const val SETTINGS = "settings"
    const val MODELS = "models"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{sessionId}"

    fun historyDetail(sessionId: String) = "history/$sessionId"
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val container = remember {
        (context.applicationContext as OpenTradukoApp).container
    }
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenConversation = { navController.navigate(Routes.CONVERSATION) },
                onOpenListening = { navController.navigate(Routes.LISTENING) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenModels = { navController.navigate(Routes.MODELS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.CONVERSATION) {
            ConversationScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(Routes.MODELS) },
            )
        }
        composable(Routes.LISTENING) {
            ListeningScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(Routes.MODELS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(Routes.MODELS) },
            )
        }
        composable(Routes.MODELS) {
            ModelsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Routes.historyDetail(id)) },
            )
        }
        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            HistoryDetailScreen(
                container = container,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
