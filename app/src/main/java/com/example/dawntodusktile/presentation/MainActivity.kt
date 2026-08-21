package com.example.dawntodusktile.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.dawntodusktile.presentation.DawnDuskRepo.Companion.currentDates
import com.example.dawntodusktile.presentation.theme.DawnToDuskTileTheme
import com.example.dawntodusktile.presentation.tile.DawnDuskTileState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                val backgroundGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if ((fineGranted || coarseGranted) && !backgroundGranted) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }

            val viewModel: DawnDuskViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            DawnToDuskTileTheme {
                Scaffold {
                    DawnDuskScreen(uiState)
                }
            }
        }
    }
}

@Composable
fun DawnDuskScreen(state: DawnDuskTileState?) {
    if (state == null || (state.dawnDuskDates.size < 3)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (state?.locationName?.isEmpty() == true) "Loading..." else "Fetching data...",
                textAlign = TextAlign.Center
            )
        }
        return
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

    val nightColor = Color(0xFF003366)
    val dawnDuskColor = Color(0xFFFF9800)
    val dayColor = Color(0xFFFFEB3B)
    val trackColor = Color(0xFF333333)

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp), contentAlignment = Alignment.Center) {
        // Edge Content (Progress)
        val isDay = nowTime in dawnToday..duskToday.minusNanos(1)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)

            // 1. Draw Background Track (full 180 degrees)
            drawArc(
                color = trackColor, startAngle = 180f, sweepAngle = 180f, useCenter = false, style = stroke
            )

            val progressAngle = 180f * progress

            if (!isDay) {
                // Night states (1 or 5) - Blue progress arc for the FUTURE (matching day behavior)
                if (progressAngle < 180f) {
                    drawArc(
                        color = nightColor,
                        startAngle = 180f + progressAngle,
                        sweepAngle = 180f - progressAngle,
                        useCenter = false,
                        style = stroke
                    )
                }
            } else {
                // Day transition states (2, 3, 4) - Segmented Future
                val dawnToDuskTotal = Duration.between(dawnTodayDT, duskTodayDT).toMillis().toFloat()
                val sunrisePoint =
                    Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(today.date), sunriseToday)).toMillis()
                        .toFloat() / dawnToDuskTotal
                val sunsetPoint =
                    Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(today.date), sunsetToday)).toMillis()
                        .toFloat() / dawnToDuskTotal

                val sunriseAngle = 180f * sunrisePoint
                val sunsetAngle = 180f * sunsetPoint

                // We don't need to draw the "Past" (Gray) because the Track is already Gray.
                // We only draw the "Future" colored segments.

                when {
                    progressAngle < sunriseAngle -> {
                        // Currently in Zone 1 (Dawn to Sunrise)
                        drawArc(
                            color = dawnDuskColor,
                            startAngle = 180f + progressAngle,
                            sweepAngle = sunriseAngle - progressAngle,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = dayColor,
                            startAngle = 180f + sunriseAngle,
                            sweepAngle = sunsetAngle - sunriseAngle,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = dawnDuskColor,
                            startAngle = 180f + sunsetAngle,
                            sweepAngle = 180f - sunsetAngle,
                            useCenter = false,
                            style = stroke
                        )
                    }

                    progressAngle < sunsetAngle -> {
                        // Currently in Zone 2 (Sunrise to Sunset)
                        drawArc(
                            color = dayColor,
                            startAngle = 180f + progressAngle,
                            sweepAngle = sunsetAngle - progressAngle,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = dawnDuskColor,
                            startAngle = 180f + sunsetAngle,
                            sweepAngle = 180f - sunsetAngle,
                            useCenter = false,
                            style = stroke
                        )
                    }

                    progressAngle < 180f -> {
                        // Currently in Zone 3 (Sunset to Dusk)
                        drawArc(
                            color = dawnDuskColor,
                            startAngle = 180f + progressAngle,
                            sweepAngle = 180f - progressAngle,
                            useCenter = false,
                            style = stroke
                        )
                    }
                }
            }
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = state.locationName,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.secondary,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = row1Text, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
                Text(text = time1, style = MaterialTheme.typography.caption2)

                Text(
                    text = row2Text,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(text = time2, style = MaterialTheme.typography.caption2)
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    DawnToDuskTileTheme {
        DawnDuskScreen(
            DawnDuskTileState(
                dawnDuskDates = currentDates, locationName = "Mock Location"
            )
        )
    }
}
