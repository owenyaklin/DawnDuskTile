package com.example.dawntodusktile.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.dawntodusktile.presentation.tile.DawnDuskTileState
import com.google.android.gms.location.FusedLocationProviderClient
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

object SolarDataUtils {
    const val STALE_DISTANCE_METERS = 5000.0
    const val STALE_TIME_MILLIS = 4 * 60 * 60 * 1000L

    fun calculateProgress(start: LocalDateTime, end: LocalDateTime, current: LocalDateTime): Float {
        val total = Duration.between(start, end).toMillis()
        if (total <= 0) return 0f
        val elapsed = Duration.between(start, current).toMillis()
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

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

    data class SolarStateResult(
        val row1Text: String,
        val time1: String,
        val row2Text: String,
        val time2: String,
        val progress: Float,
        val isDay: Boolean,
        val dawnTodayDT: LocalDateTime,
        val duskTodayDT: LocalDateTime,
        val sunriseToday: LocalTime,
        val sunsetToday: LocalTime,
        val todayDate: String,
    )

    fun calculateSolarState(now: LocalDateTime, dawnDuskDates: List<DateDawnDusk>): SolarStateResult? {
        if (dawnDuskDates.size < 3) return null

        val yesterday = dawnDuskDates[0]
        val today = dawnDuskDates[1]
        val tomorrow = dawnDuskDates[2]
        val nowTime = now.toLocalTime()

        val dawnToday = LocalTime.parse(today.dawn)
        val sunriseToday = LocalTime.parse(today.sunrise)
        val sunsetToday = LocalTime.parse(today.sunset)
        val duskToday = LocalTime.parse(today.dusk)

        val duskYesterdayDT = LocalDateTime.of(LocalDate.parse(yesterday.date), LocalTime.parse(yesterday.dusk))
        val dawnTodayDT = LocalDateTime.of(LocalDate.parse(today.date), LocalTime.parse(today.dawn))
        val duskTodayDT = LocalDateTime.of(LocalDate.parse(today.date), LocalTime.parse(today.dusk))
        val dawnTomorrowDT = LocalDateTime.of(LocalDate.parse(tomorrow.date), LocalTime.parse(tomorrow.dawn))

        val (rowParams, progress) = when {
            nowTime < dawnToday -> {
                (("Dawn" to today) to ("Sunrise" to today)) to calculateProgress(duskYesterdayDT, dawnTodayDT, now)
            }
            nowTime < sunriseToday -> {
                (("Dusk" to today) to ("Sunrise" to today)) to calculateProgress(dawnTodayDT, duskTodayDT, now)
            }
            nowTime < sunsetToday -> {
                (("Dusk" to today) to ("Sunset" to today)) to calculateProgress(dawnTodayDT, duskTodayDT, now)
            }
            nowTime < duskToday -> {
                (("Dusk" to today) to ("Sunrise" to tomorrow)) to calculateProgress(dawnTodayDT, duskTodayDT, now)
            }
            else -> {
                (("Dawn" to tomorrow) to ("Sunrise" to tomorrow)) to calculateProgress(duskTodayDT, dawnTomorrowDT, now)
            }
        }
        
        val row1Data = rowParams.first
        val row2Data = rowParams.second
        val (row1Text, time1) = formatEvent(row1Data.first, row1Data.second, now)
        val (row2Text, time2) = formatEvent(row2Data.first, row2Data.second, now)
        
        val isDay = nowTime in dawnToday..duskToday.minusNanos(1)

        return SolarStateResult(
            row1Text = row1Text,
            time1 = time1,
            row2Text = row2Text,
            time2 = time2,
            progress = progress,
            isDay = isDay,
            dawnTodayDT = dawnTodayDT,
            duskTodayDT = duskTodayDT,
            sunriseToday = sunriseToday,
            sunsetToday = sunsetToday,
            todayDate = today.date
        )
    }

    suspend fun isStale(
        state: DawnDuskTileState,
        context: Context,
        fusedLocationClient: FusedLocationProviderClient
    ): Boolean {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        val isDateStale = state.dawnDuskDates.size < 3 || state.dawnDuskDates[1].date != today
        val isTimeStale = (now - state.lastUpdatedMillis) > STALE_TIME_MILLIS
        val isDistanceStale = state.lastUpdatedMillis > 0 && checkDistanceStale(context, fusedLocationClient, state.latitude, state.longitude)

        return isDateStale || isTimeStale || isDistanceStale
    }

    fun forceRefresh(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<DawnDuskRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "DawnDuskRefresh", ExistingWorkPolicy.KEEP, workRequest,
        )
    }

    private suspend fun checkDistanceStale(
        context: Context,
        fusedLocationClient: FusedLocationProviderClient,
        storedLat: Double,
        storedLng: Double
    ): Boolean {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val lastLocation = suspendCancellableCoroutine { continuation ->
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
                distanceInMeters > STALE_DISTANCE_METERS
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
