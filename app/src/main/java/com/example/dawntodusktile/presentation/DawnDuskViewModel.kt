package com.example.dawntodusktile.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dawntodusktile.presentation.tile.DawnDuskTileState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DawnDuskViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = DawnDuskRepo(application)

    val uiState: StateFlow<DawnDuskTileState?> = repo.getInitialValues().map { data ->
        DawnDuskTileState(
            data.dates, data.locationName, data.latitude, data.longitude, data.lastUpdatedMillis,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}
