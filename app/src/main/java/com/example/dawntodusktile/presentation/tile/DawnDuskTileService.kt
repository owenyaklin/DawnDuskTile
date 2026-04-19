package com.example.dawntodusktile.presentation.tile

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
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
        val tileState = withTimeoutOrNull(500) {
            tileStateFlow.filterNotNull().first()
        }

        val result = tileState ?: DawnDuskTileState(emptyList())

        // 2. Check if we need to refresh: missing, empty, or doesn't have today's data
        val isStale = result.dawnDuskDates.none { it.date == today }

        if (isStale) {
            // PREVENT LOOP: If we already have the mock 2026 data, don't trigger another refresh.
            val alreadyHasMockData = result.dawnDuskDates.any { it.date.startsWith("2026") }
            
            if (!alreadyHasMockData) {
                Log.d(TAG, "Data is missing or stale. Triggering background refresh...")
                // 3. Trigger refresh in background to avoid blocking the tile response.
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val location = fetchLocation()
                        refreshData(location)
                        // 4. Request an update so the tile shows the new data once saved
                        TileService.getUpdater(this@DawnDuskTileService)
                            .requestUpdate(DawnDuskTileService::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Background refresh failed", e)
                    }
                }
            } else {
                Log.d(TAG, "Data is stale but mock data is already present. Skipping refresh.")
            }
        }

        if (result.dawnDuskDates.isNotEmpty()) {
            val firstDate = result.dawnDuskDates.first().date
            Log.d(TAG, "Returning state. Current date: $today, Data date: $firstDate")
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(): Location? = withTimeoutOrNull(3000) {
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get location", exception)
                if (continuation.isActive) continuation.resume(null)
            }.addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }

    private suspend fun refreshData(location: Location?) {
        Log.d(TAG, "Refreshing data for location: $location")
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
