package com.example.dawntodusktile.tile

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalHorologistApi::class)
class DawnDuskTileService : SuspendingTileService() {
    private val dataStore by lazy { DawnDuskTileDataStore(this) }
    private val renderer by lazy { DawnDuskTileRenderer(this) }
    private val repository by lazy { DawnDuskRepository(this) }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return renderer.produceRequestedResources(Unit, requestParams)
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        if (requestParams.currentState.lastClickableId == "refresh") {
            repository.updateTileData(dataStore)
        }

        val state = dataStore.tileStateFlow.first()
        return renderer.renderTimeline(state, requestParams)
    }
}
