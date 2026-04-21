package com.example.dawntodusktile.tile

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout

@OptIn(ExperimentalHorologistApi::class)
class DawnDuskTileRenderer(context: Context) :
    SingleTileLayoutRenderer<DawnDuskTileState, Unit>(context) {

    override fun renderTile(
        state: DawnDuskTileState,
        deviceParameters: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        Text.Builder(context, state.locationName)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(0xFFBB86FC.toInt()))
                            .build()
                    )
                    .addContent(
                        Text.Builder(context, "Sunrise: ${state.sunrise}")
                            .setTypography(Typography.TYPOGRAPHY_BODY1)
                            .build()
                    )
                    .addContent(
                        Text.Builder(context, "Sunset: ${state.sunset}")
                            .setTypography(Typography.TYPOGRAPHY_BODY1)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override fun ResourceBuilders.Resources.Builder.produceRequestedResources(
        resourceState: Unit,
        deviceParameters: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters,
        resourceIds: List<String>
    ) {
        // Add resources like icons here if needed
    }
}
