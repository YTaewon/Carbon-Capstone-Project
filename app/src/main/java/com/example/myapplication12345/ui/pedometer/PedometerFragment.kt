package com.example.myapplication12345.ui.pedometer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.DecimalFormat

class PedometerFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var steps by mutableStateOf(0)
    private var initialSteps = -1
    private var totalGoal = 10000
    private var startTime by mutableStateOf(0L)
    private val caloriesPerStep = 0.03
    private val distancePerStep = 0.64

    companion object {
        private const val REQUEST_ACTIVITY_RECOGNITION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val prefs = requireActivity().getSharedPreferences("stepper", Context.MODE_PRIVATE)
        initialSteps = prefs.getInt("initial_steps", -1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_ACTIVITY_RECOGNITION)
        } else {
            startStepCounter()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    StepCounterScreen(steps = steps, goal = totalGoal)
                }
            }
        }
    }

    private fun startStepCounter() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            if (startTime == 0L) startTime = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        startStepCounter()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val totalSteps = it.values[0].toInt()
            if (initialSteps == -1) {
                initialSteps = totalSteps
                requireActivity().getSharedPreferences("stepper", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("initial_steps", initialSteps)
                    .apply()
            }
            steps = totalSteps - initialSteps
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startStepCounter()
        }
    }

    @Composable
    fun StepCounterScreen(steps: Int, goal: Int) {
        var mainStat by remember { mutableStateOf("steps") }
        var clickCount by remember { mutableStateOf(0) } // 클릭 횟수 추적

        val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1.5f)
        val progressColor = Color(0xFF4CAF50)
        val trackColor = Color(0xFFE0E0E0)

        val currentTime = System.currentTimeMillis()
        val activityDurationSeconds = (currentTime - startTime) / 1000
        val activityDurationMinutes = (activityDurationSeconds / 60).toInt().coerceAtLeast(0)
        val calories = (steps * caloriesPerStep).toFloat()
        val distanceMeters = steps * distancePerStep
        val distanceKm = distanceMeters / 1000.0
        val decimalFormat = DecimalFormat("#.##")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier.size(200.dp),
                color = progressColor,
                trackColor = trackColor,
                strokeWidth = 12.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            if (progress > 1f) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = progressColor,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 메인 정보 표시 및 클릭 처리
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    clickCount++
                    if (clickCount >= 2) { // 두 번 클릭 시 steps로 복원
                        mainStat = "steps"
                        clickCount = 0 // 카운트 리셋
                    }
                }
            ) {
                when (mainStat) {
                    "steps" -> {
                        Text(
                            text = "$steps",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        Text(
                            text = "/$goal 걸음",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        )
                    }
                    "time" -> {
                        Text(
                            text = "$activityDurationMinutes",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        Text(
                            text = "분",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        )
                    }
                    "calories" -> {
                        Text(
                            text = decimalFormat.format(calories),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        Text(
                            text = "kcal",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        )
                    }
                    "distance" -> {
                        Text(
                            text = decimalFormat.format(distanceKm),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                mainStat = "time"
                                clickCount = 0 // 하위 정보 클릭 시 카운트 리셋
                            }
                        )
                ) {
                    Text(
                        text = if (mainStat == "time") "$steps 걸음" else "$activityDurationMinutes / 60 분",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = if (mainStat == "time") "걸음 수" else "활동 시간",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                mainStat = "calories"
                                clickCount = 0 // 하위 정보 클릭 시 카운트 리셋
                            }
                        )
                ) {
                    Text(
                        text = if (mainStat == "calories") "$steps 걸음" else "${decimalFormat.format(calories)} kcal",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = if (mainStat == "calories") "걸음 수" else "칼로리",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                mainStat = "distance"
                                clickCount = 0 // 하위 정보 클릭 시 카운트 리셋
                            }
                        )
                ) {
                    Text(
                        text = if (mainStat == "distance") "$steps 걸음" else "${decimalFormat.format(distanceKm)} km",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = if (mainStat == "distance") "걸음 수" else "거리",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                    )
                }
            }
        }
    }
}