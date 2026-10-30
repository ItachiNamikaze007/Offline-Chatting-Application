package com.offlinemesh.app.domain.repository

import android.net.Uri
import com.offlinemesh.app.core.model.NotificationPrivacy
import com.offlinemesh.app.core.model.UserIdentity
import kotlinx.coroutines.flow.StateFlow

interface IdentityRepository {
    val currentIdentity: StateFlow<UserIdentity?>
    val notificationPrivacy: StateFlow<NotificationPrivacy>

    suspend fun getOrCreateIdentity(): UserIdentity
    suspend fun updateProfile(
        displayName: String,
        avatarColorHex: String,
        avatarImagePath: String? = null,
        avatarVersion: Long? = null
    ): UserIdentity
    suspend fun saveProfilePhoto(imageUri: Uri): UserIdentity
    suspend fun removeProfilePhoto(): UserIdentity
    suspend fun setNotificationPrivacy(privacy: NotificationPrivacy)
}
