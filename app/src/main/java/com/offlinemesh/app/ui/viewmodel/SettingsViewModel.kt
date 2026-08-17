package com.offlinemesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase
) : ViewModel() {

    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        loadIdentity()
    }

    fun loadIdentity() {
        viewModelScope.launch {
            _userIdentity.value = getOrCreateIdentityUseCase()
        }
    }

    fun updateProfile(displayName: String, avatarColorHex: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val updated = updateProfileUseCase(displayName, avatarColorHex)
            _userIdentity.value = updated
            _isSaving.value = false
        }
    }

    class Factory(
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val updateProfileUseCase: UpdateProfileUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                getOrCreateIdentityUseCase,
                updateProfileUseCase
            ) as T
        }
    }
}
