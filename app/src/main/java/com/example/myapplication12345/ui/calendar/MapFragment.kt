package com.example.myapplication12345.ui.calendar

import android.app.AlertDialog
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
import com.example.myapplication12345.ui.calendar.MapFragment.Companion.dateFormat
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
                        writer.append("transport_mode,distance_meters,start_timestamp,latitude,longitude\n")

                        // 중심점과 반지름 설정
                        val modes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
                        val centerPoints = listOf(
                            Pair(37.5665, 126.9780), // WALK
                            Pair(37.5700, 126.9750), // BIKE
                            Pair(37.5750, 126.9700), // BUS
                            Pair(37.5800, 126.9650), // CAR
                            Pair(37.5850, 126.9600), // SUBWAY
                            Pair(37.5900, 126.9550)  // ETC
                        )
                        val radius = 0.005 // 반지름 (위도/경도 단위, 약 500m)
                        val innerRadius = radius / 2

                        modes.forEachIndexed { index, mode ->
                            val centerLat = centerPoints[index].first
                            val centerLon = centerPoints[index].second
                            val timestampBase = System.currentTimeMillis() + index * 1000L

                            // 별 모양의 10개 점 (5개 외곽 + 5개 내측)
                            for (i in 0 until 10) {
                                val isOuter = i % 2 == 0 // 짝수 인덱스는 외곽, 홀수는 내측
                                val angle = Math.toRadians((i * 36.0)) // 360° / 10 = 36° 간격
                                val currentRadius = if (isOuter) radius else innerRadius
                                val lat = centerLat + currentRadius * kotlin.math.cos(angle)
                                val lon = centerLon + currentRadius * kotlin.math.sin(angle)
                                val distance = if (isOuter) 200.0 else 100.0 // 외곽과 내측 거리 다르게 설정
                                val timestamp = timestampBase + i * 500L

                                writer.append("$mode,$distance,$timestamp,$lat,$lon\n")
                            }
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
    private lateinit var loadButton: Button
    private lateinit var selectTransportButton: Button

    private val transportModes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
    private val selectedModes = mutableSetOf<String>().apply { addAll(transportModes) } // 기본적으로 모두 선택

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

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        val selectedDate = arguments?.getString("selectedDate") ?: dateFormat.format(System.currentTimeMillis())
        dateText.text = selectedDate
        loadAndDisplayPredictionData(selectedDate)

        loadButton.setOnClickListener {
            val selectedDate = dateText.text.toString()
            loadAndDisplayPredictionData(selectedDate)
        }

        selectTransportButton.setOnClickListener {
            showTransportSelectionDialog()
        }

        return view
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

        // 초기 체크 상태 설정
        checkBoxes.forEachIndexed { index, checkBox ->
            if (index < transportModes.size) {
                checkBox.isChecked = selectedModes.contains(transportModes[index])
            }
        }
        checkBoxes.last().isChecked = selectedModes.size == transportModes.size

        // "All" 체크박스 동작
        checkBoxes.last().setOnCheckedChangeListener { _, isChecked ->
            checkBoxes.dropLast(1).forEach { it.isChecked = isChecked }
        }

        // 개별 체크박스 동작
        checkBoxes.dropLast(1).forEachIndexed { index, checkBox ->
            checkBox.setOnCheckedChangeListener { _, _ ->
                val allChecked = checkBoxes.dropLast(1).all { it.isChecked }
                checkBoxes.last().isChecked = allChecked
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
                val selectedDate = dateText.text.toString()
                loadAndDisplayPredictionData(selectedDate)
            }
            .setNegativeButton("취소", null)
            .show()
    }



    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        mapView.overlays.clear()
        val distanceInfo = StringBuilder("이동 거리 합계:\n")
        var firstPoint: GeoPoint? = null

        val selectedModes = getSelectedTransportModes()

        // 이동수단별로 데이터를 그룹화
        val groupedData = predictionData
            .filter { selectedModes.contains(it["transport_mode"]) }
            .groupBy { it["transport_mode"]!! }

        groupedData.forEach { (transportMode, dataList) ->
            val points = mutableListOf<GeoPoint>()
            var totalDistance = 0.0

            dataList.sortedBy { it["start_timestamp"]!!.toLong() }.forEach { data ->
                val distance = data["distance_meters"]!!.toDouble()
                val latitude = data["latitude"]?.toDoubleOrNull()
                val longitude = data["longitude"]?.toDoubleOrNull()

                if (latitude != null && longitude != null) {
                    val geoPoint = GeoPoint(latitude, longitude)
                    points.add(geoPoint)
                    totalDistance += distance

                    if (firstPoint == null) {
                        firstPoint = geoPoint
                    }
                }
            }

            // 이동수단별 총 거리 추가
            distanceInfo.append(
                String.format(
                    "%s: %.2f m\n",
                    transportMode, totalDistance
                )
            )

            // Polyline 생성 및 추가
            if (points.isNotEmpty()) {
                val polyline = Polyline().apply {
                    setPoints(points)
                    setTitle("$transportMode - 총 거리: ${String.format("%.2f m", totalDistance)}")
                    val paint = Paint().apply {
                        color = getTransportColor(transportMode)
                        strokeWidth = 5.0f
                        style = Paint.Style.STROKE
                    }
                    outlinePaintLists.add(MonochromaticPaintList(paint))
                }
                mapView.overlays.add(polyline)
            }
        }

        firstPoint?.let {
            mapView.controller.setCenter(it)
        }

        mapView.invalidate()
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

    private fun getSelectedTransportModes(): List<String> {
        return selectedModes.toList()
    }

    private fun loadAndDisplayPredictionData(date: String) {
        val fileName = "${date}_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")

        if (!file.exists()) {
            Log.e(TAG, "예측 데이터 CSV 파일이 존재하지 않음: $fileName")
            textDistanceInfo.text = "데이터 없음: $fileName"
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


}