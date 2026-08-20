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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dawntodusktile.presentation.DawnDuskRefreshWorker
import com.example.dawntodusktile.presentation.DawnDuskRepo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        tileStateFlow = repo.getInitialValues().map { data ->
            DawnDuskTileState(
                data.dates, data.locationName, data.latitude, data.longitude, data.lastUpdatedMillis
            )
        }.stateIn(
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
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        // 1. Try to get current state from flow quickly.
        val tileState = withTimeoutOrNull(500.milliseconds) {
            tileStateFlow.filterNotNull().first()
        }

        val result = tileState ?: DawnDuskTileState(emptyList())

        // 2. Check if we need to refresh
        // Date check: We want today's data to be the middle element of our 3-day window
        val isDateStale = result.dawnDuskDates.size < 3 || result.dawnDuskDates[1].date != today

        // Time check: Data is more than 4 hours old
        val isTimeStale = (now - result.lastUpdatedMillis) > 4 * 60 * 60 * 1000L
        val staleTimeLeft = (4 * 60 * 60 * 1000L) - (now - result.lastUpdatedMillis)
        Log.d(TAG, "isTimeStale = $staleTimeLeft")

        // Distance check: Check if system's cached location is > 50km from stored location
        val isDistanceStale = result.lastUpdatedMillis > 0 && checkDistanceStale(result.latitude, result.longitude)

        val isStale = isDateStale || isTimeStale || isDistanceStale

        if (isStale) {
            Log.d(
                TAG,
                "Data is stale (date=$isDateStale, time=$isTimeStale, dist=$isDistanceStale). Triggering background refresh via WorkManager..."
            )

            val workRequest = OneTimeWorkRequestBuilder<DawnDuskRefreshWorker>().build()

            WorkManager.getInstance(this).enqueueUniqueWork(
                "DawnDuskRefresh", ExistingWorkPolicy.KEEP, workRequest
            )
        }

        return result
    }

    private suspend fun checkDistanceStale(storedLat: Double, storedLng: Double): Boolean {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val lastLocation = suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }

            if (lastLocation != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    storedLat, storedLng, lastLocation.latitude, lastLocation.longitude, results
                )
                val distanceInMeters = results[0]
                Log.d(TAG, "Distance from stored location: ${distanceInMeters / 1000}km")
                distanceInMeters > 50_000 // 50km threshold
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
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
