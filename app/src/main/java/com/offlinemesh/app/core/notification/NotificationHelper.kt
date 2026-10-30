package com.offlinemesh.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.offlinemesh.app.MainActivity
import com.offlinemesh.app.R
import com.offlinemesh.app.core.model.NotificationPrivacy
import java.io.File

object NotificationHelper {

    const val CHANNEL_MESSAGES_ID = "offlinemesh_messages_channel"
    private const val CHANNEL_MESSAGES_NAME = "Offline Messages"
    private const val CHANNEL_MESSAGES_DESC = "Notifications for incoming peer-to-peer mesh messages"

    const val CHANNEL_SERVICE_ID = "offlinemesh_service_channel"
    private const val CHANNEL_SERVICE_NAME = "Mesh Radio Service"
    private const val CHANNEL_SERVICE_DESC = "Ongoing background radio status for OfflineMesh"

    private const val GROUP_MESSAGES_KEY = "com.offlinemesh.MESSAGES_GROUP"
    private const val SUMMARY_NOTIFICATION_ID = 10001

    const val EXTRA_OPEN_CHAT = "extra_open_chat"
    const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
    const val EXTRA_PEER_NAME = "extra_peer_name"
    const val EXTRA_AVATAR_COLOR = "extra_avatar_color"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_MESSAGES_DESC
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                CHANNEL_SERVICE_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_SERVICE_DESC
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun showMessageNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        content: String,
        avatarImagePath: String? = null,
        avatarColorHex: String? = null,
        privacy: NotificationPrivacy = NotificationPrivacy.SHOW_FULL_MESSAGE
    ) {
        // Suppress notification if user is currently viewing this conversation
        if (ActiveConversationTracker.isConversationActive(conversationId)) {
            return
        }

        createNotificationChannels(context)

        val title = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE,
            NotificationPrivacy.SHOW_SENDER_ONLY -> senderName
            NotificationPrivacy.HIDE_PREVIEW -> "OfflineMesh"
        }

        val text = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE -> content
            NotificationPrivacy.SHOW_SENDER_ONLY -> "New message"
            NotificationPrivacy.HIDE_PREVIEW -> "New encrypted message received"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CHAT, true)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_PEER_NAME, senderName)
            putExtra(EXTRA_AVATAR_COLOR, avatarColorHex ?: "#3B82F6")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_MESSAGES_KEY)

        // Load avatar bitmap if available
        if (privacy != NotificationPrivacy.HIDE_PREVIEW && !avatarImagePath.isNullOrEmpty()) {
            try {
                val file = File(avatarImagePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        builder.setLargeIcon(bitmap)
                    }
                }
            } catch (_: Exception) {
            }
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = conversationId.hashCode()
            notificationManager.notify(notificationId, builder.build())

            // Summary notification for grouping multiple conversation messages cleanly
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("OfflineMesh Messages"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_MESSAGES_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted yet
        }
    }

    fun cancelMessageNotification(context: Context, conversationId: String) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(conversationId.hashCode())
        } catch (_: Exception) {
        }
    }

    fun cancelAllNotifications(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancelAll()
        } catch (_: Exception) {
        }
    }
}
