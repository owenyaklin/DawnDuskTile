package com.example.dawntodusktile.presentation

data class DateDawnDusk(
    val date: String, val sunrise: String, val sunset: String, val dawn: String, val dusk: String
) {
    fun toPreferencesString(): String = listOf(date, sunrise, sunset, dawn, dusk).joinToString(",")

    companion object {
        fun String.toDateDawnDusk(): DateDawnDusk {
            val (date, sunrise, sunset, dawn, dusk) = split(",")

            return DateDawnDusk(date, sunrise, sunset, dawn, dusk)
        }
    }
}
