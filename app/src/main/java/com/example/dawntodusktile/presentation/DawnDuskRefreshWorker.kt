package com.example.dawntodusktile.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dawntodusktile.presentation.tile.DawnDuskTileService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class DawnDuskRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo = DawnDuskRepo(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background refresh work...")
        
        val location = fetchLocation()
        if (location != null) {
            refreshData(location)
            return Result.success()
        } else {
            Log.w(TAG, "Refresh failed: could not obtain location")
            return Result.retry()
        }
    }

    private suspend fun fetchLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        // 1. Try to get the Last Known Location first (Low Power)
        val lastLocation = try {
            suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }
        } catch (_: Exception) {
            null
        }

        // If lastLocation is "fresh enough" (e.g., within 30 minutes), use it to save battery
        if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 30 * 60 * 1000) {
            Log.d(TAG, "Using fresh lastLocation: ${lastLocation.latitude}")
            return lastLocation
        }

        // 2. If no fresh lastLocation, request a "High Accuracy" current location
        // Note: High accuracy is more reliable on emulators and preferred for background workers
        Log.d(TAG, "No fresh lastLocation, requesting high accuracy current location...")
        return withTimeoutOrNull(25000.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val cancellationTokenSource = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    Log.d(TAG, "Location fetched: $location")
                    if (continuation.isActive) continuation.resume(location)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get location", exception)
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
            }
        }
    }

    private suspend fun refreshData(location: Location) {
        Log.d(TAG, "Refreshing data for location: ${location.latitude}, ${location.longitude}")
        val locationName = fetchLocationName(location) ?: "Unknown Location"
        val dawnDuskDates = fetchDawnDuskDates(location) ?: DawnDuskRepo.currentDates
        
        repo.updateDateDawnDusk(
            dawnDuskDates,
            locationName,
            location.latitude,
            location.longitude,
            System.currentTimeMillis()
        )
        
        // Request a Tile update now that data is fresh
        TileService.getUpdater(applicationContext)
            .requestUpdate(DawnDuskTileService::class.java)
        Log.d(TAG, "Data updated and Tile refresh requested")
    }

    private suspend fun fetchLocationName(location: Location): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://geocoding.geo.census.gov/geocoder/geographies/coordinates?x=${location.longitude}&y=${location.latitude}&format=json&benchmark=2020&vintage=2020&layers=County+Subdivisions"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val result = json.getJSONObject("result")
                val geographies = result.getJSONObject("geographies")
                val countySubdivisions = geographies.getJSONArray("County Subdivisions")
                if (countySubdivisions.length() > 0) {
                    return@withContext countySubdivisions.getJSONObject(0).getString("BASENAME")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch location name", e)
        }
        null
    }

    private suspend fun fetchDawnDuskDates(location: Location): List<DateDawnDusk>? = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val tomorrow = today.plusDays(1)
            
            val urlString = "https://api.sunrisesunset.io/json?lat=${location.latitude}&lng=${location.longitude}&date_start=$yesterday&date_end=$tomorrow&time_format=24"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val results = json.getJSONArray("results")
                val dawnDuskList = mutableListOf<DateDawnDusk>()
                
                for (i in 0 until results.length()) {
                    val dayData = results.getJSONObject(i)
                    dawnDuskList.add(
                        DateDawnDusk(
                            date = dayData.getString("date"),
                            sunrise = dayData.getString("sunrise"),
                            sunset = dayData.getString("sunset"),
                            dawn = dayData.getString("dawn"),
                            dusk = dayData.getString("dusk")
                        )
                    )
                }
                return@withContext dawnDuskList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch dawn/dusk data", e)
        }
        null
    }

    companion object {
        private const val TAG = "DawnDuskRefreshWorker"
    }
}
