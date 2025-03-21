package com.example.myapplication12345.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.myapplication12345.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
// OSMDroid: import org.osmdroid.config.Configuration
// OSMDroid: import org.osmdroid.tileprovider.tilesource.TileSourceFactory
// OSMDroid: import org.osmdroid.util.GeoPoint
// OSMDroid: import org.osmdroid.views.MapView
// OSMDroid: import org.osmdroid.views.overlay.Polyline
// OSMDroid: import org.osmdroid.views.overlay.advancedpolyline.MonochromaticPaintList
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        const val TAG = "MapFragment" // 로그 태그
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault()) // 날짜 형식
        private const val REQUEST_LOCATION_PERMISSION = 1 // 위치 권한 요청 코드

        // 테스트 CSV 파일 생성 함수
        fun createTestCsvFile(context: Context, date: Date) {
            val sensorDataDir = File(context.getExternalFilesDir(null), "SensorData")
            if (!sensorDataDir.exists()) sensorDataDir.mkdirs() // 디렉토리 생성

            val dateString = dateFormat.format(date)
            val file = File(sensorDataDir, "${dateString}_predictions.csv")

            if (!file.exists()) {
                try {
                    FileWriter(file).use { writer ->
                        // CSV 헤더 작성
                        writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n")

                        val modes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC") // 이동 수단 목록
                        val centerPoints = listOf( // 시작 좌표
                            Pair(35.177306, 128.567773),
                            Pair(35.182838, 128.564494),
                            Pair(35.186558, 128.563180),
                            Pair(35.191691, 128.567251),
                            Pair(35.194362, 128.569659),
                            Pair(35.198268, 128.570484)
                        )
                        val endPoints = listOf( // 종료 좌표
                            Pair(35.182838, 128.564494),
                            Pair(35.186558, 128.563180),
                            Pair(35.191691, 128.567251),
                            Pair(35.194362, 128.569659),
                            Pair(35.198268, 128.570484),
                            Pair(35.202949, 128.572035)
                        )

                        modes.forEachIndexed { index, mode ->
                            val startLat = centerPoints[index].first
                            val startLon = centerPoints[index].second
                            val endLat = endPoints[index].first
                            val endLon = endPoints[index].second
                            val timestamp = System.currentTimeMillis() + index * 1000L
                            val distance = 500.0

                            // CSV 데이터 작성
                            writer.append(String.format(
                                "%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n",
                                timestamp, mode, distance, startLat, startLon, endLat, endLon
                            ))
                        }
                    }
                    Log.d(TAG, "테스트 CSV 생성 완료: $file")
                } catch (e: IOException) {
                    Log.e(TAG, "테스트 CSV 생성 실패: ${e.message}", e)
                }
            }
        }
    }

    private var mapView: MapView? = null // Google Maps 뷰
    private var googleMap: GoogleMap? = null // Google Maps 객체
    private lateinit var textDistanceInfo: TextView // 거리 정보 텍스트 뷰
    private lateinit var dateText: TextView // 날짜 텍스트 뷰
    private lateinit var loadButton: ImageView // 데이터 로드 버튼
    private lateinit var selectTransportButton: ImageView // 이동 수단 선택 버튼
    private lateinit var toggleDistanceButton: ImageView // 거리 정보 토글 버튼
    private lateinit var calendarButton: ImageView // 캘린더 버튼
    private lateinit var testMapButton: Button // 테스트 맵 버튼
    private lateinit var fusedLocationClient: FusedLocationProviderClient // 위치 클라이언트

    private val transportModes = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC") // 이동 수단 목록
    private val selectedModes = mutableSetOf<String>().apply { addAll(transportModes) } // 선택된 이동 수단
    private var isDistanceInfoVisible = true // 거리 정보 표시 여부
    private var isMapInitialized = false // 맵 초기화 여부

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OSMDroid: osmdroid 설정 초기화 (캐시 디렉토리 설정으로 타일 유지)
        // OSMDroid: Configuration.getInstance().apply {
        // OSMDroid:     load(requireContext(), requireActivity().getPreferences(Context.MODE_PRIVATE))
        // OSMDroid:     osmdroidBasePath = File(requireContext().cacheDir, "osmdroid")
        // OSMDroid:     osmdroidTileCache = File(requireContext().cacheDir, "osmdroid/tiles")
        // OSMDroid:     tileDownloadThreads = 2 // 타일 다운로드 스레드 수 증가
        // OSMDroid:     tileFileSystemCacheMaxBytes = 600L * 1024 * 1024 // 600MB 캐시 용량 설정
        // OSMDroid: }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity()) // 위치 서비스 초기화
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        // 뷰 초기화
        mapView = view.findViewById(R.id.map_view)
        textDistanceInfo = view.findViewById(R.id.text_distance_info)
        dateText = view.findViewById(R.id.date_text)
        loadButton = view.findViewById(R.id.load_button)
        selectTransportButton = view.findViewById(R.id.select_transport_button)
        toggleDistanceButton = view.findViewById(R.id.toggle_distance_button)
        calendarButton = view.findViewById(R.id.calendar_button)
        testMapButton = view.findViewById(R.id.test_map)

        // Google Maps 초기화
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

        // OSMDroid: MapView 초기 설정
        // OSMDroid: mapView?.apply {
        // OSMDroid:     setTileSource(TileSourceFactory.MAPNIK)
        // OSMDroid:     setMultiTouchControls(true)
        // OSMDroid:     controller.setZoom(18.0)
        // OSMDroid:     setTilesScaledToDpi(true)
        // OSMDroid:     isTilesScaledToDpi = true
        // OSMDroid:     setUseDataConnection(true)
        // OSMDroid:     Log.d(TAG, "MapView 초기화 완료, 타일 소스: ${tileProvider.tileSource.name()}")
        // OSMDroid:     setOnTouchListener { _, event ->
        // OSMDroid:         val parent = parent
        // OSMDroid:         when (event.action) {
        // OSMDroid:             MotionEvent.ACTION_DOWN -> {
        // OSMDroid:                 parent?.requestDisallowInterceptTouchEvent(true)
        // OSMDroid:                 Log.d(TAG, "MapView 터치 시작: 스와이프 비활성화")
        // OSMDroid:             }
        // OSMDroid:             MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        // OSMDroid:                 parent?.requestDisallowInterceptTouchEvent(false)
        // OSMDroid:                 Log.d(TAG, "MapView 터치 종료: 스와이프 활성화")
        // OSMDroid:             }
        // OSMDroid:         }
        // OSMDroid:         false
        // OSMDroid:     }
        // OSMDroid: }

        val todayDate = dateFormat.format(System.currentTimeMillis()) // 오늘 날짜
        val selectedDate = arguments?.getString("selectedDate") ?: todayDate // 선택된 날짜
        updateDateText(selectedDate)
        loadAndDisplayPredictionData(selectedDate)
        updateTestMapButtonState(selectedDate)

        // 로드 버튼 클릭 리스너
        loadButton.setOnClickListener {
            val selectedDateStr = dateText.text.toString()
            val parsedDate = parseDateFromText(selectedDateStr)
            loadAndDisplayPredictionData(parsedDate)
            updateTestMapButtonState(parsedDate)
            googleMap?.clear() // Google Maps 지도 초기화
            // OSMDroid: mapView?.invalidate()
            // OSMDroid: Log.d(TAG, "loadButton 클릭: 맵 상태 - 줌: ${mapView?.zoomLevelDouble}, 중심: ${mapView?.mapCenter}")
        }

        // 이동 수단 선택 버튼 클릭 리스너
        selectTransportButton.setOnClickListener {
            showTransportSelectionDialog()
        }

        // 거리 정보 토글 버튼 클릭 리스너
        toggleDistanceButton.setOnClickListener {
            toggleDistanceInfoVisibility()
        }

        // 캘린더 버튼 클릭 리스너
        calendarButton.setOnClickListener {
            showDatePickerDialog()
        }

        // 테스트 맵 버튼 클릭 리스너
        testMapButton.setOnClickListener {
            val selectedDateStr = dateText.text.toString()
            val parsedDateStr = parseDateFromText(selectedDateStr)
            val selectedDate = dateFormat.parse(parsedDateStr)
            if (selectedDate != null) {
                createTestCsvFile(requireContext(), selectedDate)
                updateDateText(parsedDateStr)
                loadAndDisplayPredictionData(parsedDateStr)
                updateTestMapButtonState(parsedDateStr)
                Toast.makeText(requireContext(), "$parsedDateStr 테스트 CSV 생성 완료", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "유효하지 않은 날짜입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    // Google Maps가 준비되었을 때 호출
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true // 줌 컨트롤 활성화
        googleMap?.uiSettings?.isMyLocationButtonEnabled = true // 내 위치 버튼 활성화

        val todayDate = dateFormat.format(System.currentTimeMillis())
        val selectedDate = arguments?.getString("selectedDate") ?: todayDate
        setMapToCurrentLocation() // 현재 위치로 이동
        loadAndDisplayPredictionData(selectedDate)
        Log.d(TAG, "Google Map 준비 완료")
    }

    // 현재 위치로 지도 중심 설정
    private fun setMapToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLocation = LatLng(location.latitude, location.longitude)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 18f))
                // OSMDroid: val currentLocation = GeoPoint(location.latitude, location.longitude)
                // OSMDroid: mapView?.controller?.setCenter(currentLocation)
                // OSMDroid: mapView?.invalidate()
                isMapInitialized = true
                Log.d(TAG, "현재 위치로 중심 설정: ${location.latitude}, ${location.longitude}")
            } else {
                Log.w(TAG, "위치 정보를 가져올 수 없음, 기본 위치로 설정")
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
                // OSMDroid: mapView?.controller?.setCenter(GeoPoint(35.177306, 128.567773))
                // OSMDroid: mapView?.invalidate()
                isMapInitialized = true
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "위치 가져오기 실패: ${e.message}")
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
            // OSMDroid: mapView?.controller?.setCenter(GeoPoint(35.177306, 128.567773))
            // OSMDroid: mapView?.invalidate()
            isMapInitialized = true
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            setMapToCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 18f))
            // OSMDroid: mapView?.controller?.setCenter(GeoPoint(35.177306, 128.567773))
            // OSMDroid: mapView?.invalidate()
            isMapInitialized = true
        }
    }

    // 날짜 선택 다이얼로그 표시
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
                updateTestMapButtonState(selectedDate)
            }, year, month, day
        ).show()
    }

    // 날짜 텍스트 업데이트
    private fun updateDateText(date: String) {
        val year = date.substring(0, 4)
        val month = date.substring(4, 6)
        val day = date.substring(6, 8)
        dateText.text = "${year}년 ${month}월 ${day}일"
    }

    // 텍스트에서 날짜 파싱
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

    // 거리 정보 표시 토글
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

    // 이동 수단 선택 다이얼로그 표시
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

    // 선택된 이동 수단 반환
    private fun getSelectedTransportModes(): List<String> {
        return selectedModes.toList()
    }

    // 예측 데이터 로드 및 표시
    private fun loadAndDisplayPredictionData(date: String) {
        if (!isAdded || googleMap == null) {
            Log.w(TAG, "프래그먼트가 연결되지 않았거나 GoogleMap이 null입니다. 데이터 로드 중단: $date")
            return
        }

        googleMap?.clear() // Google Maps 오버레이 초기화
        // OSMDroid: mapView?.overlays?.clear()
        // OSMDroid: mapView?.invalidate()
        Log.d(TAG, "loadAndDisplayPredictionData: 데이터 로드 시작, 날짜: $date")

        val fileName = "${date}_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")

        if (!file.exists()) {
            Log.e(TAG, "예측 데이터 CSV 파일이 존재하지 않음: $fileName")
            textDistanceInfo.text = "데이터 없음: $date"
            setMapToCurrentLocation()
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
            Log.d(TAG, "CSV 데이터 로드 완료, 항목 수: ${predictionData.size}")
        } catch (e: IOException) {
            Log.e(TAG, "CSV 로드 실패: ${e.message}", e)
            textDistanceInfo.text = "CSV 로드 실패: ${e.message}"
            setMapToCurrentLocation()
            return
        }

        displayPredictionOnMap(predictionData)
    }

    // 지도에 예측 데이터 표시
    @SuppressLint("DefaultLocale")
    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        if (!isAdded || googleMap == null) {
            Log.w(TAG, "프래그먼트가 연결되지 않았거나 GoogleMap이 null입니다. 맵 표시 중단")
            return
        }

        googleMap?.clear() // Google Maps 오버레이 초기화
        // OSMDroid: mapView?.overlays?.clear()
        val distanceInfo = StringBuilder("이동 거리 합계:\n")
        var firstPoint: LatLng? = null // Google Maps용 LatLng
        // OSMDroid: var firstPoint: GeoPoint? = null
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
                        val startPoint = LatLng(startLat, startLon) // Google Maps용 LatLng
                        val endPoint = LatLng(endLat, endLon)
                        // OSMDroid: val startPoint = GeoPoint(startLat, startLon)
                        // OSMDroid: val endPoint = GeoPoint(endLat, endLon)
                        // OSMDroid: val points = listOf(startPoint, endPoint)

                        totalDistance += distance
                        if (firstPoint == null) {
                            firstPoint = startPoint
                        }
                        hasData = true

                        val koreanTransportMode = transportModeNames[transportMode] ?: transportMode
                        // Google Maps Polyline 추가
                        val polylineOptions = PolylineOptions()
                            .add(startPoint, endPoint)
                            .color(getTransportColor(transportMode))
                            .width(5f)
                        googleMap?.addPolyline(polylineOptions)

                        // OSMDroid: val polyline = Polyline().apply {
                        // OSMDroid:     setPoints(points)
                        // OSMDroid:     setTitle("$koreanTransportMode - 거리: ${String.format("%.2f m", distance)}")
                        // OSMDroid:     val borderPaint = Paint().apply {
                        // OSMDroid:         color = Color.BLACK
                        // OSMDroid:         strokeWidth = 10.0f
                        // OSMDroid:         style = Paint.Style.STROKE
                        // OSMDroid:         strokeCap = Paint.Cap.ROUND
                        // OSMDroid:         isAntiAlias = true
                        // OSMDroid:     }
                        // OSMDroid:     outlinePaintLists.add(MonochromaticPaintList(borderPaint))
                        // OSMDroid:     val innerPaint = Paint().apply {
                        // OSMDroid:         color = getTransportColor(transportMode)
                        // OSMDroid:         strokeWidth = 5.0f
                        // OSMDroid:         style = Paint.Style.STROKE
                        // OSMDroid:         strokeCap = Paint.Cap.ROUND
                        // OSMDroid:         isAntiAlias = true
                        // OSMDroid:     }
                        // OSMDroid:     outlinePaintLists.add(MonochromaticPaintList(innerPaint))
                        // OSMDroid: }
                        // OSMDroid: mapView?.overlays?.add(polyline)
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
        // firstPoint가 null이 아닌 경우에만 지도 중심 이동
        firstPoint?.let { point ->
            if (hasData) {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 18f))
                Log.d(TAG, "맵 중심 설정: $point")
                isMapInitialized = true
            }
        }
