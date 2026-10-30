package com.offlinemesh.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.offlinemesh.app.core.notification.NotificationHelper
import com.offlinemesh.app.core.service.MeshForegroundService
import com.offlinemesh.app.ui.navigation.AppNavigation
import com.offlinemesh.app.ui.navigation.PendingChatNavigation
import com.offlinemesh.app.ui.theme.OfflineMeshTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val _pendingNavigation = MutableStateFlow<PendingChatNavigation?>(null)
    val pendingNavigation = _pendingNavigation.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannels(this)
        MeshForegroundService.start(this)

        handleIncomingIntent(intent)

        val app = application as OfflineMeshApplication
        val container = app.container

        setContent {
            OfflineMeshTheme {
                AppNavigation(
                    container = container,
                    pendingNavigation = pendingNavigation,
                    onClearPendingNavigation = { _pendingNavigation.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CHAT, false)) {
            val convId = intent.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID)
            val peerName = intent.getStringExtra(NotificationHelper.EXTRA_PEER_NAME) ?: "Peer"
            val avatarColor = intent.getStringExtra(NotificationHelper.EXTRA_AVATAR_COLOR) ?: "#3B82F6"

            if (!convId.isNullOrEmpty()) {
                _pendingNavigation.value = PendingChatNavigation(
                    conversationId = convId,
                    peerName = peerName,
                    avatarColor = avatarColor
                )
            }
        }
    }
}
