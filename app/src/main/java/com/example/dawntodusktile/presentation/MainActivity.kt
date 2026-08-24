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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import java.time.LocalDateTime
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
    // Add a periodic tick to refresh the countdowns every minute
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val millisToNextMinute = 60000 - (System.currentTimeMillis() % 60000)
            delay(millisToNextMinute.milliseconds) // Sync to minute boundary
            currentTime = LocalDateTime.now()
        }
    }

    if (state == null || (state.dawnDuskDates.size < 3)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (state?.locationName?.isEmpty() == true) "Loading..." else "Fetching data...",
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val solarState = SolarDataUtils.calculateSolarState(currentTime, state.dawnDuskDates) ?: return

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

    val nightColor = Color(0xFF003366)
    val dawnDuskColor = Color(0xFFFF9800)
    val dayColor = Color(0xFFFFEB3B)
    val trackColor = Color(0xFF333333)

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp), contentAlignment = Alignment.Center) {
        // Edge Content (Progress)
        
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
                val dawnToDuskTotal = java.time.Duration.between(dawnTodayDT, duskTodayDT).toMillis().toFloat()
                val sunrisePoint =
                    java.time.Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(todayDate), sunriseToday)).toMillis()
                        .toFloat() / dawnToDuskTotal
                val sunsetPoint =
                    java.time.Duration.between(dawnTodayDT, LocalDateTime.of(LocalDate.parse(todayDate), sunsetToday)).toMillis()
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
