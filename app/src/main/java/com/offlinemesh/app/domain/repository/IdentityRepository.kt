package com.offlinemesh.app.domain.repository

import com.offlinemesh.app.core.model.UserIdentity
import kotlinx.coroutines.flow.StateFlow

interface IdentityRepository {
    val currentIdentity: StateFlow<UserIdentity?>

    suspend fun getOrCreateIdentity(): UserIdentity
    suspend fun updateProfile(displayName: String, avatarColorHex: String): UserIdentity
}
