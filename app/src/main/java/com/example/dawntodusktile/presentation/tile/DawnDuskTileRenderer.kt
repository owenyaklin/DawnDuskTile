@file:OptIn(ExperimentalHorologistApi::class)

package com.example.dawntodusktile.presentation.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
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
        return dawnDuskTileLayout(context, deviceParameters, state)
    }

}

private fun dawnDuskTileLayout(
    context: Context, deviceParameters: DeviceParametersBuilders.DeviceParameters, state: DawnDuskTileState
): EdgeContentLayout {
    return EdgeContentLayout.Builder(deviceParameters).setResponsiveContentInsetEnabled(true).setContent(
        LayoutElementBuilders.Box.Builder().addContent(
            LayoutElementBuilders.Text.Builder().setText("Hello").build()
        ).build()
    ).setPrimaryLabelTextContent(LayoutElementBuilders.Text.Builder().setText("Primary Label").build())
        .setEdgeContent(CircularProgressIndicator.Builder().setStartAngle(-90F).setEndAngle(90F).setProgress(0.6F).build())
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
@Preview(device = WearDevices.SQUARE)
fun messagingTileLayoutPreview(context: Context): TilePreviewData {

    return TilePreviewData { request ->
        DawnDuskTileRenderer(context).renderTimeline(
            DawnDuskTileState(currentDates), request
        )
    }
}