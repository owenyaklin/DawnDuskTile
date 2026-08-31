package com.example.dawntodusktile.presentation.tile

import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import com.example.dawntodusktile.R
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dawntodusktile.presentation.DawnDuskRefreshWorker
import com.example.dawntodusktile.presentation.DawnDuskRepo
import com.example.dawntodusktile.presentation.SolarDataUtils
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
import kotlinx.coroutines.withTimeoutOrNull
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

        // 1. Try to get current state from flow quickly.
        val tileState = withTimeoutOrNull(500.milliseconds) {
            tileStateFlow.filterNotNull().first()
        }

        val result = tileState ?: DawnDuskTileState(emptyList())

        // 2. Check if we need to refresh
        val isStale = SolarDataUtils.isStale(result, this, fusedLocationClient)

        if (isStale) {
            Log.d(TAG, "Data is stale. Triggering background refresh via WorkManager...")

            val workRequest = OneTimeWorkRequestBuilder<DawnDuskRefreshWorker>().build()

            WorkManager.getInstance(this).enqueueUniqueWork(
                "DawnDuskRefresh", ExistingWorkPolicy.KEEP, workRequest
            )
            return result.copy(isLoading = true)
        }

        return result
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                ID_IC_REFRESH,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_refresh)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    companion object {
        const val TAG = "DawnDuskTileService"
        const val ID_IC_REFRESH = "ic_refresh"
    }
}
