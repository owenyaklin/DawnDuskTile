@file:OptIn(ExperimentalHorologistApi::class)

package com.example.dawntodusktile.presentation.tile

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DawnDuskTileRenderer(context: Context) : SingleTileLayoutRenderer<DawnDuskTileState, Bitmap>(context) {

    override val freshnessIntervalMillis: Long = 60 * 1000

    override fun renderTile(
        state: DawnDuskTileState, deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {

        return dawnDuskTileLayout(context, deviceParameters, state)
    }

    companion object {
        const val TAG = "DawnDuskTileRenderer"
    }
}

private fun dawnDuskTileLayout(
    context: Context, deviceParameters: DeviceParametersBuilders.DeviceParameters, state: DawnDuskTileState
): EdgeContentLayout {
    state.dawnDuskDates.forEach { dawnDuskDate ->
        Log.d(DawnDuskTileRenderer.TAG, "dawnDuskDate.date = " + dawnDuskDate.date)
    }
    val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss", Locale.US)
    val currentDate = sdf.format(Date())
    Log.d(DawnDuskTileRenderer.TAG, " C DATE is  $currentDate")
    return EdgeContentLayout.Builder(deviceParameters).setResponsiveContentInsetEnabled(true).setContent(
        LayoutElementBuilders.Box.Builder().addContent(
            LayoutElementBuilders.Text.Builder().setText(currentDate).build()
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