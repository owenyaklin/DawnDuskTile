@file:OptIn(ExperimentalHorologistApi::class)

package com.example.dawntodusktile.presentation.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.dawntodusktile.presentation.DawnDuskRepo.Companion.currentDates
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer

class DawnDuskTileRenderer(context: Context) : SingleTileLayoutRenderer<DawnDuskTileState, Bitmap>(context) {
    override fun renderTile(
        state: DawnDuskTileState, deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        TODO("Not yet implemented")
    }

}

private fun previewResources() = Resources.Builder().build()

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun messagingTileLayoutPreview(context: Context): TilePreviewData {

    return TilePreviewData({ previewResources() }) { request ->
        DawnDuskTileRenderer(context).renderTimeline(
            DawnDuskTileState(currentDates), request
        )
    }
}