package com.example.dawntodusktile.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dawntodusktile.presentation.tile.DawnDuskTileState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DawnDuskViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = DawnDuskRepo(application)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    val uiState: StateFlow<DawnDuskTileState?> = repo.getInitialValues().map { data ->
        DawnDuskTileState(
            data.dates, data.locationName, data.latitude, data.longitude, data.lastUpdatedMillis,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            refreshIfStale()
        }
    }

    private suspend fun refreshIfStale() {
        val currentState = uiState.filterNotNull().first()

        if (SolarDataUtils.isStale(currentState, getApplication(), fusedLocationClient)) {
            Log.d(TAG, "Data is stale. Triggering background refresh...")
            val workRequest = OneTimeWorkRequestBuilder<DawnDuskRefreshWorker>().build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "DawnDuskRefresh", ExistingWorkPolicy.KEEP, workRequest,
            )
        }
    }

    companion object {
        private const val TAG = "DawnDuskViewModel"
    }
}
