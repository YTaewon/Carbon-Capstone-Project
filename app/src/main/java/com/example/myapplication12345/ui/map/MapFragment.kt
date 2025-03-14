package com.example.myapplication12345.ui.map

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.myapplication12345.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.advancedpolyline.MonochromaticPaintList
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MapFragment : Fragment() {

    companion object {
        const val TAG = "MapFragment"
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        fun createTestCsvFile(context: Context, date: Date) {
            val sensorDataDir = File(context.getExternalFilesDir(null), "SensorData")
            if (!sensorDataDir.exists()) sensorDataDir.mkdirs()

            val dateString = dateFormat.format(date)
            val file = File(sensorDataDir, "${dateString}_predictions.csv")

            if (!file.exists()) {
                try {
                    FileWriter(file).use { writer ->
                        writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n")

                        val modes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
                        val centerPoints = listOf(
                            Pair(35.177306, 128.567773), // WALK 시작점
                            Pair(35.182838, 128.564494), // BIKE 시작점
                            Pair(35.186558, 128.563180), // BUS 시작점
                            Pair(35.191691, 128.567251), // CAR 시작점
                            Pair(35.194362, 128.569659), // SUBWAY 시작점
                            Pair(35.198268, 128.570484)  // ETC 시작점
                        )
                        val endPoints = listOf(
                            Pair(35.182838, 128.564494), // WALK 끝점
                            Pair(35.186558, 128.563180), // BIKE 끝점
                            Pair(35.191691, 128.567251), // BUS 끝점
                            Pair(35.194362, 128.569659), // CAR 끝점
                            Pair(35.198268, 128.570484), // SUBWAY 끝점
                            Pair(35.202949, 128.572035)  // ETC 끝점
                        )

                        modes.forEachIndexed { index, mode ->
                            val startLat = centerPoints[index].first
                            val startLon = centerPoints[index].second
                            val endLat = endPoints[index].first
                            val endLon = endPoints[index].second
                            val timestamp = System.currentTimeMillis() + index * 1000L
                            val distance = 500.0 // 임의의 거리

                            writer.append(String.format(
                                "%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n",
                                timestamp, mode, distance, startLat, startLon, endLat, endLon
                            ))
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "테스트 CSV 생성 실패: ${e.message}", e)
                }
            }
        }
    }

    private lateinit var mapView: MapView
    private lateinit var textDistanceInfo: TextView
    private lateinit var dateText: TextView
    private lateinit var loadButton: ImageView
    private lateinit var selectTransportButton: ImageView
    private lateinit var toggleDistanceButton: ImageView
    private lateinit var calendarButton: ImageView
    private lateinit var testMapButton: Button

    private val transportModes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
    private val selectedModes = mutableSetOf<String>().apply { addAll(transportModes) }
    private var isDistanceInfoVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(requireContext(), requireActivity().getPreferences(Context.MODE_PRIVATE))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        mapView = view.findViewById(R.id.map_view)
        textDistanceInfo = view.findViewById(R.id.text_distance_info)
        dateText = view.findViewById(R.id.date_text)
        loadButton = view.findViewById(R.id.load_button)
        selectTransportButton = view.findViewById(R.id.select_transport_button)
        toggleDistanceButton = view.findViewById(R.id.toggle_distance_button)
        calendarButton = view.findViewById(R.id.calendar_button)
        testMapButton = view.findViewById(R.id.test_map)

        // MapView 초기화
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)
        mapView.setTilesScaledToDpi(true)

        // 인자로 날짜가 제공되지 않으면 오늘 날짜를 기본값으로 사용
        val todayDate = dateFormat.format(System.currentTimeMillis())
        val selectedDate = arguments?.getString("selectedDate") ?: todayDate
        updateDateText(selectedDate)
        loadAndDisplayPredictionData(selectedDate)
        updateTestMapButtonState(selectedDate) // 초기 버튼 상태 설정

        loadButton.setOnClickListener {
            val selectedDateStr = dateText.text.toString()
            val parsedDate = parseDateFromText(selectedDateStr)
            loadAndDisplayPredictionData(parsedDate)
            updateTestMapButtonState(parsedDate) // 버튼 상태 업데이트
        }

        selectTransportButton.setOnClickListener {
            showTransportSelectionDialog()
        }

        toggleDistanceButton.setOnClickListener {
            toggleDistanceInfoVisibility()
        }

        calendarButton.setOnClickListener {
            showDatePickerDialog()
        }

        testMapButton.setOnClickListener {
            val selectedDateStr = dateText.text.toString()
            val parsedDateStr = parseDateFromText(selectedDateStr)
            val selectedDate = dateFormat.parse(parsedDateStr)
            if (selectedDate != null) {
                createTestCsvFile(requireContext(), selectedDate)
                updateDateText(parsedDateStr)
                loadAndDisplayPredictionData(parsedDateStr)
                updateTestMapButtonState(parsedDateStr) // CSV 생성 후 버튼 상태 업데이트
                Toast.makeText(requireContext(), "$parsedDateStr 테스트 CSV 생성 완료", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "유효하지 않은 날짜입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            requireContext(), R.style.DatePickerTheme, { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = String.format("%04d%02d%02d", selectedYear, selectedMonth + 1, selectedDay)
                updateDateText(selectedDate)
                loadAndDisplayPredictionData(selectedDate)
                updateTestMapButtonState(selectedDate) // 날짜 선택 후 버튼 상태 업데이트
            }, year, month, day
        ).show()
    }

    private fun updateDateText(date: String) {
        val year = date.substring(0, 4)
        val month = date.substring(4, 6)
        val day = date.substring(6, 8)
        dateText.text = "${year}년 ${month}월 ${day}일"
    }

    private fun parseDateFromText(dateText: String): String {
        return try {
            val parts = dateText.split("년 ", "월 ", "일")
            val yearPart = parts[0]
            val monthPart = parts[1].padStart(2, '0')
            val dayPart = parts[2].padStart(2, '0')
            "$yearPart$monthPart$dayPart"
        } catch (e: Exception) {
            Log.e(TAG, "날짜 파싱 실패: $dateText", e)
            dateFormat.format(System.currentTimeMillis())
        }
    }

    private fun toggleDistanceInfoVisibility() {
        if (isDistanceInfoVisible) {
            textDistanceInfo.visibility = View.GONE
            toggleDistanceButton.setImageResource(R.drawable.ic_drop_down)
            isDistanceInfoVisible = false
        } else {
            textDistanceInfo.visibility = View.VISIBLE
            toggleDistanceButton.setImageResource(R.drawable.ic_drop_up)
            isDistanceInfoVisible = true
        }
    }

    private fun showTransportSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transport_selection, null)
        val checkBoxes = listOf(
            dialogView.findViewById<CheckBox>(R.id.checkbox_walk),
            dialogView.findViewById<CheckBox>(R.id.checkbox_bike),
            dialogView.findViewById<CheckBox>(R.id.checkbox_bus),
            dialogView.findViewById<CheckBox>(R.id.checkbox_car),
            dialogView.findViewById<CheckBox>(R.id.checkbox_subway),
            dialogView.findViewById<CheckBox>(R.id.checkbox_etc),
            dialogView.findViewById<CheckBox>(R.id.checkbox_all)
        )

        checkBoxes.forEachIndexed { index, checkBox ->
            if (index < transportModes.size) {
                checkBox.isChecked = selectedModes.contains(transportModes[index])
            }
        }
        checkBoxes.last().isChecked = selectedModes.size == transportModes.size

        checkBoxes.last().setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkBoxes.dropLast(1).forEach { it.isChecked = true }
            }
        }

        checkBoxes.dropLast(1).forEachIndexed { _, checkBox ->
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val allChecked = checkBoxes.dropLast(1).all { it.isChecked }
                if (!isChecked && checkBoxes.last().isChecked) {
                    checkBoxes.last().isChecked = false
                } else {
                    checkBoxes.last().isChecked = allChecked
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("이동수단 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                selectedModes.clear()
                checkBoxes.dropLast(1).forEachIndexed { index, checkBox ->
                    if (checkBox.isChecked) {
                        selectedModes.add(transportModes[index])
                    }
                }
                val parsedDate = parseDateFromText(dateText.text.toString())
                loadAndDisplayPredictionData(parsedDate)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun getSelectedTransportModes(): List<String> {
        return selectedModes.toList()
    }

    @SuppressLint("DefaultLocale")
    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        mapView.overlays.clear() // 기존 오버레이 제거
        val distanceInfo = StringBuilder("이동 거리 합계:\n")
        var firstPoint: GeoPoint? = null
        var hasData = false

        val selectedModes = getSelectedTransportModes()
        val validModes = setOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY")
        val transportModeNames = mapOf(
            "WALK" to "걷기", "BIKE" to "자전거", "BUS" to "버스",
            "CAR" to "자동차", "SUBWAY" to "지하철", "ETC" to "나머지"
        )

        val groupedData = predictionData
            .map { data ->
                val mode = data["transport_mode"] ?: "ETC"
                if (validModes.contains(mode)) data
                else data.toMutableMap().apply { this["transport_mode"] = "ETC" }
            }
            .filter { selectedModes.contains(it["transport_mode"]) }
            .groupBy { it["transport_mode"]!! }

        if (groupedData.isEmpty()) {
            distanceInfo.clear()
            distanceInfo.append("데이터 없음")
        } else {
            groupedData.forEach { (transportMode, dataList) ->
                var totalDistance = 0.0

                dataList.sortedBy { it["start_timestamp"]!!.toLong() }.forEach { data ->
                    val distance = data["distance_meters"]!!.toDouble()
                    val startLat = data["start_latitude"]?.toDoubleOrNull()
                    val startLon = data["start_longitude"]?.toDoubleOrNull()
                    val endLat = data["end_latitude"]?.toDoubleOrNull()
                    val endLon = data["end_longitude"]?.toDoubleOrNull()

                    if (startLat != null && startLon != null && endLat != null && endLon != null) {
                        val startPoint = GeoPoint(startLat, startLon)
                        val endPoint = GeoPoint(endLat, endLon)
                        val points = listOf(startPoint, endPoint)

                        totalDistance += distance

                        if (firstPoint == null) {
                            firstPoint = startPoint
                        }
                        hasData = true

                        val koreanTransportMode = transportModeNames[transportMode] ?: transportMode
                        val polyline = Polyline().apply {
                            setPoints(points)
                            setTitle("$koreanTransportMode - 거리: ${String.format("%.2f m", distance)}")
                            val borderPaint = Paint().apply {
                                color = Color.BLACK
                                strokeWidth = 10.0f
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                isAntiAlias = true
                            }
                            outlinePaintLists.add(MonochromaticPaintList(borderPaint))
                            val innerPaint = Paint().apply {
                                color = getTransportColor(transportMode)
                                strokeWidth = 5.0f
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                isAntiAlias = true
                            }
                            outlinePaintLists.add(MonochromaticPaintList(innerPaint))
                        }
                        mapView.overlays.add(polyline)
                    }
                }

                if (totalDistance > 0) {
                    val koreanTransportMode = transportModeNames[transportMode] ?: transportMode
                    distanceInfo.append(
                        String.format("%s: %.2f m\n", koreanTransportMode, totalDistance)
                    )
                }
            }

            if (!hasData) {
                distanceInfo.clear()
                distanceInfo.append("데이터 없음")
            }
        }

        firstPoint?.let {
            mapView.controller.setCenter(it)
        }

        mapView.invalidate() // 지도 갱신
        textDistanceInfo.text = distanceInfo.toString()
    }

    private fun getTransportColor(transportMode: String): Int {
        return when (transportMode) {
            "WALK" -> Color.GREEN
            "BIKE" -> Color.BLUE
            "BUS" -> Color.YELLOW
            "CAR" -> Color.RED
            "SUBWAY" -> Color.MAGENTA
            "ETC" -> Color.GRAY
            else -> Color.BLACK
        }
    }

    private fun loadAndDisplayPredictionData(date: String) {
        mapView.overlays.clear() // 데이터 로드 전에 오버레이 초기화
        mapView.invalidate() // 지도 갱신

        val fileName = "${date}_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")

        if (!file.exists()) {
            Log.e(TAG, "예측 데이터 CSV 파일이 존재하지 않음: $fileName")
            textDistanceInfo.text = "데이터 없음: $date"
            return
        }

        val predictionData = mutableListOf<Map<String, String>>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
                val headerLine = br.readLine() ?: run {
                    Log.e(TAG, "CSV 헤더가 없음: $fileName")
                    textDistanceInfo.text = "CSV 헤더 없음: $fileName"
                    return
                }
                val headers = headerLine.split(",")

                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val values = line!!.split(",")
                    if (values.size != headers.size) {
                        Log.w(TAG, "CSV 데이터 불일치: $line")
                        continue
                    }
                    val data = mutableMapOf<String, String>()
                    headers.forEachIndexed { index, header ->
                        data[header] = values[index]
                    }
                    predictionData.add(data)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "CSV 로드 실패: ${e.message}", e)
            textDistanceInfo.text = "CSV 로드 실패: ${e.message}"
            return
        }

        displayPredictionOnMap(predictionData)
    }

    private fun isCsvFileExists(date: String): Boolean {
        val fileName = "${date}_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")
        return file.exists()
    }

    private fun updateTestMapButtonState(date: String) {
        testMapButton.isEnabled = !isCsvFileExists(date)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}