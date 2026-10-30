package com.offlinemesh.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.core.crypto.IdentityGenerator
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.core.storage.AvatarStorageHelper
import com.offlinemesh.app.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class IdentityRepositoryImpl(
    private val context: Context,
    private val keystoreManager: AndroidKeystoreManager
) : IdentityRepository {

    companion object {
        private const val PREFS_NAME = "offlinemesh_identity_prefs"
        private const val KEY_USER_ID = "pref_user_id"
        private const val KEY_DISPLAY_NAME = "pref_display_name"
        private const val KEY_AVATAR_COLOR = "pref_avatar_color"
        private const val KEY_AVATAR_IMAGE_PATH = "pref_avatar_image_path"
        private const val KEY_AVATAR_VERSION = "pref_avatar_version"
        private const val KEY_NOTIFICATION_PRIVACY = "pref_notification_privacy"
        private const val KEY_CREATED_AT = "pref_created_at"

        private val DEFAULT_AVATAR_COLORS = listOf(
            "#3B82F6", // Blue
            "#10B981", // Emerald
            "#8B5CF6", // Purple
            "#F59E0B", // Amber
            "#EC4899", // Pink
            "#06B6D4", // Cyan
            "#6366F1"  // Indigo
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _currentIdentity = MutableStateFlow<UserIdentity?>(null)
    override val currentIdentity: StateFlow<UserIdentity?> = _currentIdentity.asStateFlow()

    private val _notificationPrivacy = MutableStateFlow(loadNotificationPrivacy())
    override val notificationPrivacy: StateFlow<com.offlinemesh.app.core.model.NotificationPrivacy> = _notificationPrivacy.asStateFlow()

    init {
        loadExistingIdentity()
    }

    private fun loadNotificationPrivacy(): com.offlinemesh.app.core.model.NotificationPrivacy {
        val savedName = prefs.getString(KEY_NOTIFICATION_PRIVACY, null) ?: return com.offlinemesh.app.core.model.NotificationPrivacy.SHOW_FULL_MESSAGE
        return try {
            com.offlinemesh.app.core.model.NotificationPrivacy.valueOf(savedName)
        } catch (_: Exception) {
            com.offlinemesh.app.core.model.NotificationPrivacy.SHOW_FULL_MESSAGE
        }
    }

    private fun loadExistingIdentity() {
        val userId = prefs.getString(KEY_USER_ID, null)
        if (userId != null) {
            val displayName = prefs.getString(KEY_DISPLAY_NAME, "Mesh User") ?: "Mesh User"
            val avatarColor = prefs.getString(KEY_AVATAR_COLOR, DEFAULT_AVATAR_COLORS.first()) ?: DEFAULT_AVATAR_COLORS.first()
            val rawImagePath = prefs.getString(KEY_AVATAR_IMAGE_PATH, null)
            val avatarVersion = prefs.getLong(KEY_AVATAR_VERSION, 0L)
            val createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
            val publicKey = keystoreManager.getPublicKeyBase64()

            val validatedImagePath = if (rawImagePath != null && File(rawImagePath).exists()) rawImagePath else null

            val identity = UserIdentity(
                userId = userId,
                displayName = displayName,
                publicKey = publicKey,
                avatarColorHex = avatarColor,
                avatarImagePath = validatedImagePath,
                avatarVersion = avatarVersion,
                createdAt = createdAt
            )
            _currentIdentity.value = identity
        }
    }

    override suspend fun getOrCreateIdentity(): UserIdentity {
        _currentIdentity.value?.let { return it }

        // Ensure keypair exists
        val keyPair = keystoreManager.getOrCreateKeyPair()
        val publicKeyBase64 = keystoreManager.getPublicKeyBase64()

        // Generate OFC-XXXXXXXX from public key
        val generatedUserId = IdentityGenerator.generateFromPublicKey(keyPair.public.encoded)
        val shortSuffix = if (generatedUserId.length >= 8) generatedUserId.takeLast(4) else "Peer"
        val defaultDisplayName = "Mesh $shortSuffix"
        val defaultAvatar = DEFAULT_AVATAR_COLORS.random()
        val createdAt = System.currentTimeMillis()

        prefs.edit()
            .putString(KEY_USER_ID, generatedUserId)
            .putString(KEY_DISPLAY_NAME, defaultDisplayName)
            .putString(KEY_AVATAR_COLOR, defaultAvatar)
            .putLong(KEY_AVATAR_VERSION, 0L)
            .putLong(KEY_CREATED_AT, createdAt)
            .commit()

        val newIdentity = UserIdentity(
            userId = generatedUserId,
            displayName = defaultDisplayName,
            publicKey = publicKeyBase64,
            avatarColorHex = defaultAvatar,
            avatarImagePath = null,
            avatarVersion = 0L,
            createdAt = createdAt
        )

        _currentIdentity.value = newIdentity
        return newIdentity
    }

    override suspend fun updateProfile(
        displayName: String,
        avatarColorHex: String,
        avatarImagePath: String?,
        avatarVersion: Long?
    ): UserIdentity {
        val current = getOrCreateIdentity()
        val trimmed = displayName.trim().ifEmpty { current.displayName }
        val finalImagePath = avatarImagePath ?: current.avatarImagePath
        val finalVersion = avatarVersion ?: current.avatarVersion

        val updated = current.copy(
            displayName = trimmed,
            avatarColorHex = avatarColorHex,
            avatarImagePath = finalImagePath,
            avatarVersion = finalVersion
        )

        val editor = prefs.edit()
            .putString(KEY_DISPLAY_NAME, updated.displayName)
            .putString(KEY_AVATAR_COLOR, updated.avatarColorHex)
            .putLong(KEY_AVATAR_VERSION, updated.avatarVersion)

        if (updated.avatarImagePath != null) {
            editor.putString(KEY_AVATAR_IMAGE_PATH, updated.avatarImagePath)
        } else {
            editor.remove(KEY_AVATAR_IMAGE_PATH)
        }
        editor.commit()

        _currentIdentity.value = updated
        return updated
    }

    override suspend fun saveProfilePhoto(imageUri: Uri): UserIdentity {
        val current = getOrCreateIdentity()
        val savedPath = AvatarStorageHelper.saveSelfAvatarFromUri(context, imageUri)
        val newVersion = System.currentTimeMillis()

        val updated = current.copy(
            avatarImagePath = savedPath,
            avatarVersion = newVersion
        )

        prefs.edit()
            .putString(KEY_AVATAR_IMAGE_PATH, savedPath)
            .putLong(KEY_AVATAR_VERSION, newVersion)
            .commit()

        _currentIdentity.value = updated
        return updated
    }

    override suspend fun removeProfilePhoto(): UserIdentity {
        val current = getOrCreateIdentity()
        AvatarStorageHelper.deleteSelfAvatar(context)
        val newVersion = System.currentTimeMillis()

        val updated = current.copy(
            avatarImagePath = null,
            avatarVersion = newVersion
        )

        prefs.edit()
            .remove(KEY_AVATAR_IMAGE_PATH)
            .putLong(KEY_AVATAR_VERSION, newVersion)
            .commit()

        _currentIdentity.value = updated
        return updated
    }

    override suspend fun setNotificationPrivacy(privacy: com.offlinemesh.app.core.model.NotificationPrivacy) {
        prefs.edit()
            .putString(KEY_NOTIFICATION_PRIVACY, privacy.name)
            .commit()
        _notificationPrivacy.value = privacy
    }
}
