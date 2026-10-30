package com.offlinemesh.app.domain.usecase

import android.net.Uri
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

class SaveProfilePhotoUseCase(
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(imageUri: Uri): UserIdentity {
        return identityRepository.saveProfilePhoto(imageUri)
    }
}

class RemoveProfilePhotoUseCase(
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(): UserIdentity {
        return identityRepository.removeProfilePhoto()
    }
}
