package com.example.myapplication12345.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope // lifecycleScope 임포트
import com.example.myapplication12345.ServerManager
import com.example.myapplication12345.ui.calendar.CalendarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapFragment"
        private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN)

        // 이 함수는 파일 I/O를 포함하므로 백그라운드 스레드에서 호출해야 합니다.
        suspend fun createTestCsvFile(context: Context, date: Date) = withContext(Dispatchers.IO) {
            val mapDataDir = File(context.getExternalFilesDir(null), "Map").apply { mkdirs() }
            val file = File(mapDataDir, "${dateFormat.format(date)}_predictions.csv")
            if (file.exists()) return@withContext // 파일이 이미 존재하면 생성하지 않음

            try {
                FileWriter(file).use { writer ->
                    writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n")
                    val modes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC", "UNKNOWN_MODE")
                    val centerPoints = listOf(
                        35.177306 to 128.567773, 35.182838 to 128.564494, 35.186558 to 128.563180,
                        35.191691 to 128.567251, 35.194362 to 128.569659, 35.198268 to 128.570484,
                        35.200000 to 128.571000
                    )
                    val endPoints = listOf(
                        35.182838 to 128.564494, 35.186558 to 128.563180, 35.191691 to 128.567251,
                        35.194362 to 128.569659, 35.198268 to 128.570484, 35.202949 to 128.572035,
                        35.201000 to 128.572000
                    )
                    modes.forEachIndexed { index, mode ->
                        writer.append(String.format(Locale.ROOT, "%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n",
                            System.currentTimeMillis() + index * 1000L, mode, 500.0 + (index * 50),
                            centerPoints[index].first, centerPoints[index].second, endPoints[index].first, endPoints[index].second))
                    }
                }
                Timber.tag(TAG).d("테스트 CSV 생성 완료: $file")
            } catch (e: Exception) {
                Timber.tag(TAG).e("테스트 CSV 생성 실패: ${e.message}")
            }
        }
    }

    // UI 요소 변수
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

    // 위치 서비스 관련 변수
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    // 상태 변수
    private val TRANSPORT_MODES = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
    private val selectedModes = mutableSetOf<String>().apply { addAll(TRANSPORT_MODES) }
    private var isDistanceInfoVisible = true
    private var isMyLocationShown = false
    private var userInteractedWithMap = false

    private val calendarViewModel: CalendarViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CalendarViewModel(ServerManager(requireContext())) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (googleMap == null || !isAdded || !isMyLocationShown || userInteractedWithMap) return

                locationResult.lastLocation?.let { location ->
                    val newLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLng(newLatLng))
                }
            }
        }
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

    @SuppressLint("MissingPermission")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
        }

        // ViewPager2 스와이프 충돌 방지
        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)
        googleMap?.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                viewPager?.isUserInputEnabled = false
                if (isMyLocationShown) userInteractedWithMap = true
            }
        }
        googleMap?.setOnCameraIdleListener {
            viewPager?.isUserInputEnabled = true
        }

        val selectedDate = arguments?.getString("selectedDate") ?: dateFormat.format(System.currentTimeMillis())
        loadAndDisplayPredictionData(selectedDate)

        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)

        if (isMyLocationShown && hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true
            } catch (se: SecurityException) {
                Timber.tag(TAG).e(se, "onMapReady에서 '내 위치' 레이어 재활성화 중 SecurityException 발생")
            }
        }
    }

    // --- 주요 수정 부분: 파일 I/O를 백그라운드로 ---

    private fun loadAndDisplayPredictionData(date: String) {
        if (!isAdded || googleMap == null) return

        googleMap?.clear()

        // lifecycleScope.launch를 사용하여 코루틴 시작
        viewLifecycleOwner.lifecycleScope.launch {
            // 메인 스레드: UI 업데이트 (로딩 시작)
            textDistanceInfo.text = "데이터 로딩 중..."

            // withContext(Dispatchers.IO)로 백그라운드 스레드로 전환하여 파일 작업 수행
            val predictionData = withContext(Dispatchers.IO) {
                val file = File(requireContext().getExternalFilesDir(null), "Map/${date}_predictions.csv")
                if (file.exists()) loadCsvData(file) else null
            }

            // 메인 스레드로 자동 복귀하여 UI 업데이트
            if (!isAdded) return@launch // 작업 완료 후 fragment가 detached 되었는지 확인

            if (predictionData == null) {
                "데이터 없음: $date".also { textDistanceInfo.text = it }
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            } else if (predictionData.isEmpty()) {
                "데이터 없음: $date (파일 내용 없음)".also { textDistanceInfo.text = it }
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            } else {
                displayPredictionOnMap(predictionData)
            }
            updateTestMapButtonState(date)
        }
    }

    private fun loadCsvData(file: File): List<Map<String, String>> {
        val data = mutableListOf<Map<String, String>>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
                val headers = reader.readLine()?.split(",") ?: return emptyList()
                reader.forEachLine { line ->
                    val values = line.split(",")
                    if (values.size == headers.size) {
                        data.add(headers.zip(values).toMap())
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "CSV 로딩 실패: ${file.path}")
        }
        return data
    }

    private fun handleTestMapButtonClick() {
        viewLifecycleOwner.lifecycleScope.launch {
            val parsedDateStr = parseDateFromText(dateText.text.toString())
            try {
                val dateObj = withContext(Dispatchers.Default) { dateFormat.parse(parsedDateStr) }
                if (dateObj != null) {
                    createTestCsvFile(requireContext(), dateObj) // suspend 함수 호출
                    loadAndDisplayPredictionData(parsedDateStr)
                    Toast.makeText(requireContext(), "$parsedDateStr 테스트 CSV 생성 완료", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "유효하지 않은 날짜입니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "handleTestMapButtonClick에서 오류: $parsedDateStr")
                Toast.makeText(requireContext(), "날짜 형식 오류입니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTestMapButtonState(date: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val fileExists = withContext(Dispatchers.IO) {
                File(requireContext().getExternalFilesDir(null), "Map/${date}_predictions.csv").exists()
            }
            if(isAdded) testMapButton.isEnabled = !fileExists
        }
    }

    // --- 나머지 코드 (UI 및 로직 관련, 큰 변경 없음) ---

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
        isDistanceInfoVisible = this.textDistanceInfo.isVisible
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapView(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
        mapView?.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(event.action != MotionEvent.ACTION_UP && event.action != MotionEvent.ACTION_CANCEL)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            false
        }
    }

    private fun setupListeners() {
        loadButton.setOnClickListener {
            loadAndDisplayPredictionData(parseDateFromText(dateText.text.toString()))
        }
        selectTransportButton.setOnClickListener { showTransportSelectionDialog() }
        toggleDistanceButton.setOnClickListener { toggleDistanceInfoVisibility() }
        calendarButton.setOnClickListener { showDatePickerDialog() }
        dateText.setOnClickListener { showDatePickerDialog() }
        testMapButton.setOnClickListener { handleTestMapButtonClick() }
        findnowlocateButton.setOnClickListener {
            if (!isMyLocationShown) enableMyLocationAndStartUpdates() else disableMyLocationAndStopUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationAndStartUpdates() {
        if (googleMap == null || !hasLocationPermission()) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        try {
            googleMap?.isMyLocationEnabled = true
            findnowlocateButton.setImageResource(R.drawable.ic_location_searching_true)
            isMyLocationShown = true
            userInteractedWithMap = false
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && isAdded) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
                }
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "enableMyLocationAndStartUpdates 실패")
        }
    }

    private fun disableMyLocationAndStopUpdates() {
        try {
            googleMap?.isMyLocationEnabled = false
        } catch (_: SecurityException) {}
        if (isAdded) fusedLocationClient.removeLocationUpdates(locationCallback)
        findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
        isMyLocationShown = false
        userInteractedWithMap = false
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) enableMyLocationAndStartUpdates() else {
            if(isAdded) Toast.makeText(requireContext(), "위치 권한이 거부되어 현재 위치를 표시할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    // ... (showDatePickerDialog, updateDateText, parseDateFromText, toggleDistanceInfoVisibility, showTransportSelectionDialog 등은 변경 없음)
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
        }
    }

    private fun parseDateFromText(dateTextString: String): String =
        dateTextString.replace("[^0-9]".toRegex(), "")

    private fun toggleDistanceInfoVisibility() {
        isDistanceInfoVisible = !isDistanceInfoVisible
        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)
    }

    private fun showTransportSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transport_selection, null)
        val checkboxIds = mapOf("WALK" to R.id.checkbox_walk, "BIKE" to R.id.checkbox_bike, "CAR" to R.id.checkbox_car, "BUS" to R.id.checkbox_bus, "SUBWAY" to R.id.checkbox_subway, "ETC" to R.id.checkbox_etc)
        val checkBoxes = checkboxIds.mapValues { dialogView.findViewById<CheckBox>(it.value) }

        checkBoxes.forEach { (mode, checkBox) -> checkBox.isChecked = selectedModes.contains(mode) }
        val allCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_all)
        allCheckBox.isChecked = checkBoxes.values.all { it.isChecked }
        allCheckBox.setOnCheckedChangeListener { _, isChecked -> checkBoxes.values.forEach { it.isChecked = isChecked } }
        checkBoxes.values.forEach { cb -> cb.setOnCheckedChangeListener { _, _ -> allCheckBox.isChecked = checkBoxes.values.all { it.isChecked } } }

        AlertDialog.Builder(requireContext())
            .setTitle("이동수단 선택").setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                selectedModes.clear()
                checkBoxes.forEach { (mode, checkBox) -> if (checkBox.isChecked) selectedModes.add(mode) }
                loadAndDisplayPredictionData(parseDateFromText(dateText.text.toString()))
            }
            .setNegativeButton("취소", null).show()
    }

    @SuppressLint("DefaultLocale")
    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        if (!isAdded || googleMap == null) return

        val distanceInfo = StringBuilder("이동 거리 합계:\n")
        var firstPoint: LatLng? = null
        var totalOverallDistance = 0.0
        var totalTransportEmissions = 0.0

        val modeNames = mapOf("WALK" to "걷기", "BIKE" to "자전거", "CAR" to "자동차", "BUS" to "버스", "SUBWAY" to "지하철", "ETC" to "기타")
        val definedModes = TRANSPORT_MODES.toSet()

        val filteredData = predictionData.map { data ->
            val mode = data["transport_mode"]?.uppercase(Locale.ROOT)
            if (mode != null && definedModes.contains(mode)) data else data + ("transport_mode" to "ETC")
        }.filter { selectedModes.contains(it["transport_mode"]) }

        if (filteredData.isEmpty()) {
            textDistanceInfo.text = "선택된 이동수단에 대한 데이터 없음"
            return
        }

        filteredData.groupBy { it["transport_mode"]!! }.forEach { (mode, dataList) ->
            var distanceForMode = 0.0
            dataList.forEach { data ->
                val dist = data["distance_meters"]?.toDoubleOrNull() ?: 0.0
                val startLat = data["start_latitude"]?.toDoubleOrNull()
                val startLon = data["start_longitude"]?.toDoubleOrNull()
                val endLat = data["end_latitude"]?.toDoubleOrNull()
                val endLon = data["end_longitude"]?.toDoubleOrNull()

                if (startLat != null && startLon != null && endLat != null && endLon != null) {
                    val start = LatLng(startLat, startLon)
                    val end = LatLng(endLat, endLon)
                    distanceForMode += dist
                    if (firstPoint == null) firstPoint = start
                    googleMap?.addPolyline(PolylineOptions().add(start, end).color(getTransportColor(mode)).width(8f))
                }
            }
            if (distanceForMode > 0) {
                totalOverallDistance += distanceForMode
                val emissions = (distanceForMode / 100.0) * getEmissionFactor(mode)
                totalTransportEmissions += emissions
                distanceInfo.append(String.format(Locale.KOREAN, "%s: %.2f m (%.2f g CO₂)\n", modeNames[mode] ?: mode, distanceForMode, emissions))
            }
        }

        if (totalOverallDistance > 0) {
            distanceInfo.append("--------------------\n")
            distanceInfo.append(String.format(Locale.KOREAN, "총 이동거리: %.2f m\n", totalOverallDistance))
            distanceInfo.append(String.format(Locale.KOREAN, "총 탄소 배출량: %.2f g CO₂", totalTransportEmissions))
            textDistanceInfo.text = distanceInfo.toString()
            firstPoint?.let { googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f)) }
        } else {
            textDistanceInfo.text = "표시할 데이터 없음 (필터링 후)"
        }
    }

    private fun getEmissionFactor(mode: String): Double = when (mode.uppercase(Locale.ROOT)) {
        "WALK", "BIKE" -> 0.0
        "CAR" -> 12.0; "BUS" -> 6.0; "SUBWAY" -> 4.0; else -> 8.0
    }

    private fun getTransportColor(mode: String): Int = when (mode.uppercase(Locale.ROOT)) {
        "WALK" -> "#DB4437".toColorInt(); "BIKE" -> "#F4B400".toColorInt()
        "CAR" -> "#0F9D58".toColorInt(); "BUS" -> "#4285F4".toColorInt()
        "SUBWAY" -> "#7C4700".toColorInt(); else -> "#2E2E2E".toColorInt()
    }

    // --- Fragment 생명주기 ---
    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if (isMyLocationShown && hasLocationPermission() && googleMap != null) {
            try {
                googleMap?.isMyLocationEnabled = true
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (se: SecurityException) {
                disableMyLocationAndStopUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        if (isAdded) fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // lifecycleScope는 자동으로 취소되므로 수동 취소 불필요.
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        mapView?.onDestroy()
        mapView = null
        googleMap = null
    }

    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
}