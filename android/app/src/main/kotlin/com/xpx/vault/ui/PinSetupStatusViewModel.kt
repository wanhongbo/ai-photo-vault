package com.xpx.vault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.data.db.PhotoVaultDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PinSetupStatusViewModel @Inject constructor(
    db: PhotoVaultDatabase,
) : ViewModel() {

    val hasPin = db.securitySettingDao()
        .observeSingleton()
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}
