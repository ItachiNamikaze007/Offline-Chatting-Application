package com.offlinemesh.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.NotificationPrivacy
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.domain.repository.ChatRepository
import com.offlinemesh.app.domain.repository.IdentityRepository
import com.offlinemesh.app.domain.usecase.ClearAllMessagesUseCase
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.RemoveProfilePhotoUseCase
import com.offlinemesh.app.domain.usecase.SaveProfilePhotoUseCase
import com.offlinemesh.app.domain.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val clearAllMessagesUseCase: ClearAllMessagesUseCase,
    private val saveProfilePhotoUseCase: SaveProfilePhotoUseCase,
    private val removeProfilePhotoUseCase: RemoveProfilePhotoUseCase,
    private val identityRepository: IdentityRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    val notificationPrivacy: StateFlow<NotificationPrivacy> = identityRepository.notificationPrivacy

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        loadIdentity()
    }

    fun loadIdentity() {
        viewModelScope.launch {
            _userIdentity.value = getOrCreateIdentityUseCase()
        }
    }

    fun updateProfile(displayName: String, avatarColorHex: String) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) {
            _snackbarMessage.value = "Display name cannot be empty"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = updateProfileUseCase(trimmed, avatarColorHex)
                _userIdentity.value = updated
                _snackbarMessage.value = "Profile saved"
            } catch (e: Exception) {
                _snackbarMessage.value = "Couldn't save profile. Try again."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onProfilePhotoSelected(imageUri: Uri) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = saveProfilePhotoUseCase(imageUri)
                _userIdentity.value = updated
                chatRepository.syncProfilePhoto()
                _snackbarMessage.value = "Profile photo updated"
            } catch (e: Exception) {
                _snackbarMessage.value = "Couldn't update profile photo"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onRemoveProfilePhoto() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = removeProfilePhotoUseCase()
                _userIdentity.value = updated
                chatRepository.syncProfilePhoto()
                _snackbarMessage.value = "Profile photo removed"
            } catch (e: Exception) {
                _snackbarMessage.value = "Couldn't remove photo"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setNotificationPrivacy(privacy: NotificationPrivacy) {
        viewModelScope.launch {
            identityRepository.setNotificationPrivacy(privacy)
            _snackbarMessage.value = "Notification privacy updated"
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch {
            try {
                clearAllMessagesUseCase()
                _snackbarMessage.value = "All chat history cleared"
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to clear history"
            }
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    class Factory(
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val updateProfileUseCase: UpdateProfileUseCase,
        private val clearAllMessagesUseCase: ClearAllMessagesUseCase,
        private val saveProfilePhotoUseCase: SaveProfilePhotoUseCase,
        private val removeProfilePhotoUseCase: RemoveProfilePhotoUseCase,
        private val identityRepository: IdentityRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                getOrCreateIdentityUseCase,
                updateProfileUseCase,
                clearAllMessagesUseCase,
                saveProfilePhotoUseCase,
                removeProfilePhotoUseCase,
                identityRepository,
                chatRepository
            ) as T
        }
    }
}
