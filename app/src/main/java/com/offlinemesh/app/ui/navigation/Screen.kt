package com.offlinemesh.app.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Welcome : Screen("welcome")
    data object Home : Screen("home")
    data object Nearby : Screen("nearby")
    data object Communities : Screen("communities")
    data object Settings : Screen("settings")

    data object ChatDetail : Screen("chat/{conversationId}/{peerName}/{avatarColor}") {
        fun createRoute(conversationId: String, peerName: String, avatarColor: String?): String {
            val encodedName = URLEncoder.encode(peerName, StandardCharsets.UTF_8.toString())
            val encodedColor = URLEncoder.encode(avatarColor ?: "#3B82F6", StandardCharsets.UTF_8.toString())
            return "chat/$conversationId/$encodedName/$encodedColor"
        }
    }
}
