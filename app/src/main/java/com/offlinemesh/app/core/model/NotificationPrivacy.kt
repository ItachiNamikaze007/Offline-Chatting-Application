package com.offlinemesh.app.core.model

enum class NotificationPrivacy(val title: String, val description: String) {
    SHOW_FULL_MESSAGE("Show full message", "Displays sender name and message content"),
    SHOW_SENDER_ONLY("Show sender only", "Displays sender name without message content"),
    HIDE_PREVIEW("Hide message preview", "Displays 'New encrypted message' without sender details")
}
