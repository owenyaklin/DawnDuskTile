package com.example.dawntodusktile.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import kotlin.coroutines.resume

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
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        val isDateStale =
            (currentState.dawnDuskDates.size < 3) || (currentState.dawnDuskDates[1].date != today)
        val isTimeStale = ((now - currentState.lastUpdatedMillis) > (4 * 60 * 60 * 1000L))
        val isDistanceStale = (currentState.lastUpdatedMillis > 0) &&
                checkDistanceStale(currentState.latitude, currentState.longitude)

        if (isDateStale || isTimeStale || isDistanceStale) {
            Log.d(TAG, "Data is stale. Triggering background refresh...")
            val workRequest = OneTimeWorkRequestBuilder<DawnDuskRefreshWorker>().build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "DawnDuskRefresh", ExistingWorkPolicy.KEEP, workRequest,
            )
        }
    }

    private suspend fun checkDistanceStale(storedLat: Double, storedLng: Double): Boolean {
        if (ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val lastLocation = suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }

            if (lastLocation != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    storedLat, storedLng, lastLocation.latitude, lastLocation.longitude, results,
                )
                val distanceInMeters = results[0]
                distanceInMeters > 50_000 // 50km threshold
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "DawnDuskViewModel"
    }
}