//        if (hasData && firstPoint != null) {
//            // OSMDroid: mapView?.controller?.setCenter(firstPoint)
//            // OSMDroid: mapView?.invalidate()
//            // OSMDroid: Log.d(TAG, "맵 중심 설정: $firstPoint, 오버레이 수: ${mapView?.overlays?.size}")
//        }
        textDistanceInfo.text = distanceInfo.toString()
    }

    // 이동 수단별 색상 반환
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

    // CSV 파일 존재 여부 확인
    private fun isCsvFileExists(date: String): Boolean {
        val fileName = "${date}_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")
        return file.exists()
    }

    // 테스트 맵 버튼 상태 업데이트
    private fun updateTestMapButtonState(date: String) {
        testMapButton.isEnabled = !isCsvFileExists(date)
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if (isMapInitialized) {
            // OSMDroid: mapView?.invalidate() // 이미 초기화된 경우 갱신만
            Log.d(TAG, "onResume: 맵 이미 초기화됨, 갱신만 수행")
        } else {
            val selectedDate = parseDateFromText(dateText.text.toString())
            loadAndDisplayPredictionData(selectedDate)
            Log.d(TAG, "onResume: 맵 재초기화, 데이터 로드 수행")
        }
        // OSMDroid: Log.d(TAG, "onResume 호출: 맵 상태 - 줌: ${mapView?.zoomLevelDouble}, 중심: ${mapView?.mapCenter}")
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        Log.d(TAG, "onPause 호출")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        // OSMDroid: mapView?.onDetach()
        // OSMDroid: Log.d(TAG, "onDestroyView 호출: MapView 유지")
        Log.d(TAG, "onDestroyView 호출")
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView = null
        // OSMDroid: mapView?.onDetach() // 최종 종료 시에만 해제
        // OSMDroid: Log.d(TAG, "onDestroy 호출: MapView 해제")
        Log.d(TAG, "onDestroy 호출: MapView 해제")
    }
}