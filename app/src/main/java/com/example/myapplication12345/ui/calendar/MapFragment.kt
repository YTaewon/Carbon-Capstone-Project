package com.example.myapplication12345.ui.calendar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication12345.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MapFragment : Fragment() {

    private companion object {
        const val TAG = "MapFragment"
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    }

    private lateinit var mapView: MapView
    private lateinit var textDistanceInfo: TextView
    private lateinit var dateSpinner: Spinner
    private lateinit var loadButton: Button

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
        dateSpinner = view.findViewById(R.id.date_spinner)
        loadButton = view.findViewById(R.id.load_button)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)
        mapView.getController().setZoom(15.0)

        val availableDates = getAvailableDates()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableDates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateSpinner.adapter = adapter

        val selectedDate = arguments?.getString("selectedDate")
        if (selectedDate != null && availableDates.contains(selectedDate)) {
            dateSpinner.setSelection(availableDates.indexOf(selectedDate))
            loadAndDisplayPredictionData(selectedDate)
        } else {
            val currentDate = dateFormat.format(System.currentTimeMillis())
            val defaultPosition = availableDates.indexOf(currentDate)
            if (defaultPosition >= 0) {
                dateSpinner.setSelection(defaultPosition)
                loadAndDisplayPredictionData(currentDate)
            } else if (availableDates.isNotEmpty()) {
                dateSpinner.setSelection(0)
                loadAndDisplayPredictionData(availableDates[0])
            }
        }

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDate = parent.getItemAtPosition(position) as String
                loadAndDisplayPredictionData(selectedDate)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        loadButton.setOnClickListener {
            val selectedDate = dateSpinner.selectedItem as String
            loadAndDisplayPredictionData(selectedDate)
        }

        return view
    }

    private fun getAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val sensorDataDir = File(requireContext().getExternalFilesDir(null), "SensorData")
        if (sensorDataDir.exists() && sensorDataDir.isDirectory) {
            val files = sensorDataDir.listFiles { _, name -> name.endsWith("_predictions.csv") }
            files?.forEach { file ->
                val fileName = file.name
                val date = fileName.replace("_predictions.csv", "")
                dates.add(date)
            }
        }
        if (dates.isEmpty()) {
            dates.add(dateFormat.format(System.currentTimeMillis()))
        }
        return dates
    }

    private fun loadAndDisplayPredictionData(date: String) {
        val fileName = date + "_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")

        if (!file.exists()) {
            Log.e(TAG, "예측 데이터 CSV 파일이 존재하지 않음: $fileName")
            textDistanceInfo.text = "데이터 없음: $fileName"
            return
        }

        val predictionData = mutableListOf<Map<String, String>>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
                val headerLine = br.readLine()
                if (headerLine == null) {
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

    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        mapView.overlays.clear()
        val distanceInfo = StringBuilder("이동 기록:\n")
        var firstPoint: GeoPoint? = null

        predictionData.forEach { data ->
            val transportMode = data["transport_mode"]!!
            val distance = data["distance_meters"]!!.toDouble()
            val startTimestamp = data["start_timestamp"]!!.toLong()
            val latitude = data["latitude"]?.toDoubleOrNull() // 위치 데이터 가정
            val longitude = data["longitude"]?.toDoubleOrNull() // 위치 데이터 가정

            if (latitude != null && longitude != null) {
                val geoPoint = GeoPoint(latitude, longitude)
                val polyline = Polyline()
                polyline.setPoints(listOf(geoPoint)) // 단일 포인트라도 Polyline으로 표시 가능
                polyline.setColor(getTransportColor(transportMode))
                polyline.setWidth(5.0f)
                polyline.setTitle("$transportMode - 거리: ${String.format("%.2f m", distance)}")
                mapView.overlays.add(polyline)

                if (firstPoint == null) {
                    firstPoint = geoPoint
                }
            }

            distanceInfo.append(
                String.format(
                    "시간: %s, 이동수단: %s, 거리: %.2f m\n",
                    SimpleDateFormat("HH:mm:ss").format(startTimestamp), transportMode, distance
                )
            )
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
}