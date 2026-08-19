@file:OptIn(ExperimentalHorologistApi::class)

package com.example.dawntodusktile.presentation.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.dawntodusktile.presentation.DateDawnDusk
import com.example.dawntodusktile.presentation.DawnDuskRepo.Companion.currentDates
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
    if (state.dawnDuskDates.size < 3) {
        return EdgeContentLayout.Builder(deviceParameters).setResponsiveContentInsetEnabled(true).setPrimaryLabelTextContent(
            LayoutElementBuilders.Text.Builder().setText(state.locationName.ifEmpty { "Loading..." }).build()
        ).setContent(LayoutElementBuilders.Text.Builder().setText("Fetching data...").build()).build()
    }

    val yesterday = state.dawnDuskDates[0]
    val today = state.dawnDuskDates[1]
    val tomorrow = state.dawnDuskDates[2]
    val now = LocalDateTime.now()
    val nowTime = now.toLocalTime()

    val dawnToday = LocalTime.parse(today.dawn)
    val sunriseToday = LocalTime.parse(today.sunrise)
    val sunsetToday = LocalTime.parse(today.sunset)
    val duskToday = LocalTime.parse(today.dusk)

    // Helper for progress calculation
    fun calculateProgress(start: LocalDateTime, end: LocalDateTime, current: LocalDateTime): Float {
        val total = Duration.between(start, end).toMillis()
        if (total <= 0) return 0f
        val elapsed = Duration.between(start, current).toMillis()
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    val duskYesterdayDT = LocalDateTime.of(LocalDate.parse(yesterday.date), LocalTime.parse(yesterday.dusk))
    val dawnTodayDT = LocalDateTime.of(LocalDate.parse(today.date), LocalTime.parse(today.dawn))
    val duskTodayDT = LocalDateTime.of(LocalDate.parse(today.date), LocalTime.parse(today.dusk))
    val dawnTomorrowDT = LocalDateTime.of(LocalDate.parse(tomorrow.date), LocalTime.parse(tomorrow.dawn))

    // Determine state, upcoming events, and progress
    val (rowParams, progress) = when {
        nowTime < dawnToday -> {
            // 1) Before dawn
            (("Dawn" to today) to ("Sunrise" to today)) to calculateProgress(duskYesterdayDT, dawnTodayDT, now)
        }

        nowTime < sunriseToday -> {
            // 2) After dawn, before sunrise
            (("Dusk" to today) to ("Sunrise" to today)) to calculateProgress(dawnTodayDT, duskTodayDT, now)
        }

        nowTime < sunsetToday -> {
            // 3) After sunrise, before sunset
            (("Dusk" to today) to ("Sunset" to today)) to calculateProgress(dawnTodayDT, duskTodayDT, now)
        }

        nowTime < duskToday -> {
            // 4) After sunset, before dusk
            (("Dusk" to today) to ("Sunrise" to tomorrow)) to calculateProgress(dawnTodayDT, duskTodayDT, now)
        }

        else -> {
            // 5) After dusk
            (("Dawn" to tomorrow) to ("Sunrise" to tomorrow)) to calculateProgress(duskTodayDT, dawnTomorrowDT, now)
        }
    }
    val row1Data = rowParams.first
    val row2Data = rowParams.second

    fun formatEvent(label: String, dateDawnDusk: DateDawnDusk, now: LocalDateTime): Pair<String, String> {
        val timeStr = when (label) {
            "Dawn" -> dateDawnDusk.dawn
            "Sunrise" -> dateDawnDusk.sunrise
            "Sunset" -> dateDawnDusk.sunset
            "Dusk" -> dateDawnDusk.dusk
            else -> ""
        }
        val eventTime = LocalTime.parse(timeStr)
        val eventDate = LocalDate.parse(dateDawnDusk.date)
        val eventDateTime = LocalDateTime.of(eventDate, eventTime)

        val displayTime = eventTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        val duration = Duration.between(now, eventDateTime)
        val hours = duration.toHours()
        val minutes = abs(duration.toMinutes() % 60)

        return ("$label: $displayTime") to ("${hours}h ${minutes}m")
    }

    val (row1Text, time1) = formatEvent(row1Data.first, row1Data.second, now)
    val (row2Text, time2) = formatEvent(row2Data.first, row2Data.second, now)

    // Colors
    val nightColor = 0xFF003366.toInt()
    val dawnDuskColor = 0xFFFF9800.toInt() // Orange
    val dayColor = 0xFFFFEB3B.toInt() // Yellow
    val trackColor = 0xFF333333.toInt()

    val edgeContent = when {
        nowTime !in dawnToday..duskToday.minusNanos(1) -> {
            // Night states (1 or 5)
            CircularProgressIndicator.Builder().setStartAngle(-90f).setEndAngle(90f).setProgress(progress)
                .setCircularProgressIndicatorColors(
                    ProgressIndicatorColors(
                        ColorBuilders.argb(trackColor), ColorBuilders.argb(nightColor)
                    )
                ).build()
        }

        else -> {
            // Day transition states (2, 3, 4) - Optimized: Single Gray bar + remaining colors
            val dawnToDuskTotal = Duration.between(dawnTodayDT, duskTodayDT).toMillis().toFloat()
            val sunrisePoint =
                Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(today.date), sunriseToday)).toMillis()
                    .toFloat() / dawnToDuskTotal
            val sunsetPoint =
                Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(today.date), sunsetToday)).toMillis()
                    .toFloat() / dawnToDuskTotal

            val sunriseAngle = 180f * sunrisePoint
            val sunsetAngle = 180f * sunsetPoint
            val progressAngle = 180f * progress

            val arcBuilder = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(-90f))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)

            // 1. Consolidated Past (Gray)
            if (progressAngle > 0f) {
                arcBuilder.addContent(
                    LayoutElementBuilders.ArcLine.Builder()
                        .setLength(DimensionBuilders.degrees(progressAngle))
                        .setThickness(DimensionBuilders.dp(12f))
                        .setColor(ColorBuilders.argb(trackColor))
                        .build()
                )
            }

            // 2. Future portions
            when {
                progressAngle < sunriseAngle -> {
                    // Currently in Zone 1 (Dawn to Sunrise)
                    arcBuilder.addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(sunriseAngle - progressAngle))
                            .setThickness(DimensionBuilders.dp(12f))
                            .setColor(ColorBuilders.argb(dawnDuskColor))
                            .build()
                    )
                    arcBuilder.addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(sunsetAngle - sunriseAngle))
                            .setThickness(DimensionBuilders.dp(12f))
                            .setColor(ColorBuilders.argb(dayColor))
                            .build()
                    )
                    arcBuilder.addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(180f - sunsetAngle))
                            .setThickness(DimensionBuilders.dp(12f))
                            .setColor(ColorBuilders.argb(dawnDuskColor))
                            .build()
                    )
                }
                progressAngle < sunsetAngle -> {
                    // Currently in Zone 2 (Sunrise to Sunset)
                    arcBuilder.addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(sunsetAngle - progressAngle))
                            .setThickness(DimensionBuilders.dp(12f))
                            .setColor(ColorBuilders.argb(dayColor))
                            .build()
                    )
                    arcBuilder.addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(180f - sunsetAngle))
                            .setThickness(DimensionBuilders.dp(12f))
                            .setColor(ColorBuilders.argb(dawnDuskColor))
                            .build()
                    )
                }
                progressAngle < 180f -> {
                    // Currently in Zone 3 (Sunset to Dusk)
                    arcBuilder.addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(180f - progressAngle))
                            .setThickness(DimensionBuilders.dp(12f))
                            .setColor(ColorBuilders.argb(dawnDuskColor))
                            .build()
                    )
                }
            }

            arcBuilder.build()
        }
    }

    return EdgeContentLayout.Builder(deviceParameters).setResponsiveContentInsetEnabled(true).setContent(
        LayoutElementBuilders.Column.Builder().addContent(LayoutElementBuilders.Text.Builder().setText(row1Text).build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(time1).build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(row2Text).build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(time2).build()).build()
    ).setPrimaryLabelTextContent(LayoutElementBuilders.Text.Builder().setText(state.locationName).build())
        .setEdgeContent(edgeContent).build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
@Preview(device = WearDevices.SQUARE)
fun messagingTileLayoutPreview(context: Context): TilePreviewData {

    return TilePreviewData { request ->
        DawnDuskTileRenderer(context).renderTimeline(
            DawnDuskTileState(currentDates, "Mock Location"), request
        )
    }

}