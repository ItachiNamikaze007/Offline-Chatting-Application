package com.offlinemesh.app.domain.usecase

import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.domain.repository.IdentityRepository

class GetOrCreateIdentityUseCase(
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(): UserIdentity {
        return identityRepository.getOrCreateIdentity()
    }
}

class UpdateProfileUseCase(
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(displayName: String, avatarColorHex: String): UserIdentity {
        return identityRepository.updateProfile(displayName, avatarColorHex)
    }
}
