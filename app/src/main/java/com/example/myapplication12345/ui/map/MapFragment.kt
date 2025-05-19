package com.example.myapplication12345.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.graphics.toColorInt
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

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapFragment"
        private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN)

        fun createTestCsvFile(context: Context, date: Date) {
            val mapDataDir = File(context.getExternalFilesDir(null), "Map").apply { mkdirs() }
            val file = File(mapDataDir, "${dateFormat.format(date)}_predictions.csv")
            if (file.exists()) return

            try {
                FileWriter(file).use { writer ->
                    writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n")
                    // 테스트 데이터에 UNKNOWN_MODE 추가하여 ETC 처리 테스트
                    val modes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC", "UNKNOWN_MODE")
                    val centerPoints = listOf(
                        35.177306 to 128.567773, 35.182838 to 128.564494, 35.186558 to 128.563180,
                        35.191691 to 128.567251, 35.194362 to 128.569659, 35.198268 to 128.570484,
                        35.200000 to 128.571000 // UNKNOWN_MODE 좌표
                    )
                    val endPoints = listOf(
                        35.182838 to 128.564494, 35.186558 to 128.563180, 35.191691 to 128.567251,
                        35.194362 to 128.569659, 35.198268 to 128.570484, 35.202949 to 128.572035,
                        35.201000 to 128.572000 // UNKNOWN_MODE 좌표
                    )
                    modes.forEachIndexed { index, mode ->
                        writer.append(String.format(buildString {
                            append("%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n")
                        }, System.currentTimeMillis() + index * 1000L, mode, 500.0 + (index * 50), // 거리 다양화
                            centerPoints[index].first, centerPoints[index].second, endPoints[index].first, endPoints[index].second))
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

    private val TRANSPORT_MODES = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
    private val selectedModes = mutableSetOf<String>().apply { addAll(TRANSPORT_MODES) }
    private var isDistanceInfoVisible = true
    private var isMapInitialized = false
    private var isMyLocationShown = false

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
        updateTestMapButtonState(selectedDate)

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

        // isDistanceInfoVisible 상태와 실제 UI 가시성 동기화
        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)

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

        isDistanceInfoVisible = textDistanceInfo.visibility == View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
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
        }
        selectTransportButton.setOnClickListener { showTransportSelectionDialog() }
        toggleDistanceButton.setOnClickListener { toggleDistanceInfoVisibility() }
        calendarButton.setOnClickListener { showDatePickerDialog() }
        dateText.setOnClickListener { showDatePickerDialog() }
        testMapButton.setOnClickListener { handleTestMapButtonClick() }
        findnowlocateButton.setOnClickListener {
            if (!isMyLocationShown) {
                enableMyLocationIfPermitted()
            } else {
                try {
                    googleMap?.isMyLocationEnabled = false
                } catch (_: SecurityException) {
                    Timber.tag(TAG).w("위치 비활성화 중 권한 문제 발생 (무시)")
                }
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
                isMyLocationShown = false
                Timber.tag(TAG).d("현재 위치 표시 중단")
            }
        }
    }

    private fun enableMyLocationIfPermitted() {
        if (hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching_true)
                isMyLocationShown = true
                setMapToCurrentLocation()
                Timber.tag(TAG).d("내 위치 레이어 활성화됨 및 현재 위치로 이동 시작")
            } catch (e: SecurityException) {
                Timber.tag(TAG).e(e, "내 위치 레이어 활성화 중 SecurityException")
                Toast.makeText(requireContext(), "위치 서비스를 활성화하는데 문제가 발생했습니다.", Toast.LENGTH_SHORT).show()
                isMyLocationShown = false
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun setMapToCurrentLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            isMyLocationShown = false
            findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val defaultLocation = LatLng(35.177306, 128.567773)
            val targetLocation = location?.let { LatLng(it.latitude, it.longitude) } ?: defaultLocation
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 18f))
            Timber.tag(TAG).d("현재 위치로 이동: $targetLocation")
        }.addOnFailureListener {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
            Timber.tag(TAG).e(it, "위치 가져오기 실패")
            Toast.makeText(requireContext(), "현재 위치를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), R.style.DatePickerTheme, { _, year, month, day ->
            val selectedDate = String.format(Locale.KOREAN, "%04d%02d%02d", year, month + 1, day)
            updateDateText(selectedDate)
            loadAndDisplayPredictionData(selectedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateText(date: String) {
        if (date.length == 8) {
            "${date.substring(0, 4)}년 ${date.substring(4, 6)}월 ${date.substring(6, 8)}일".also { dateText.text = it }
        } else {
            dateText.text = "날짜 형식 오류"
            Timber.tag(TAG).w("Invalid date format for dateText: $date")
        }
    }

    private fun parseDateFromText(dateTextString: String): String =
        try {
            val parts = dateTextString.replace("년 ", "-").replace("월 ", "-").replace("일", "").split("-")
            if (parts.size == 3) {
                "${parts[0]}${parts[1].padStart(2, '0')}${parts[2].padStart(2, '0')}"
            } else {
                Timber.tag(TAG).w("Failed to parse date from text: $dateTextString, using current date.")
                dateFormat.format(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception parsing date from text: $dateTextString, using current date.")
            dateFormat.format(System.currentTimeMillis())
        }

    private fun toggleDistanceInfoVisibility() {
        isDistanceInfoVisible = !isDistanceInfoVisible
        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)
    }

    private fun showTransportSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transport_selection, null)

        val checkboxIds = mapOf(
            "WALK" to R.id.checkbox_walk,
            "BIKE" to R.id.checkbox_bike,
            "CAR" to R.id.checkbox_car,
            "BUS" to R.id.checkbox_bus,
            "SUBWAY" to R.id.checkbox_subway,
            "ETC" to R.id.checkbox_etc
        )

        val individualCheckBoxes = TRANSPORT_MODES.mapNotNull { mode ->
            checkboxIds[mode]?.let { id ->
                dialogView.findViewById<CheckBox>(id)
                    ?.apply { isChecked = selectedModes.contains(mode) }
            }
        }
        val allCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_all)?.apply {
            isChecked = individualCheckBoxes.isNotEmpty() && individualCheckBoxes.all { it.isChecked }
        }

        allCheckBox?.setOnCheckedChangeListener { _, isChecked ->
            individualCheckBoxes.forEach { it.isChecked = isChecked }
        }

        individualCheckBoxes.forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, _ ->
                allCheckBox?.isChecked = individualCheckBoxes.all { it.isChecked }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("이동수단 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                selectedModes.clear()
                TRANSPORT_MODES.forEach { mode ->
                    checkboxIds[mode]?.let { id ->
                        if (dialogView.findViewById<CheckBox>(id)?.isChecked == true) {
                            selectedModes.add(mode)
                        }
                    }
                }
                loadAndDisplayPredictionData(parseDateFromText(dateText.text.toString()))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun handleTestMapButtonClick() {
        val parsedDateStr = parseDateFromText(dateText.text.toString())
        try {
            val dateObj = dateFormat.parse(parsedDateStr)
            if (dateObj != null) {
                createTestCsvFile(requireContext(), dateObj)
                loadAndDisplayPredictionData(parsedDateStr)
                Toast.makeText(requireContext(), "$parsedDateStr 테스트 CSV 생성 완료", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "유효하지 않은 날짜입니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: java.text.ParseException) {
            Timber.tag(TAG).e(e, "Date parse error in handleTestMapButtonClick for $parsedDateStr")
            Toast.makeText(requireContext(), "날짜 형식 오류입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAndDisplayPredictionData(date: String) {
        if (!isAdded || googleMap == null) {
            Timber.tag(TAG).w("Fragment not added or GoogleMap not ready. Skipping data load for $date.")
            return
        }
        Timber.tag(TAG).d("Loading data for date: $date")

        googleMap?.clear()

        val file = File(requireContext().getExternalFilesDir(null), "Map/${date}_predictions.csv")
        if (!file.exists()) {
            "데이터 없음: $date".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            updateTestMapButtonState(date)
            Timber.tag(TAG).i("No data file found for $date. Displaying default message and map view.")
            return
        }

        val predictionData = loadCsvData(file)
        if (predictionData.isEmpty()) {
            "데이터 없음: $date (파일 내용 없음)".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
        } else {
            displayPredictionOnMap(predictionData)
        }
        updateTestMapButtonState(date)
    }

    private fun loadCsvData(file: File): List<Map<String, String>> {
        val predictionData = mutableListOf<Map<String, String>>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
                val headers = br.readLine()?.split(",") ?: run {
                    Timber.tag(TAG).w("CSV file is empty or header is missing: ${file.path}")
                    return emptyList()
                }
                br.forEachLine { line ->
                    val values = line.split(",")
                    if (values.size == headers.size) {
                        predictionData.add(headers.zip(values).toMap())
                    } else {
                        Timber.tag(TAG).w("Skipping malformed line in CSV: $line (expected ${headers.size} values, got ${values.size})")
                    }
                }
            }
        } catch (e: Exception) {
            "CSV 로드 실패: ${e.message}".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            Timber.tag(TAG).e(e, "Failed to load CSV data from ${file.path}")
        }
        return predictionData
    }

    @SuppressLint("DefaultLocale")
    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        if (!isAdded || googleMap == null) {
            Timber.tag(TAG).w("Fragment not added or GoogleMap not ready during displayPredictionOnMap.")
            return
        }

        val distanceInfo = StringBuilder("이동 거리 합계:\n")
        var firstPoint: LatLng? = null
        var hasData = false
        var totalOverallDistance = 0.0

        val definedTransportModes = TRANSPORT_MODES.toSet()
        val modeNames = mapOf(
            "WALK" to "걷기",
            "BIKE" to "자전거",
            "CAR" to "자동차",
            "BUS" to "버스",
            "SUBWAY" to "지하철",
            "ETC" to "기타"
        )

        val processedAndFilteredData = predictionData.map { data ->
            val originalMode = data["transport_mode"]?.uppercase(Locale.ROOT)
            val validatedMode = if (originalMode != null && definedTransportModes.contains(originalMode)) {
                originalMode
            } else {
                "ETC"
            }
            data.toMutableMap().apply { put("transport_mode", validatedMode) }
        }.filter { selectedModes.contains(it["transport_mode"]) }

        if (processedAndFilteredData.isEmpty()) {
            textDistanceInfo.text = if (selectedModes.isEmpty()) "선택된 이동수단 없음" else "선택된 이동수단에 대한 데이터 없음"
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            return
        }

        processedAndFilteredData.groupBy { it["transport_mode"]!! }.forEach { (mode, dataList) ->
            var totalDistanceForMode = 0.0
            val sortedData = dataList.sortedBy { it["start_timestamp"]?.toLongOrNull() ?: 0L }

            sortedData.forEach { data ->
                val distance = data["distance_meters"]?.toDoubleOrNull() ?: 0.0
                val startLat = data["start_latitude"]?.toDoubleOrNull()
                val startLon = data["start_longitude"]?.toDoubleOrNull()
                val endLat = data["end_latitude"]?.toDoubleOrNull()
                val endLon = data["end_longitude"]?.toDoubleOrNull()

                if (startLat != null && startLon != null && endLat != null && endLon != null) {
                    val startPoint = LatLng(startLat, startLon)
                    val endPoint = LatLng(endLat, endLon)

                    totalDistanceForMode += distance
                    totalOverallDistance += distance
                    if (firstPoint == null) firstPoint = startPoint
                    hasData = true

                    googleMap?.addPolyline(
                        PolylineOptions()
                            .add(startPoint, endPoint)
                            .color(getTransportColor(mode))
                            .width(8f)
                    )
                }
            }
            if (totalDistanceForMode > 0) {
                distanceInfo.append(String.format(Locale.KOREAN, "%s: %.2f m\n", modeNames[mode] ?: mode, totalDistanceForMode))
            }
        }

        if (hasData) {
            distanceInfo.append("--------------------\n")
            distanceInfo.append(String.format(Locale.KOREAN, "총 이동거리: %.2f m", totalOverallDistance))
            textDistanceInfo.text = distanceInfo.toString()
            firstPoint?.let {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
            }
        } else {
            textDistanceInfo.text = "표시할 데이터 없음 (필터링 후)"
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.tag(TAG).d("위치 권한 승인됨")
            try {
                googleMap?.isMyLocationEnabled = true
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching_true)
                isMyLocationShown = true
                setMapToCurrentLocation()
            } catch (e: SecurityException) {
                Timber.tag(TAG).e(e, "위치 권한 오류 (승인 후)")
                Toast.makeText(requireContext(), "위치 서비스를 활성화하는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                isMyLocationShown = false
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
            }
        } else {
            Timber.tag(TAG).d("위치 권한 거부됨")
            Toast.makeText(requireContext(), "위치 권한이 거부되어 현재 위치를 표시할 수 없습니다.", Toast.LENGTH_LONG).show()
            isMyLocationShown = false
            findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
        }
    }

    private fun getTransportColor(mode: String): Int = when (mode.uppercase(Locale.ROOT)) {
        "WALK"   -> "#DB4437".toColorInt()
        "BIKE"   -> "#F4B400".toColorInt()
        "CAR"    -> "#0F9D58".toColorInt()
        "BUS"    -> "#4285F4".toColorInt()
        "SUBWAY" -> "#7C4700".toColorInt()
        else     -> "#2E2E2E".toColorInt()
    }

    private fun isCsvFileExists(date: String): Boolean =
        File(requireContext().getExternalFilesDir(null), "Map/${date}_predictions.csv").exists()

    private fun updateTestMapButtonState(date: String) {
        testMapButton.isEnabled = !isCsvFileExists(date)
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        mapView = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}