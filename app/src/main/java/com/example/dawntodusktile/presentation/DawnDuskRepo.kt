package com.example.dawntodusktile.presentation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dawntodusktile.presentation.DateDawnDusk.Companion.toDateDawnDusk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dateDawnDusk")

class DawnDuskRepo(private val context: Context) {
    private val COUNT_KEY = intPreferencesKey("date.count")
    private val LOCATION_NAME_KEY = stringPreferencesKey("location_name")
    private val LATITUDE_KEY = doublePreferencesKey("latitude")
    private val LONGITUDE_KEY = doublePreferencesKey("longitude")

    fun getInitialValues(): Flow<DawnDuskData> = context.dataStore.data.map { preferences ->
        val count = preferences[COUNT_KEY] ?: 0
        val dates = (0 until count).mapNotNull {
            preferences[stringPreferencesKey("date.$it")]?.toDateDawnDusk()
        }
        val locationName = preferences[LOCATION_NAME_KEY] ?: ""
        val latitude = preferences[LATITUDE_KEY] ?: 0.0
        val longitude = preferences[LONGITUDE_KEY] ?: 0.0
        DawnDuskData(dates, locationName, latitude, longitude)
    }

    suspend fun updateDateDawnDusk(
        dawnDuskDates: List<DateDawnDusk>,
        locationName: String,
        latitude: Double,
        longitude: Double
    ) {
        context.dataStore.edit {
            it.clear()
            dawnDuskDates.forEachIndexed { index, dawnDusk ->
                it[stringPreferencesKey("date.$index")] = dawnDusk.toPreferencesString()
            }
            it[COUNT_KEY] = dawnDuskDates.size
            it[LOCATION_NAME_KEY] = locationName
            it[LATITUDE_KEY] = latitude
            it[LONGITUDE_KEY] = longitude
        }
    }

    companion object {
        val currentDates: List<DateDawnDusk>
            get() = listOf(
                DateDawnDusk(
                    date = LocalDate.now().toString(),
                    sunrise = "05:59:15",
                    sunset = "19:56:30",
                    dawn = "05:27:54",
                    dusk = "20:27:51"
                ),
                DateDawnDusk(
                    date = LocalDate.now().plusDays(1).toString(),
                    sunrise = "06:00:26",
                    sunset = "19:54:54",
                    dawn = "05:29:11",
                    dusk = "20:26:09"
                )
            )
    }
}