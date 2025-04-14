package com.example.myapplication12345.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication12345.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.graphics.toColorInt

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapFragment"
        private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN)

        fun createTestCsvFile(context: Context, date: Date) {
            val sensorDataDir = File(context.getExternalFilesDir(null), "SensorData").apply { mkdirs() }
            val file = File(sensorDataDir, "${dateFormat.format(date)}_predictions.csv")
            if (file.exists()) return

            try {
                FileWriter(file).use { writer ->
                    writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n")
                    val modes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
                    val centerPoints = listOf(35.177306 to 128.567773, 35.182838 to 128.564494, 35.186558 to 128.563180, 35.191691 to 128.567251, 35.194362 to 128.569659, 35.198268 to 128.570484)
                    val endPoints = listOf(35.182838 to 128.564494, 35.186558 to 128.563180, 35.191691 to 128.567251, 35.194362 to 128.569659, 35.198268 to 128.570484, 35.202949 to 128.572035)
                    modes.forEachIndexed { index, mode ->
                        writer.append(String.format(buildString {
                            append("%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n")
                        }, System.currentTimeMillis() + index * 1000L, mode, 500.0, centerPoints[index].first, centerPoints[index].second, endPoints[index].first, endPoints[index].second))
                    }
                }
                Timber.tag(TAG).d("Test CSV created: $file")
            } catch (e: Exception) {
                Timber.tag(TAG).e("Failed to create test CSV: ${e.message}")
            }
        }
    }

    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private lateinit var textDistanceInfo: TextView
    private lateinit var loadButton: ImageView
    private lateinit var selectTransportButton: ImageView
    private lateinit var toggleDistanceButton: ImageView
    private lateinit var calendarButton: ImageView
    private lateinit var dateText: TextView
    private lateinit var testMapButton: Button
    private lateinit var findnowlocateButton: ImageView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val transportModes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
    private val selectedModes = mutableSetOf<String>().apply { addAll(transportModes) }
    private var isDistanceInfoVisible = true
    private var isMapInitialized = false
    private var isMyLocationShown = false // 현재 위치 표시 상태 추적

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        initializeViews(view)
        setupMapView(savedInstanceState)
        setupListeners()

        val todayDate = dateFormat.format(System.currentTimeMillis())
        val selectedDate = arguments?.getString("selectedDate") ?: todayDate
        updateDateText(selectedDate)
        loadAndDisplayPredictionData(selectedDate)
        updateTestMapButtonState(selectedDate)
        toggleDistanceInfoVisibility()

        return view
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isZoomGesturesEnabled = true
            uiSettings.isScrollGesturesEnabled = true
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
        }

        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)
        googleMap?.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                viewPager?.isUserInputEnabled = false
            }
        }
        googleMap?.setOnCameraIdleListener { viewPager?.isUserInputEnabled = true }

        val selectedDate = arguments?.getString("selectedDate") ?: dateFormat.format(System.currentTimeMillis())
        loadAndDisplayPredictionData(selectedDate)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
        isMapInitialized = true
    }

    private fun initializeViews(view: View) {
        mapView = view.findViewById(R.id.map_view)
        textDistanceInfo = view.findViewById(R.id.text_distance_info)
        loadButton = view.findViewById(R.id.load_button)
        selectTransportButton = view.findViewById(R.id.select_transport_button)
        toggleDistanceButton = view.findViewById(R.id.toggle_distance_button)
        calendarButton = view.findViewById(R.id.calendar_button)
        dateText = view.findViewById(R.id.date_text)
        testMapButton = view.findViewById(R.id.test_map)
        findnowlocateButton = view.findViewById(R.id.find_now_locate_button)
    }

    private fun setupMapView(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
        mapView?.setOnTouchListener { v, event ->
            v.parent?.run {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        requestDisallowInterceptTouchEvent(false)
                        if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    }
                }
            }
            false
        }
    }

    private fun setupListeners() {
        loadButton.setOnClickListener {
            val parsedDate = parseDateFromText(dateText.text.toString())
            loadAndDisplayPredictionData(parsedDate)
            updateTestMapButtonState(parsedDate)
        }
        selectTransportButton.setOnClickListener { showTransportSelectionDialog() }
        toggleDistanceButton.setOnClickListener { toggleDistanceInfoVisibility() }
        calendarButton.setOnClickListener { showDatePickerDialog() }
        dateText.setOnClickListener { showDatePickerDialog() }
        testMapButton.setOnClickListener { handleTestMapButtonClick() }
        findnowlocateButton.setOnClickListener {
            if (!isMyLocationShown) {
                // 첫 클릭: 현재 위치 표시 시작
                enableMyLocationIfPermitted()
                setMapToCurrentLocation()
                isMyLocationShown = true
                Timber.tag(TAG).d("현재 위치 표시 시작")
            } else {
                // 두 번째 클릭: 현재 위치 표시 중단
                try {
                    googleMap?.isMyLocationEnabled = false
                }catch (_:SecurityException){
                    Timber.tag(TAG).d("권환 필요")
                }

                isMyLocationShown = false
                Timber.tag(TAG).d("현재 위치 표시 중단")
            }
        }
    }

    private fun enableMyLocationIfPermitted() {
        if (hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true
            }catch (_:SecurityException){
                Timber.tag(TAG).d("권환 필요")
            }
            Timber.tag(TAG).d("내 위치 레이어 활성화됨")
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun setMapToCurrentLocation() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val defaultLocation = LatLng(35.177306, 128.567773)
                val targetLocation = location?.let { LatLng(it.latitude, it.longitude) } ?: defaultLocation
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 18f))
                if (hasLocationPermission()) googleMap?.isMyLocationEnabled = true
                isMapInitialized = true
                Timber.tag(TAG).d("현재 위치로 이동: $targetLocation")
            }.addOnFailureListener {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
                isMapInitialized = true
                Timber.tag(TAG).e("위치 가져오기 실패: ${it.message}")
            }
        }catch (_:SecurityException){
            Timber.tag(TAG).d("권환 필요")
        }

    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), R.style.DatePickerTheme, { _, year, month, day ->
            val selectedDate = String.format(buildString {
                append("%04d%02d%02d")
            }, year, month + 1, day)
            updateDateText(selectedDate)
            loadAndDisplayPredictionData(selectedDate)
            updateTestMapButtonState(selectedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateText(date: String) {
        "${date.substring(0, 4)}년 ${date.substring(4, 6)}월 ${date.substring(6, 8)}일".also { dateText.text = it }
    }

    private fun parseDateFromText(dateText: String): String =
        try {
            val parts = dateText.split("년 ", "월 ", "일")
            "${parts[0]}${parts[1].padStart(2, '0')}${parts[2].padStart(2, '0')}"
        } catch (_: Exception) {
            dateFormat.format(System.currentTimeMillis())
        }

    private fun toggleDistanceInfoVisibility() {
        isDistanceInfoVisible = !isDistanceInfoVisible
        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)
    }

    private fun showTransportSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transport_selection, null)

        // transportModes와 체크박스 ID 매핑
        val checkboxIds = mapOf(
            "WALK" to R.id.checkbox_walk,
            "BIKE" to R.id.checkbox_bike,
            "BUS" to R.id.checkbox_bus,
            "CAR" to R.id.checkbox_car,
            "SUBWAY" to R.id.checkbox_subway,
            "ETC" to R.id.checkbox_etc
        )

        val checkBoxes = transportModes.map { mode ->
            dialogView.findViewById<CheckBox>(checkboxIds[mode] ?: throw IllegalArgumentException("Invalid mode: $mode"))
                .apply { isChecked = selectedModes.contains(mode) }
        } + dialogView.findViewById<CheckBox>(R.id.checkbox_all).apply { isChecked = selectedModes.size == transportModes.size }

        checkBoxes.last().setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkBoxes.dropLast(1).forEach { it.isChecked = true }
        }
        checkBoxes.dropLast(1).forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                checkBoxes.last().isChecked = if (isChecked) checkBoxes.dropLast(1).all { it.isChecked } else false
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("이동수단 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                selectedModes.clear()
                transportModes.forEachIndexed { index, mode ->
                    if (checkBoxes[index].isChecked) selectedModes.add(mode)
                }
                loadAndDisplayPredictionData(parseDateFromText(dateText.text.toString()))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun handleTestMapButtonClick() {
        val parsedDateStr = parseDateFromText(dateText.text.toString())
        dateFormat.parse(parsedDateStr)?.let { date ->
            createTestCsvFile(requireContext(), date)
            updateDateText(parsedDateStr)
            loadAndDisplayPredictionData(parsedDateStr)
            updateTestMapButtonState(parsedDateStr)
            Toast.makeText(requireContext(), "$parsedDateStr 테스트 CSV 생성 완료", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(requireContext(), "유효하지 않은 날짜입니다.", Toast.LENGTH_SHORT).show()
    }

    private fun loadAndDisplayPredictionData(date: String) {
        if (!isAdded || googleMap == null) return

        val file = File(requireContext().getExternalFilesDir(null), "SensorData/${date}_predictions.csv")
        if (!file.exists()) {
            "데이터 없음: $date".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
            return
        }

        val predictionData = loadCsvData(file)
        displayPredictionOnMap(predictionData)
    }

    private fun loadCsvData(file: File): List<Map<String, String>> {
        val predictionData = mutableListOf<Map<String, String>>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
                val headers = br.readLine()?.split(",") ?: return emptyList()
                br.forEachLine { line ->
                    val values = line.split(",")
                    if (values.size == headers.size) predictionData.add(headers.zip(values).toMap())
                }
            }
        } catch (e: Exception) {
            "CSV 로드 실패: ${e.message}".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
        }
        return predictionData
    }

    @SuppressLint("DefaultLocale")
    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        if (!isAdded || googleMap == null) return

        googleMap?.clear()
        val distanceInfo = StringBuilder("이동 거리 합계:\n")
        var firstPoint: LatLng? = null
        var hasData = false

        val validModes = setOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY")
        val modeNames = mapOf("WALK" to "걷기", "BIKE" to "자전거", "BUS" to "버스", "CAR" to "자동차", "SUBWAY" to "지하철", "ETC" to "나머지")

        val filteredData = predictionData.map { data ->
            val mode = data["transport_mode"]?.takeIf { validModes.contains(it) } ?: "ETC"
            data.toMutableMap().apply { put("transport_mode", mode) }
        }.filter { selectedModes.contains(it["transport_mode"]) }

        filteredData.groupBy { it["transport_mode"]!! }.forEach { (mode, dataList) ->
            var totalDistance = 0.0
            val sortedData = dataList.sortedBy { it["start_timestamp"]!!.toLong() }

            sortedData.forEachIndexed { index, data ->
                val distance = data["distance_meters"]!!.toDouble()
                val startPoint = data["start_latitude"]?.toDoubleOrNull()?.let { lat ->
                    data["start_longitude"]?.toDoubleOrNull()?.let { lon -> LatLng(lat, lon) }
                }
                val endPoint = data["end_latitude"]?.toDoubleOrNull()?.let { lat ->
                    data["end_longitude"]?.toDoubleOrNull()?.let { lon -> LatLng(lat, lon) }
                }

                if (startPoint != null && endPoint != null) {
                    totalDistance += distance
                    if (firstPoint == null) firstPoint = startPoint
                    hasData = true

                    // Draw the primary polyline for this segment
                    googleMap?.addPolyline(
                        PolylineOptions()
                            .add(startPoint, endPoint)
                            .color(getTransportColor(mode))
                            .width(5f)
                    )
                }
            }
            if (totalDistance > 0) distanceInfo.append(String.format("%s: %.2f m\n", modeNames[mode] ?: mode, totalDistance))
        }

        textDistanceInfo.text = if (hasData) distanceInfo.toString() else "데이터 없음"
        if (hasData) firstPoint?.let { googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 18f)); isMapInitialized = true }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.tag(TAG).d("위치 권한 승인됨")
            try {
                googleMap?.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                Timber.tag(TAG).e("위치 권한 오류: ${e.message}")
            }
            setMapToCurrentLocation()
            isMyLocationShown = true
        } else {
            Timber.tag(TAG).d("위치 권한 거부됨")
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
            isMapInitialized = true
            isMyLocationShown = false
        }
    }

    private fun getTransportColor(mode: String): Int = when (mode) {
        "WALK" -> "#DB4437".toColorInt()
        "BIKE" -> "#F4B400".toColorInt()
        "BUS" -> "#0F9D58".toColorInt()
        "CAR" -> "#4285F4".toColorInt()
        "SUBWAY" -> "#9933CC".toColorInt()
        "ETC" -> "#2E2E2E".toColorInt()
        else -> "#2E2E2E".toColorInt()
    }

    private fun isCsvFileExists(date: String): Boolean =
        File(requireContext().getExternalFilesDir(null), "SensorData/${date}_predictions.csv").exists()

    private fun updateTestMapButtonState(date: String) {
        testMapButton.isEnabled = !isCsvFileExists(date)
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if (!isMapInitialized) loadAndDisplayPredictionData(parseDateFromText(dateText.text.toString()))
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView = null
    }
}