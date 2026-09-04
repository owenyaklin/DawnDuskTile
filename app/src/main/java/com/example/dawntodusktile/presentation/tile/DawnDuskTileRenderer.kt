@file:OptIn(ExperimentalHorologistApi::class)

package com.example.dawntodusktile.presentation.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.dawntodusktile.presentation.DawnDuskRepo.Companion.currentDates
import com.example.dawntodusktile.presentation.SolarDataUtils
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class DawnDuskTileRenderer(context: Context) : SingleTileLayoutRenderer<DawnDuskTileState, Bitmap>(context) {

    override val freshnessIntervalMillis: Long = 60 * 1000

    override fun renderTile(
        state: DawnDuskTileState, deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {

        return dawnDuskTileLayout(context, deviceParameters, state)
    }
}

private fun dawnDuskTileLayout(
    context: Context, deviceParameters: DeviceParametersBuilders.DeviceParameters, state: DawnDuskTileState
): EdgeContentLayout {
    if (state.dawnDuskDates.size < 3 || state.isLoading) {
        val builder = EdgeContentLayout.Builder(deviceParameters).setResponsiveContentInsetEnabled(true).setContent(
                Text.Builder(context, if (state.isLoading) "Refreshing..." else "Fetching data...")
                    .setTypography(Typography.TYPOGRAPHY_BODY2).build()
            )

        if (!state.isLoading) {
            builder.setPrimaryLabelTextContent(
                Text.Builder(context, state.locationName.ifEmpty { "Loading..." })
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(ColorBuilders.argb(0xFFFDE293.toInt())).build()
            )
        }
        return builder.build()
    }

    val now = LocalDateTime.now()
    val solarState =
        SolarDataUtils.calculateSolarState(now, state.dawnDuskDates) ?: return EdgeContentLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true).build()

    val row1Text = solarState.row1Text
    val time1 = solarState.time1
    val row2Text = solarState.row2Text
    val time2 = solarState.time2
    val progress = solarState.progress
    val isDay = solarState.isDay
    val dawnTodayDT = solarState.dawnTodayDT
    val duskTodayDT = solarState.duskTodayDT
    val sunriseToday = solarState.sunriseToday
    val sunsetToday = solarState.sunsetToday
    val todayDate = solarState.todayDate

    // Colors
    val nightColor = 0xFF003366.toInt()
    val dawnDuskColor = 0xFFFF9800.toInt() // Orange
    val dayColor = 0xFFFFEB3B.toInt() // Yellow
    val trackColor = 0xFF333333.toInt()

    val edgeContent = if (!isDay) {
        // Night states (1 or 5)
        CircularProgressIndicator.Builder().setStartAngle(-90f).setEndAngle(90f).setProgress(progress).setStrokeWidth(8f)
            .setOuterMarginApplied(false).setCircularProgressIndicatorColors(
                ProgressIndicatorColors(
                    ColorBuilders.argb(trackColor), ColorBuilders.argb(nightColor)
                )
            ).build()
    } else {
        // Day transition states (2, 3, 4) - Optimized: Single Gray bar + remaining colors
        val dawnToDuskTotal = Duration.between(dawnTodayDT, duskTodayDT).toMillis().toFloat()
        val sunrisePoint =
            Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(todayDate), sunriseToday)).toMillis()
                .toFloat() / dawnToDuskTotal
        val sunsetPoint = Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(todayDate), sunsetToday)).toMillis()
            .toFloat() / dawnToDuskTotal

        val sunriseAngle = 180f * sunrisePoint
        val sunsetAngle = 180f * sunsetPoint
        val progressAngle = 180f * progress

        val arcBuilder = LayoutElementBuilders.Arc.Builder().setAnchorAngle(DimensionBuilders.degrees(-90f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)

        // 1. Consolidated Past (Gray)
        if (progressAngle > 0f) {
            arcBuilder.addContent(
                LayoutElementBuilders.ArcLine.Builder().setLength(DimensionBuilders.degrees(progressAngle))
                    .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(trackColor)).build()
            )
        }

        // 2. Future portions
        when {
            progressAngle < sunriseAngle -> {
                // Currently in Zone 1 (Dawn to Sunrise)
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder()
                        .setLength(DimensionBuilders.degrees(sunriseAngle - progressAngle))
                        .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(dawnDuskColor)).build()
                )
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder().setLength(DimensionBuilders.degrees(sunsetAngle - sunriseAngle))
                        .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(dayColor)).build()
                )
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder().setLength(DimensionBuilders.degrees(180f - sunsetAngle))
                        .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(dawnDuskColor)).build()
                )
            }

            progressAngle < sunsetAngle -> {
                // Currently in Zone 2 (Sunrise to Sunset)
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder().setLength(DimensionBuilders.degrees(sunsetAngle - progressAngle))
                        .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(dayColor)).build()
                )
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder().setLength(DimensionBuilders.degrees(180f - sunsetAngle))
                        .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(dawnDuskColor)).build()
                )
            }

            progressAngle < 180f -> {
                // Currently in Zone 3 (Sunset to Dusk)
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder().setLength(DimensionBuilders.degrees(180f - progressAngle))
                        .setThickness(DimensionBuilders.dp(8f)).setColor(ColorBuilders.argb(dawnDuskColor)).build()
                )
            }
        }

        arcBuilder.build()
    }

    return EdgeContentLayout.Builder(deviceParameters).setResponsiveContentInsetEnabled(true).setEdgeContentThickness(8f)
        .setContent(
            LayoutElementBuilders.Column.Builder()
                .addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(16f)).build()
                )
                .addContent(
                    Text.Builder(context, state.locationName).setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(ColorBuilders.argb(0xFFFDE293.toInt())).build()
                ).addContent(
                    Text.Builder(context, row1Text).setTypography(Typography.TYPOGRAPHY_BODY2)
                        .setColor(ColorBuilders.argb(0xFFDEDEDE.toInt())).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .build()
                ).addContent(
                    Text.Builder(context, time1).setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(ColorBuilders.argb(0xFFDEDEDE.toInt())).build()
                ).addContent(
                    Text.Builder(context, row2Text).setTypography(Typography.TYPOGRAPHY_BODY2)
                        .setColor(ColorBuilders.argb(0xFFDEDEDE.toInt())).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .build()
                ).addContent(
                    Text.Builder(context, time2).setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(ColorBuilders.argb(0xFFDEDEDE.toInt())).build()
                ).build()
        )
        .setSecondaryLabelTextContent(
            Button.Builder(
                context, ModifiersBuilders.Clickable.Builder().setOnClick(
                        ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder().setPackageName(context.packageName)
                                    .setClassName("com.example.dawntodusktile.presentation.TransparentRefreshActivity")
                                    .build()
                            ).build()
                    ).build()
            ).setIconContent(DawnDuskTileService.ID_IC_REFRESH).setSize(DimensionBuilders.dp(24f)).setButtonColors(
                    ButtonColors(
                        0x66333333, 0x66DEDEDE
                    )
                ).build()
        ).setEdgeContent(edgeContent).build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
@Preview(device = WearDevices.SQUARE)
fun dawnDuskTileLayoutPreview(context: Context): TilePreviewData {

    return TilePreviewData { request ->
        DawnDuskTileRenderer(context).renderTimeline(
            DawnDuskTileState(
                dawnDuskDates = currentDates,
                locationName = "Mock Location",
                latitude = 37.7749,
                longitude = -122.4194,
                lastUpdatedMillis = System.currentTimeMillis()
            ), request
        )
    }

}