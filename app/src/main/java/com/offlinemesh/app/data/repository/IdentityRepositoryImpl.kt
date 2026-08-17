package com.offlinemesh.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.core.crypto.IdentityGenerator
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IdentityRepositoryImpl(
    private val context: Context,
    private val keystoreManager: AndroidKeystoreManager
) : IdentityRepository {

    companion object {
        private const val PREFS_NAME = "offlinemesh_identity_prefs"
        private const val KEY_USER_ID = "pref_user_id"
        private const val KEY_DISPLAY_NAME = "pref_display_name"
        private const val KEY_AVATAR_COLOR = "pref_avatar_color"
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

    init {
        loadExistingIdentity()
    }

    private fun loadExistingIdentity() {
        val userId = prefs.getString(KEY_USER_ID, null)
        if (userId != null) {
            val displayName = prefs.getString(KEY_DISPLAY_NAME, "Mesh User") ?: "Mesh User"
            val avatarColor = prefs.getString(KEY_AVATAR_COLOR, DEFAULT_AVATAR_COLORS.first()) ?: DEFAULT_AVATAR_COLORS.first()
            val createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
            val publicKey = keystoreManager.getPublicKeyBase64()

            val identity = UserIdentity(
                userId = userId,
                displayName = displayName,
                publicKey = publicKey,
                avatarColorHex = avatarColor,
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
            .putLong(KEY_CREATED_AT, createdAt)
            .apply()

        val newIdentity = UserIdentity(
            userId = generatedUserId,
            displayName = defaultDisplayName,
            publicKey = publicKeyBase64,
            avatarColorHex = defaultAvatar,
            createdAt = createdAt
        )

        _currentIdentity.value = newIdentity
        return newIdentity
    }

    override suspend fun updateProfile(displayName: String, avatarColorHex: String): UserIdentity {
        val current = getOrCreateIdentity()
        val updated = current.copy(
            displayName = displayName.trim().ifEmpty { current.displayName },
            avatarColorHex = avatarColorHex
        )

        prefs.edit()
            .putString(KEY_DISPLAY_NAME, updated.displayName)
            .putString(KEY_AVATAR_COLOR, updated.avatarColorHex)
            .apply()

        _currentIdentity.value = updated
        return updated
    }
}
