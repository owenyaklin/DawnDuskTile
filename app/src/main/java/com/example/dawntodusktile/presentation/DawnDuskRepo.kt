package com.example.dawntodusktile.presentation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dawntodusktile.presentation.DateDawnDusk.Companion.toDateDawnDusk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dateDawnDusk")

class DawnDuskRepo(private val context: Context) {
    fun getInitialValues(): Flow<List<DateDawnDusk>> = context.dataStore.data.map { preferences ->
        val count = preferences[intPreferencesKey("date.count")] ?: 0
        (0 until count).mapNotNull {
            preferences[stringPreferencesKey("date.$it")]?.toDateDawnDusk()
        }
    }

    suspend fun updateDateDawnDusk(dawnDuskDates: List<DateDawnDusk>) {
        context.dataStore.edit {
            it.clear()
            dawnDuskDates.forEachIndexed { index, dawnDusk ->
                it[stringPreferencesKey("date.$index")] = dawnDusk.toPreferencesString()
            }
            it[intPreferencesKey("date.count")] = dawnDuskDates.size
        }
    }

    companion object {
        val currentDates = listOf(
            DateDawnDusk(
                date = "2025-04-30",
                sunrise = "05:45:20",
                sunset = "19:56:15",
                dawn = "05:13:25",
                dusk = "20:28:10"
            ),
            DateDawnDusk(
                date = "2025-05-01",
                sunrise = "05:43:53",
                sunset = "19:57:29",
                dawn = "05:11:51",
                dusk = "20:29:31"
            )
        )
    }
}