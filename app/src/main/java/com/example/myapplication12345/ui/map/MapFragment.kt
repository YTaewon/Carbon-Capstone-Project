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

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapFragment" // Timber 로깅 태그
        private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN) // 날짜 형식

        // 테스트용 CSV 파일을 생성하는 함수
        fun createTestCsvFile(context: Context, date: Date) {
            val mapDataDir = File(context.getExternalFilesDir(null), "Map").apply { mkdirs() }
            val file = File(mapDataDir, "${dateFormat.format(date)}_predictions.csv")
            if (file.exists()) return // 파일이 이미 존재하면 생성하지 않음

            try {
                FileWriter(file).use { writer ->
                    writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n")
                    // UNKNOWN_MODE를 포함하여 ETC 처리 테스트
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
                Timber.tag(TAG).d("테스트 CSV 생성 완료: $file")
            } catch (e: Exception) {
                Timber.tag(TAG).e("테스트 CSV 생성 실패: ${e.message}")
            }
        }
    }

    // UI 요소 변수 선언
    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private lateinit var textDistanceInfo: TextView
    private lateinit var loadButton: ImageView
    private lateinit var selectTransportButton: ImageView
    private lateinit var toggleDistanceButton: ImageView
    private lateinit var calendarButton: ImageView
    private lateinit var dateText: TextView
    private lateinit var testMapButton: Button
    private lateinit var findnowlocateButton: ImageView // 현재 위치 찾기 버튼

    // 위치 서비스 관련 변수
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 이동수단 관련 상수 및 선택된 모드 저장 변수
    private val TRANSPORT_MODES = listOf("WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC")
    private val selectedModes = mutableSetOf<String>().apply { addAll(TRANSPORT_MODES) } // 기본적으로 모든 모드 선택

    // 상태 변수
    private var isDistanceInfoVisible = true // 거리 정보 텍스트 표시 여부
    private var isMapInitialized = false     // 지도 초기화 완료 여부
    private var isMyLocationShown = false    // "내 위치" 기능(레이어 표시 및 추적) 활성화 여부

    // 지속적인 위치 추적을 위한 변수
    private lateinit var locationCallback: LocationCallback // 위치 업데이트 수신 콜백
    private lateinit var locationRequest: LocationRequest   // 위치 업데이트 요청 객체
    private var userInteractedWithMap = false // 사용자가 "내 위치" 기능 활성 중 지도를 수동으로 조작했는지 여부 (조작 시 자동 추적 중단)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity()) // FusedLocationProviderClient 초기화

        // 위치 업데이트 요청 설정
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000L) // (우선순위, 간격)
            .setMinUpdateIntervalMillis(5000L) // 앱이 처리 가능한 최소 업데이트 간격 (5초)
            .build()

        // 위치 업데이트를 수신하는 콜백 정의
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (googleMap == null || !isAdded) return // Fragment가 attach되지 않았거나 지도가 준비되지 않으면 무시

                // "내 위치" 기능이 꺼져 있거나 사용자가 지도를 수동으로 조작했다면 카메라 자동 이동 안 함
                if (!isMyLocationShown || userInteractedWithMap) {
                    return
                }
                // 마지막 위치 정보로 카메라 이동 (지도 따라가기)
                locationResult.lastLocation?.let { location ->
                    val newLatLng = LatLng(location.latitude, location.longitude)
                    Timber.tag(TAG).d("지속적인 위치 업데이트: $newLatLng. 카메라 이동 중.")
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLng(newLatLng)) // 현재 줌 레벨 유지하며 이동
                    // 또는 특정 줌 레벨로 이동하려면:
                    // val currentZoom = googleMap?.cameraPosition?.zoom ?: 18f // 현재 줌 레벨 가져오거나 기본값 사용
                    // googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, currentZoom))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        initializeViews(view)      // UI 요소 초기화
        setupMapView(savedInstanceState) // MapView 설정
        setupListeners()           // 이벤트 리스너 설정

        // 오늘 날짜 또는 전달받은 날짜로 UI 업데이트
        val todayDate = dateFormat.format(System.currentTimeMillis())
        val selectedDate = arguments?.getString("selectedDate") ?: todayDate
        updateDateText(selectedDate)
        updateTestMapButtonState(selectedDate) // 테스트 맵 버튼 활성화 상태 업데이트

        return view
    }

    @SuppressLint("MissingPermission") // 권한 확인은 hasLocationPermission()에서 수행
    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            // 지도 UI 설정
            uiSettings.isZoomGesturesEnabled = true
            uiSettings.isScrollGesturesEnabled = true
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false // 기본 '내 위치' 버튼 비활성화 (커스텀 버튼 사용)
        }

        // ViewPager2와의 스와이프 충돌 방지
        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)
        googleMap?.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) { // 사용자의 제스처로 카메라 이동 시작 시
                viewPager?.isUserInputEnabled = false // ViewPager 스와이프 비활성화
                if (isMyLocationShown) { // "내 위치" 기능 활성 중 사용자가 지도 조작 시
                    Timber.tag(TAG).d("사용자 지도 제스처 감지. 지속적인 추적 일시 중단.")
                    userInteractedWithMap = true // 사용자가 지도 조작했음을 표시
                    // 선택 사항: findnowlocateButton 아이콘을 "일시 중단" 상태로 변경 가능
                    // 예: findnowlocateButton.setImageResource(R.drawable.ic_location_paused)
                    // 현재는 아이콘은 ic_location_searching_true로 유지하고, userInteractedWithMap 플래그로 동작 제어
                }
            }
        }
        googleMap?.setOnCameraIdleListener { // 카메라 이동이 멈추면
            viewPager?.isUserInputEnabled = true // ViewPager 스와이프 다시 활성화
        }

        // 선택된 날짜의 예측 데이터 로드 및 표시
        val selectedDate = arguments?.getString("selectedDate") ?: dateFormat.format(System.currentTimeMillis())
        loadAndDisplayPredictionData(selectedDate)

        // 거리 정보 UI 초기 상태 설정
        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)

        isMapInitialized = true // 지도 초기화 완료

        // 화면 회전 등 구성 변경으로 Fragment가 재생성될 때, "내 위치" 기능이 활성화되어 있었다면
        // "내 위치" 레이어를 다시 활성화. 위치 업데이트 재개는 onResume에서 처리.
        if (isMyLocationShown && hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true // 권한은 hasLocationPermission()에서 이미 확인됨
            } catch (se: SecurityException) {
                Timber.tag(TAG).e(se, "onMapReady에서 '내 위치' 레이어 재활성화 중 SecurityException 발생")
                // 이 경우, onResume에서도 업데이트 재개에 실패할 수 있으므로,
                // disableMyLocationAndStopUpdates()를 호출하여 상태를 초기화하는 것을 고려.
            }
        }
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

        isDistanceInfoVisible = this.textDistanceInfo.isVisible // 초기 가시성 상태 저장
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() 호출하므로 경고 무시
    private fun setupMapView(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this) // 비동기로 지도 가져오기 (콜백: onMapReady)
        // MapView 터치 시 부모 ViewPager2의 터치 이벤트 가로채기 방지
        mapView?.setOnTouchListener { v, event ->
            v.parent?.run {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> requestDisallowInterceptTouchEvent(true) // 터치 시작 시 부모의 터치 가로채기 비활성화
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        requestDisallowInterceptTouchEvent(false) // 터치 종료 시 부모의 터치 가로채기 다시 활성화
                        if (event.action == MotionEvent.ACTION_UP) v.performClick() // 접근성을 위해 performClick 호출
                    }
                }
            }
            false // MapView 자체의 터치 이벤트는 계속 처리하도록 false 반환
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
        dateText.setOnClickListener { showDatePickerDialog() } // 날짜 텍스트 클릭 시에도 DatePicker 표시
        testMapButton.setOnClickListener { handleTestMapButtonClick() }

        // "현재 위치 찾기" 버튼 클릭 리스너
        findnowlocateButton.setOnClickListener {
            if (!isMyLocationShown) { // "내 위치" 기능이 꺼져 있다면, 켜기
                enableMyLocationAndStartUpdates()
            } else { // "내 위치" 기능이 켜져 있다면, 끄기
                disableMyLocationAndStopUpdates()
            }
        }
    }

    // "내 위치" 기능 활성화 및 위치 업데이트 시작
    @SuppressLint("MissingPermission") // 권한 확인은 hasLocationPermission()에서 수행
    private fun enableMyLocationAndStartUpdates() {
        if (googleMap == null) { // 지도가 준비되지 않았으면 실행하지 않음
            Timber.tag(TAG).w("enableMyLocationAndStartUpdates 호출되었으나 googleMap이 null입니다.")
            return
        }
        if (hasLocationPermission()) { // 위치 권한이 있다면
            try {
                googleMap?.isMyLocationEnabled = true // 지도에 "내 위치" 레이어(파란 점) 표시
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching_true) // 버튼 아이콘 변경 (활성 상태)
                isMyLocationShown = true         // "내 위치" 기능 활성화 상태로 설정
                userInteractedWithMap = false    // 사용자 지도 조작 플래그 초기화 (다시 자동 추적 시작)

                Timber.tag(TAG).d("'내 위치' 기능 활성화됨. 위치 업데이트 요청 중.")
                // 위치 업데이트 시작
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

                // 현재 위치로 한 번 카메라 이동 (초기 이동)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    // Fragment가 화면에 추가되어 있고(isAdded) 위치 정보가 유효하면
                    if (location != null && isAdded) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f)) // 애니메이션으로 이동
                        Timber.tag(TAG).d("현재 위치로 이동: $currentLatLng")
                    } else if (isAdded) {
                        Timber.tag(TAG).d("마지막으로 알려진 위치가 null입니다. 초기 이동 없음.")
                        // 선택 사항: 마지막 위치가 없을 경우 기본 위치로 이동
                        // googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
                    }
                }.addOnFailureListener { e ->
                    if(isAdded) Timber.tag(TAG).e(e, "초기 이동을 위한 마지막 위치 가져오기 실패.")
                }
            } catch (e: SecurityException) { // 권한 관련 예외 처리
                Timber.tag(TAG).e(e, "'내 위치' 활성화 및 업데이트 시작 중 SecurityException 발생.")
                if(isAdded) Toast.makeText(requireContext(), "위치 서비스를 활성화하는데 문제가 발생했습니다.", Toast.LENGTH_SHORT).show()
                // 상태 원복
                isMyLocationShown = false
                findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
            }
        } else { // 위치 권한이 없다면
            Timber.tag(TAG).d("위치 권한이 없습니다. 권한 요청 중.")
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) // 권한 요청
        }
    }

    // "내 위치" 기능 비활성화 및 위치 업데이트 중단
    private fun disableMyLocationAndStopUpdates() {
        if (googleMap == null && isAdded) { // 지도가 null이어도 Fragment가 살아있으면 로깅
            Timber.tag(TAG).w("disableMyLocationAndStopUpdates 호출되었으나 googleMap이 null입니다.")
        }
        try {
            googleMap?.isMyLocationEnabled = false // 지도에 "내 위치" 레이어 숨기기
        } catch (_: SecurityException) {
            // 비활성화 시 SecurityException은 드물지만, 발생 가능성 대비
            Timber.tag(TAG).w("'내 위치' 레이어 비활성화 중 SecurityException 발생 (무시 가능).")
        }
        if (isAdded) { // Fragment가 attach된 상태에서만 fusedLocationClient 접근
            fusedLocationClient.removeLocationUpdates(locationCallback) // 위치 업데이트 중단
        }
        findnowlocateButton.setImageResource(R.drawable.ic_location_searching) // 버튼 아이콘 변경 (비활성 상태)
        isMyLocationShown = false        // "내 위치" 기능 비활성화 상태로 설정
        userInteractedWithMap = false    // 사용자 지도 조작 플래그 초기화
        Timber.tag(TAG).d("'내 위치' 기능 비활성화 및 추적 중단됨.")
    }

    // 위치 권한 확인 함수
    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // 날짜 선택 다이얼로그 표시
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), R.style.DatePickerTheme, { _, year, month, day ->
            val selectedDate = String.format(Locale.KOREAN, "%04d%02d%02d", year, month + 1, day) // "yyyyMMdd" 형식
            updateDateText(selectedDate) // 날짜 텍스트 업데이트
            loadAndDisplayPredictionData(selectedDate) // 선택된 날짜의 데이터 로드
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    // 날짜 텍스트 업데이트 ("yyyy년 MM월 dd일" 형식으로)
    private fun updateDateText(date: String) { // date는 "yyyyMMdd" 형식
        if (date.length == 8) {
            "${date.substring(0, 4)}년 ${date.substring(4, 6)}월 ${date.substring(6, 8)}일".also { dateText.text = it }
        } else {
            dateText.text = "날짜 형식 오류"
            Timber.tag(TAG).w("dateText에 대한 날짜 형식이 잘못되었습니다: $date")
        }
    }

    // 표시된 날짜 텍스트("yyyy년 MM월 dd일")를 "yyyyMMdd" 형식으로 변환
    private fun parseDateFromText(dateTextString: String): String =
        try {
            val parts = dateTextString.replace("년 ", "-").replace("월 ", "-").replace("일", "").split("-")
            if (parts.size == 3) {
                "${parts[0]}${parts[1].padStart(2, '0')}${parts[2].padStart(2, '0')}"
            } else {
                Timber.tag(TAG).w("텍스트에서 날짜 파싱 실패: $dateTextString, 현재 날짜 사용.")
                dateFormat.format(System.currentTimeMillis()) // 파싱 실패 시 현재 날짜 반환
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "텍스트에서 날짜 파싱 중 예외 발생: $dateTextString, 현재 날짜 사용.")
            dateFormat.format(System.currentTimeMillis()) // 예외 발생 시 현재 날짜 반환
        }

    // 거리 정보 텍스트 표시/숨기기 토글
    private fun toggleDistanceInfoVisibility() {
        isDistanceInfoVisible = !isDistanceInfoVisible
        textDistanceInfo.visibility = if (isDistanceInfoVisible) View.VISIBLE else View.GONE
        toggleDistanceButton.setImageResource(if (isDistanceInfoVisible) R.drawable.ic_drop_up else R.drawable.ic_drop_down)
    }

    // 이동수단 선택 다이얼로그 표시
    private fun showTransportSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transport_selection, null)
        // 각 이동수단에 해당하는 체크박스 ID 맵
        val checkboxIds = mapOf(
            "WALK" to R.id.checkbox_walk, "BIKE" to R.id.checkbox_bike, "CAR" to R.id.checkbox_car,
            "BUS" to R.id.checkbox_bus, "SUBWAY" to R.id.checkbox_subway, "ETC" to R.id.checkbox_etc
        )
        // 개별 이동수단 체크박스 리스트 생성 및 현재 선택 상태 반영
        val individualCheckBoxes = TRANSPORT_MODES.mapNotNull { mode ->
            checkboxIds[mode]?.let { id -> dialogView.findViewById<CheckBox>(id)?.apply { isChecked = selectedModes.contains(mode) } }
        }
        // "전체 선택" 체크박스 설정 및 현재 상태 반영
        val allCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_all)?.apply {
            isChecked = individualCheckBoxes.isNotEmpty() && individualCheckBoxes.all { it.isChecked }
        }
        // "전체 선택" 체크박스 변경 시 개별 체크박스 상태 동기화
        allCheckBox?.setOnCheckedChangeListener { _, isChecked -> individualCheckBoxes.forEach { it.isChecked = isChecked } }
        // 개별 체크박스 변경 시 "전체 선택" 체크박스 상태 동기화
        individualCheckBoxes.forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, _ -> allCheckBox?.isChecked = individualCheckBoxes.all { it.isChecked } }
        }
        // 다이얼로그 생성 및 표시
        AlertDialog.Builder(requireContext())
            .setTitle("이동수단 선택").setView(dialogView)
            .setPositiveButton("확인") { _, _ -> // "확인" 버튼 클릭 시
                selectedModes.clear() // 기존 선택 모드 초기화
                // 체크된 이동수단만 selectedModes에 추가
                TRANSPORT_MODES.forEach { mode ->
                    checkboxIds[mode]?.let { id -> if (dialogView.findViewById<CheckBox>(id)?.isChecked == true) selectedModes.add(mode) }
                }
                // 변경된 선택 모드로 데이터 다시 로드 및 표시
                loadAndDisplayPredictionData(parseDateFromText(dateText.text.toString()))
            }
            .setNegativeButton("취소", null).show()
    }

    // 테스트 맵 버튼 클릭 처리 (테스트 CSV 파일 생성 및 로드)
    private fun handleTestMapButtonClick() {
        val parsedDateStr = parseDateFromText(dateText.text.toString()) // "yyyyMMdd"
        try {
            val dateObj = dateFormat.parse(parsedDateStr) // String을 Date 객체로 변환
            if (dateObj != null) {
                createTestCsvFile(requireContext(), dateObj) // 테스트 CSV 파일 생성
                loadAndDisplayPredictionData(parsedDateStr) // 데이터 로드
                Toast.makeText(requireContext(), "$parsedDateStr 테스트 CSV 생성 완료", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "유효하지 않은 날짜입니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: java.text.ParseException) { // 날짜 파싱 예외 처리
            Timber.tag(TAG).e(e, "handleTestMapButtonClick에서 날짜 파싱 오류: $parsedDateStr")
            Toast.makeText(requireContext(), "날짜 형식 오류입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 특정 날짜의 예측 데이터 로드 및 지도에 표시
    private fun loadAndDisplayPredictionData(date: String) { // date는 "yyyyMMdd" 형식
        if (!isAdded || googleMap == null) { // Fragment가 attach되지 않았거나 지도가 준비되지 않으면 중단
            Timber.tag(TAG).w("Fragment가 attach되지 않았거나 GoogleMap이 준비되지 않음. $date 데이터 로드 건너뛰기.")
            return
        }
        Timber.tag(TAG).d("$date 날짜의 데이터 로딩 중...")
        googleMap?.clear() // 이전 지도 데이터(폴리라인 등) 지우기

        val file = File(requireContext().getExternalFilesDir(null), "Map/${date}_predictions.csv")
        if (!file.exists()) { // 해당 날짜의 CSV 파일이 없으면
            "데이터 없음: $date".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f)) // 기본 위치로 이동
            updateTestMapButtonState(date) // 테스트 맵 버튼 상태 업데이트 (파일 없으므로 활성화)
            Timber.tag(TAG).i("$date 에 대한 데이터 파일 없음. 기본 메시지 및 지도 뷰 표시.")
            return
        }

        val predictionData = loadCsvData(file) // CSV 파일에서 데이터 로드
        if (predictionData.isEmpty()) { // 파일은 있으나 내용이 없거나 로드 실패 시
            "데이터 없음: $date (파일 내용 없음)".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
        } else {
            displayPredictionOnMap(predictionData) // 로드된 데이터를 지도에 표시
        }
        updateTestMapButtonState(date) // 테스트 맵 버튼 상태 업데이트 (파일 있으므로 비활성화)
    }

    // CSV 파일에서 데이터를 읽어 List<Map<String, String>> 형태로 반환
    private fun loadCsvData(file: File): List<Map<String, String>> {
        val predictionData = mutableListOf<Map<String, String>>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
                // 첫 번째 줄(헤더) 읽기. 헤더가 없으면 빈 리스트 반환.
                val headers = br.readLine()?.split(",") ?: run {
                    Timber.tag(TAG).w("CSV 파일이 비어있거나 헤더가 없습니다: ${file.path}")
                    return emptyList()
                }
                // 각 데이터 라인 처리
                br.forEachLine { line ->
                    val values = line.split(",")
                    if (values.size == headers.size) { // 헤더 수와 값 수가 일치하면 데이터 추가
                        predictionData.add(headers.zip(values).toMap())
                    } else { // 형식이 맞지 않는 라인은 건너뛰고 로그 기록
                        Timber.tag(TAG).w("CSV에서 잘못된 형식의 라인 건너뛰기: $line (예상 ${headers.size} 값, 실제 ${values.size} 값)")
                    }
                }
            }
        } catch (e: Exception) { // 파일 읽기 중 예외 발생 시
            "CSV 로드 실패: ${e.message}".also { textDistanceInfo.text = it }
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            Timber.tag(TAG).e(e, "${file.path}에서 CSV 데이터 로드 실패")
        }
        return predictionData
    }

    // 로드된 예측 데이터를 지도에 폴리라인 등으로 표시
    @SuppressLint("DefaultLocale") // String.format에서 Locale.KOREAN 사용
    private fun displayPredictionOnMap(predictionData: List<Map<String, String>>) {
        if (!isAdded || googleMap == null) { // Fragment attach 및 지도 준비 상태 확인
            Timber.tag(TAG).w("displayPredictionOnMap 중 Fragment가 attach되지 않았거나 GoogleMap이 준비되지 않음.")
            return
        }

        val distanceInfo = StringBuilder("이동 거리 합계:\n") // 거리 정보 표시용 StringBuilder
        var firstPoint: LatLng? = null // 첫 번째 데이터의 시작점으로 카메라 이동하기 위함
        var hasData = false            // 표시할 데이터가 있는지 여부
        var totalOverallDistance = 0.0 // 모든 이동수단의 총 이동 거리

        val definedTransportModes = TRANSPORT_MODES.toSet() // 정의된 이동수단 집합
        // 이동수단 영문명을 한글명으로 매핑
        val modeNames = mapOf("WALK" to "걷기", "BIKE" to "자전거", "CAR" to "자동차", "BUS" to "버스", "SUBWAY" to "지하철", "ETC" to "기타")

        // 1. 데이터 전처리: transport_mode 유효성 검사 (정의되지 않은 모드는 "ETC"로)
        // 2. 필터링: 현재 선택된 이동수단(selectedModes)에 해당하는 데이터만 필터링
        val processedAndFilteredData = predictionData.map { data ->
            val originalMode = data["transport_mode"]?.uppercase(Locale.ROOT)
            // 정의된 이동수단 목록에 있으면 해당 모드 사용, 없으면 "ETC"로 처리
            val validatedMode = if (originalMode != null && definedTransportModes.contains(originalMode)) originalMode else "ETC"
            data.toMutableMap().apply { put("transport_mode", validatedMode) } // transport_mode 업데이트
        }.filter { selectedModes.contains(it["transport_mode"]) } // 선택된 이동수단만 필터링

        if (processedAndFilteredData.isEmpty()) { // 필터링 후 데이터가 없으면
            textDistanceInfo.text = if (selectedModes.isEmpty()) "선택된 이동수단 없음" else "선택된 이동수단에 대한 데이터 없음"
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
            return
        }

        // 이동수단별로 그룹화하여 처리
        processedAndFilteredData.groupBy { it["transport_mode"]!! }.forEach { (mode, dataList) ->
            var totalDistanceForMode = 0.0 // 해당 이동수단의 총 이동 거리
            // 시작 시간(timestamp) 기준으로 정렬
            val sortedData = dataList.sortedBy { it["start_timestamp"]?.toLongOrNull() ?: 0L }

            sortedData.forEach { data ->
                val distance = data["distance_meters"]?.toDoubleOrNull() ?: 0.0
                val startLat = data["start_latitude"]?.toDoubleOrNull()
                val startLon = data["start_longitude"]?.toDoubleOrNull()
                val endLat = data["end_latitude"]?.toDoubleOrNull()
                val endLon = data["end_longitude"]?.toDoubleOrNull()

                // 좌표값이 모두 유효하면 폴리라인 추가
                if (startLat != null && startLon != null && endLat != null && endLon != null) {
                    val startPoint = LatLng(startLat, startLon)
                    val endPoint = LatLng(endLat, endLon)

                    totalDistanceForMode += distance   // 해당 모드의 거리 누적
                    totalOverallDistance += distance   // 전체 거리 누적
                    if (firstPoint == null) firstPoint = startPoint // 첫 번째 데이터의 시작점 저장
                    hasData = true                     // 표시할 데이터가 있음을 표시

                    // 지도에 폴리라인 추가
                    googleMap?.addPolyline(
                        PolylineOptions()
                            .add(startPoint, endPoint)              // 시작점과 끝점
                            .color(getTransportColor(mode))         // 이동수단별 색상
                            .width(8f)                              // 선 두께
                    )
                }
            }
            if (totalDistanceForMode > 0) { // 해당 이동수단의 이동 거리가 있으면 정보 추가
                distanceInfo.append(String.format(Locale.KOREAN, "%s: %.2f m\n", modeNames[mode] ?: mode, totalDistanceForMode))
            }
        }

        if (hasData) { // 표시된 데이터가 있으면
            distanceInfo.append("--------------------\n")
            distanceInfo.append(String.format(Locale.KOREAN, "총 이동거리: %.2f m", totalOverallDistance))
            textDistanceInfo.text = distanceInfo.toString() // 거리 정보 텍스트뷰 업데이트
            firstPoint?.let { // 첫 번째 데이터 지점으로 카메라 이동
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
            }
        } else { // 필터링 결과 표시할 데이터가 없으면
            textDistanceInfo.text = "표시할 데이터 없음 (필터링 후)"
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.177306, 128.567773), 15f))
        }
    }

    // 위치 권한 요청 결과 처리
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) { // 권한이 승인되면
            Timber.tag(TAG).d("위치 권한 승인됨. '내 위치' 기능 활성화 중.")
            // 사용자가 명시적으로 권한을 승인했으므로, userInteractedWithMap=false 상태로 추적 시작
            enableMyLocationAndStartUpdates()
        } else { // 권한이 거부되면
            Timber.tag(TAG).d("위치 권한 거부됨")
            if(isAdded) Toast.makeText(requireContext(), "위치 권한이 거부되어 현재 위치를 표시할 수 없습니다.", Toast.LENGTH_LONG).show()
            // UI가 비활성화 상태를 정확히 반영하도록 보장
            isMyLocationShown = false
            findnowlocateButton.setImageResource(R.drawable.ic_location_searching)
            userInteractedWithMap = false
        }
    }

    // 이동수단별 색상 반환
    private fun getTransportColor(mode: String): Int = when (mode.uppercase(Locale.ROOT)) {
        "WALK"   -> "#DB4437".toColorInt() // 빨강 계열
        "BIKE"   -> "#F4B400".toColorInt() // 노랑 계열
        "CAR"    -> "#0F9D58".toColorInt() // 초록 계열
        "BUS"    -> "#4285F4".toColorInt() // 파랑 계열
        "SUBWAY" -> "#7C4700".toColorInt() // 갈색 계열 (지하철 노선도 색상 참고)
        else     -> "#2E2E2E".toColorInt() // 기타 (어두운 회색)
    }

    // 특정 날짜의 CSV 파일 존재 여부 확인
    private fun isCsvFileExists(date: String): Boolean =
        File(requireContext().getExternalFilesDir(null), "Map/${date}_predictions.csv").exists()

    // 테스트 맵 버튼 활성화/비활성화 상태 업데이트 (CSV 파일 존재 여부에 따라)
    private fun updateTestMapButtonState(date: String) {
        testMapButton.isEnabled = !isCsvFileExists(date) // 파일이 없으면 버튼 활성화, 있으면 비활성화
    }

    // --- Fragment 생명주기 콜백 ---
    @SuppressLint("MissingPermission") // 권한 확인은 hasLocationPermission()에서 수행
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        // "내 위치" 기능이 활성화되어 있었고, 권한이 있고, 지도가 준비되었다면
        if (isMyLocationShown && hasLocationPermission() && googleMap != null) {
            // 위치 업데이트 리스닝 재개.
            // "내 위치" 레이어(파란 점)도 다시 활성화되어야 함.
            // userInteractedWithMap 상태는 유지됨 (일시 중지 전에 사용자가 지도를 움직였다면, onResume 후에도 자동 추적 안 함)
            Timber.tag(TAG).d("onResume 호출됨. '내 위치' 기능 활성 상태였음. 업데이트 재개. userInteractedWithMap: $userInteractedWithMap")
            try {
                googleMap?.isMyLocationEnabled = true // "내 위치" 레이어 가시성 재확인
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper()) // 위치 업데이트 재시작
            } catch (se: SecurityException) { // 권한이 중간에 철회된 경우 등
                Timber.tag(TAG).e(se, "onResume에서 위치 업데이트 재개 중 SecurityException 발생. 기능 비활성화.")
                // 예외 발생 시, 추가 문제 방지를 위해 기능 비활성화
                disableMyLocationAndStopUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        // Fragment가 보이지 않거나 비활성 상태일 때 배터리 절약을 위해 위치 업데이트 중단.
        // isMyLocationShown 및 userInteractedWithMap 상태는 유지됨.
        if (isAdded) { // Fragment가 여전히 Activity에 attach되어 있는지 확인
            fusedLocationClient.removeLocationUpdates(locationCallback) // 위치 업데이트 중단
            Timber.tag(TAG).d("onPause에서 위치 업데이트 중단됨.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 명시적으로 위치 업데이트 중단 (onPause에서 이미 처리했어야 하지만, 안전 장치)
        if (::fusedLocationClient.isInitialized) { // 초기화되었는지 확인 후 사용
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        mapView?.onDestroy() // MapView 리소스 해제
        mapView = null
        googleMap = null // 지도 객체 참조 해제
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