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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.myapplication12345.R
import timber.log.Timber
import java.text.DecimalFormat

class PedometerFragment : DialogFragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var initialSteps = -1
    private var totalGoal = 10000
    private var startTime by mutableStateOf(0L)
    private val caloriesPerStep = 0.03
    private val distancePerStep = 0.64

    private val viewModel: PedometerViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startStepCounter()
        } else {
            Timber.tag("PedometerFragment").w("Activity recognition permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            Timber.tag("PedometerFragment").e("Step counter sensor not available")
            return
        }

        val prefs = requireActivity().getSharedPreferences("stepper", Context.MODE_PRIVATE)
        initialSteps = prefs.getInt("initial_steps", -1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                startStepCounter()
            }
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
                    StepCounterScreen(steps = viewModel.steps.value ?: 0, goal = totalGoal)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = (resources.displayMetrics.widthPixels * 0.8).toInt()
            params.height = (resources.displayMetrics.heightPixels * 0.5).toInt()
            window.attributes = params
            window.setBackgroundDrawableResource(R.drawable.background_white)
        }
    }

    private fun startStepCounter() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            if (startTime == 0L) startTime = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            startStepCounter()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val totalSteps = it.values[0].toInt()
            val prefs = requireActivity().getSharedPreferences("stepper", Context.MODE_PRIVATE)
            if (initialSteps == -1 || totalSteps < initialSteps) {
                initialSteps = totalSteps
                prefs.edit().putInt("initial_steps", initialSteps).apply()
            }
            val steps = totalSteps - initialSteps
            viewModel.updateSteps(steps)
            Timber.tag("PedometerFragment").d("Steps updated: $steps")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Composable
    fun StepCounterScreen(steps: Int, goal: Int) {
        var mainStat by remember { mutableStateOf("steps") }
        var subTimeStat by remember { mutableStateOf("time") }
        var subCaloriesStat by remember { mutableStateOf("calories") }
        var subDistanceStat by remember { mutableStateOf("distance") }

        val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1.5f)
        val progressColor = Color(0xFF4CAF50)
        val trackColor = Color(0xFFE0E0E0)

        val currentTime = System.currentTimeMillis()
        val activityDurationSeconds = (currentTime - startTime) / 1000
        val activityDurationMinutes = (activityDurationSeconds / 60).toInt().coerceAtLeast(0)
        val calories = (steps * caloriesPerStep).toFloat()
        val distanceMeters = steps * distancePerStep
        val decimalFormat = DecimalFormat("#.##")

        // 거리 포맷팅 (1000m 이상이면 km으로 변환)
        val distanceText = if (distanceMeters >= 1000) {
            "${decimalFormat.format(distanceMeters / 1000.0)}km"
        } else {
            "${distanceMeters.toInt()}m"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier.size(150.dp),
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
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
                            text = distanceText, // 포맷팅된 거리 사용
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        Text(
                            text = if (distanceMeters >= 1000) "km" else "m",
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
                                val temp = mainStat
                                mainStat = subTimeStat
                                subTimeStat = temp
                            }
                        )
                ) {
                    when (subTimeStat) {
                        "steps" -> {
                            Text(
                                text = "$steps 걸음",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "걸음 수",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "time" -> {
                            Text(
                                text = "$activityDurationMinutes / 60 분",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "활동 시간",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "calories" -> {
                            Text(
                                text = "${decimalFormat.format(calories)} kcal",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "칼로리",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "distance" -> {
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "거리",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val temp = mainStat
                                mainStat = subCaloriesStat
                                subCaloriesStat = temp
                            }
                        )
                ) {
                    when (subCaloriesStat) {
                        "steps" -> {
                            Text(
                                text = "$steps 걸음",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "걸음 수",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "time" -> {
                            Text(
                                text = "$activityDurationMinutes / 60 분",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "활동 시간",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "calories" -> {
                            Text(
                                text = "${decimalFormat.format(calories)} kcal",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "칼로리",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "distance" -> {
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "거리",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val temp = mainStat
                                mainStat = subDistanceStat
                                subDistanceStat = temp
                            }
                        )
                ) {
                    when (subDistanceStat) {
                        "steps" -> {
                            Text(
                                text = "$steps 걸음",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "걸음 수",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "time" -> {
                            Text(
                                text = "$activityDurationMinutes / 60 분",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "활동 시간",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "calories" -> {
                            Text(
                                text = "${decimalFormat.format(calories)} kcal",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "칼로리",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                        "distance" -> {
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "거리",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                    }
                }
            }
        }
    }
}