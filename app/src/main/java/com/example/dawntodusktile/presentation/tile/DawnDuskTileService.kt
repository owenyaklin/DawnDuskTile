package com.example.dawntodusktile.presentation.tile

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.example.dawntodusktile.presentation.DawnDuskRepo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private const val RESOURCES_VERSION = "0"

@OptIn(ExperimentalHorologistApi::class)
class DawnDuskTileService : SuspendingTileService() {
    private lateinit var repo: DawnDuskRepo
    private lateinit var renderer: DawnDuskTileRenderer
    private lateinit var tileStateFlow: StateFlow<DawnDuskTileState?>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        repo = DawnDuskRepo(this)
        renderer = DawnDuskTileRenderer(this)
        tileStateFlow = repo.getInitialValues().map { dawnDuskDates -> DawnDuskTileState(dawnDuskDates) }.stateIn(
            lifecycleScope, started = SharingStarted.Eagerly, initialValue = null
        )
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        Log.d(TAG, "tileRequest")
        val tileState = latestTileState()
        return renderer.renderTimeline(tileState, requestParams)
    }

    private suspend fun latestTileState(): DawnDuskTileState {
        Log.d(TAG, "latestTileState")
        val today = LocalDate.now().toString()

        // 1. Try to get current state from flow quickly.
        val tileState = withTimeoutOrNull(500.milliseconds) {
            tileStateFlow.filterNotNull().first()
        }

        val result = tileState ?: DawnDuskTileState(emptyList())

        // 2. Check if we need to refresh: missing or doesn't have today's data
        val isStale = result.dawnDuskDates.none { it.date == today }

        if (isStale) {
            Log.d(TAG, "Data is missing or stale. Triggering background refresh...")
            // 3. Trigger refresh in background to avoid blocking the tile response.
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val location = fetchLocation()
                    if (location != null) {
                        refreshData(location)
                        // 4. Request an update so the tile shows the new data once saved
                        getUpdater(this@DawnDuskTileService).requestUpdate(DawnDuskTileService::class.java)
                    } else {
                        Log.w(TAG, "Could not fetch location for refresh")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background refresh failed", e)
                }
            }
        }

        return result
    }

    private suspend fun fetchLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        // 1. Try to get the Last Known Location first (Low Power)
        val lastLocation = try {
            suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }
        } catch (e: Exception) {
            null
        }

        // If lastLocation is "fresh enough" (e.g., within 30 minutes), use it to save battery
        if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 30 * 60 * 1000) {
            Log.d(TAG, "Using fresh lastLocation: ${lastLocation.latitude}")
            return lastLocation
        }

        // 2. If no fresh lastLocation, request a "Balanced" current location (Medium Power)
        Log.d(TAG, "No fresh lastLocation, requesting balanced current location...")
        return withTimeoutOrNull(10000.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val cancellationTokenSource = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    Log.d(TAG, "Balanced location fetched: $location")
                    if (continuation.isActive) continuation.resume(location)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get balanced location", exception)
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
            }
        }
    }

    private suspend fun refreshData(location: Location) {
        Log.d(TAG, "Refreshing data for location: ${location.latitude}, ${location.longitude}")
        // For now, we still use mock dates, but we acknowledge the location.
        repo.updateDateDawnDusk(DawnDuskRepo.currentDates)
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    companion object {
        const val TAG = "DawnDuskTileService"
    }
}
