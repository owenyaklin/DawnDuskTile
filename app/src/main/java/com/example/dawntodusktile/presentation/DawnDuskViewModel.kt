package com.example.dawntodusktile.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.dawntodusktile.presentation.tile.DawnDuskTileState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DawnDuskViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = DawnDuskRepo(application)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val workManager = WorkManager.getInstance(application)
    private val refreshWorkInfo = workManager.getWorkInfosForUniqueWorkFlow("DawnDuskRefresh")
        .map { it.any { info -> info.state == androidx.work.WorkInfo.State.RUNNING || info.state == androidx.work.WorkInfo.State.ENQUEUED } }

    val uiState: StateFlow<DawnDuskTileState?> = 
        combine(repo.getInitialValues(), refreshWorkInfo) { data, loading ->
            DawnDuskTileState(
                data.dates, data.locationName, data.latitude, data.longitude, data.lastUpdatedMillis, loading
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

    fun forceRefresh() {
        SolarDataUtils.forceRefresh(getApplication())
    }

    private suspend fun refreshIfStale() {
        val currentState = uiState.filterNotNull().first()

        if (SolarDataUtils.isStale(currentState, getApplication(), fusedLocationClient)) {
            Log.d(TAG, "Data is stale. Triggering background refresh...")
            SolarDataUtils.forceRefresh(getApplication())
        }
    }

    companion object {
        private const val TAG = "DawnDuskViewModel"
    }
}
