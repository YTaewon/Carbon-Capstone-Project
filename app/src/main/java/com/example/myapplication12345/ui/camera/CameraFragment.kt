package com.example.myapplication12345.ui.camera

import android.Manifest
import android.app.AlertDialog // AlertDialog 임포트 추가 (android.app.AlertDialog 사용)
import android.content.Context
import android.content.Intent // Intent 임포트 추가
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri // Uri 임포트 추가
import android.os.Bundle
import android.provider.Settings // Settings 임포트 추가 (앱 설정으로 이동 위함)
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.myapplication12345.BuildConfig
import com.example.myapplication12345.chatbot.ApiClient
import com.example.myapplication12345.chatbot.ChatGPTApi
import com.example.myapplication12345.chatbot.ChatGPTRequest
import com.example.myapplication12345.chatbot.ChatGPTResponse
import com.example.myapplication12345.chatbot.ChatMsg
import com.example.myapplication12345.databinding.FragmentCameraBinding
import com.example.myapplication12345.ui.calendar.CalendarViewModel
import com.example.myapplication12345.ui.home.HomeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var capturedBitmap: Bitmap? = null
    private val EMISSION_FACTOR = 0.4567
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private lateinit var cameraExecutor: ExecutorService


    // 카메라 시작 상태를 저장할 플래그
    private var isCameraStarted = false

    // 갤러리 이미지 선택을 위한 ActivityResultLauncher
    private val getContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (canInputThisMonth()) {
                processGalleryImage(it)
            } else {
                Toast.makeText(requireContext(), "이 달에는 이미 전기 고지서를 입력했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 카메라 권한 요청을 위한 ActivityResultLauncher
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 권한이 승인됨: 카메라 시작
                Timber.d("카메라 권한 승인됨.")
                startCamera()
                binding.startCameraButton.visibility = View.GONE // 카메라 시작 후 시작 버튼 숨김
            } else {
                // 권한 거부됨
                Timber.w("카메라 권한 거부됨.")
                Toast.makeText(requireContext(), "카메라 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()

                // "다시 묻지 않음"을 선택했거나 영구적으로 거부한 경우
                // shouldShowRequestPermissionRationale()가 false를 반환하면 영구 거부된 것임
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    showPermissionDeniedPermanentlyDialog("카메라")
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.startCameraButton.setOnClickListener {
            // 카메라 시작 전 권한 확인
            checkCameraPermissionAndStartCamera()
        }

        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.galleryButton.setOnClickListener {
            // 갤러리 이미지 선택은 READ_EXTERNAL_STORAGE 권한이 필요할 수 있지만,
            // ActivityResultContracts.GetContent()는 시스템 파일 선택기를 사용하므로 일반적으로 권한 요청이 필요 없음.
            // (Android 10+에서는 Scoped Storage로 인해 더욱 그렇습니다.)
            getContentLauncher.launch("image/*")
        }

        binding.analyzeButton.setOnClickListener {
            analyzeImageAndSendToGPT()
        }

        binding.resultText.text = "사진 촬영 혹은 갤러리에서 이미지를 선택하세요."
        displaySavedResult()
    }

    // 카메라 권한 확인 및 요청 메서드
    private fun checkCameraPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // 권한이 이미 승인됨
            Timber.d("카메라 권한 이미 승인됨. 카메라 시작.")
            startCamera()
            binding.startCameraButton.visibility = View.GONE // 시작 버튼 숨김
        } else {
            // 권한이 없는 경우, 설명을 보여줄지 결정
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // 이전에 권한을 거부했으나 "다시 묻지 않음"을 선택하지 않은 경우
                // 사용자에게 권한이 필요한 이유를 설명하는 다이얼로그 표시
                showCameraPermissionRationaleDialog()
            } else {
                // 권한 요청 (설명 불필요하거나, 사용자가 '다시 묻지 않음'을 선택한 경우)
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // 카메라 권한 설명 다이얼로그
    private fun showCameraPermissionRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("카메라 권한 필요")
            .setMessage("사진 촬영 기능을 사용하려면 카메라 접근 권한이 필요합니다. 권한을 허용해 주세요.")
            .setPositiveButton("확인") { dialog, which ->
                // 사용자가 확인을 누르면 권한 요청 실행
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("취소") { dialog, which ->
                Toast.makeText(requireContext(), "카메라 권한 요청이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                Timber.d("카메라 권한 설명 다이얼로그 취소됨.")
            }
            .create()
            .show()
    }

    // 권한이 영구적으로 거부되었을 때 앱 설정으로 이동을 제안하는 다이얼로그
    private fun showPermissionDeniedPermanentlyDialog(permissionName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("권한 필요")
            .setMessage("$permissionName 권한이 영구적으로 거부되었습니다. 앱 설정에서 수동으로 권한을 허용해야 합니다.")
            .setPositiveButton("설정으로 이동") { dialog, which ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireActivity().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("취소", null) // 취소 버튼 클릭 시 아무것도 하지 않음
            .create()
            .show()
    }

    private fun getUserPrefs(): SharedPreferences? {
        val userId = auth.currentUser?.uid ?: run {
            Timber.w("User not logged in")
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return null
        }
        return requireContext().getSharedPreferences("CameraInputPrefs_$userId", Context.MODE_PRIVATE)
    }

    private fun displaySavedResult() {
        try {
            val prefs = getUserPrefs() ?: return
            val lastInputTime = prefs.getLong("lastInputTime", 0L)

            if (lastInputTime != 0L && isSameMonth(lastInputTime)) {
                val usage = prefs.getFloat("usage", -1f).toDouble()
                val carbonEmission = prefs.getFloat("carbonEmission", -1f).toDouble()
                if (usage >= 0 && carbonEmission >= 0) {
                    binding.resultText.text = buildString {
                        append("전기 사용량: $usage kWh\n")
                        append("탄소 배출량: %.2f kg CO2".format(carbonEmission))
                    }
                } else {
                    binding.resultText.text = "저장된 데이터가 없습니다."
                }
            } else {
                binding.resultText.text = "이 달의 데이터가 없습니다."
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to display saved result")
            binding.resultText.text = "결과 표시 중 오류 발생"
        }
    }

    private fun isSameMonth(timestamp: Long): Boolean {
        val currentCalendar = Calendar.getInstance()
        val lastInputCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return currentCalendar.get(Calendar.YEAR) == lastInputCalendar.get(Calendar.YEAR) &&
                currentCalendar.get(Calendar.MONTH) == lastInputCalendar.get(Calendar.MONTH)
    }

    private fun canInputThisMonth(): Boolean {
        try {
            val prefs = getUserPrefs() ?: return false
            val lastInputTime = prefs.getLong("lastInputTime", 0L)
            return lastInputTime == 0L || !isSameMonth(lastInputTime)
        } catch (e: Exception) {
            Timber.e(e, "Error checking monthly input")
            return false
        }
    }

    private fun saveInputTimeAndResult(usage: Double, carbonEmission: Double): Boolean {
        try {
            val prefs = getUserPrefs() ?: return false
            with(prefs.edit()) {
                putLong("lastInputTime", System.currentTimeMillis())
                putFloat("usage", usage.toFloat())
                putFloat("carbonEmission", carbonEmission.toFloat())
                commit()
            }
            Timber.d("Saved input for user ${auth.currentUser?.uid}: usage=$usage, carbonEmission=$carbonEmission")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save input time and result")
            Toast.makeText(requireContext(), "데이터 저장 실패", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder().build()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                Timber.d("카메라 시작됨")

                // 성공 시 플래그를 true로 설정하고 시작 버튼을 숨깁니다.
                isCameraStarted = true
                binding.startCameraButton.visibility = View.GONE

            } catch (e: Exception) {
                Timber.e(e, "카메라 유즈케이스 바인딩 실패")
                Toast.makeText(requireContext(), "카메라 시작 실패", Toast.LENGTH_SHORT).show()

                // 실패 시 플래그를 false로 유지하고 시작 버튼을 다시 보여줍니다.
                isCameraStarted = false
                binding.startCameraButton.visibility = View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(requireContext(), "카메라가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    val bitmap = imageProxy.toBitmap()
                    capturedBitmap = bitmap
                    imageProxy.close()
                    requireActivity().runOnUiThread {
                        binding.resultText.text = "사진이 촬영되었습니다. 분석 버튼을 눌러주세요."
                    }
                } catch (e: Exception) {
                    Timber.e(e, "이미지 캡처 처리 실패")
                    Toast.makeText(requireContext(), "이미지 처리 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "사진 촬영 실패: ${exception.message}")
                Toast.makeText(requireContext(), "사진 촬영 실패", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun processGalleryImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                capturedBitmap = bitmap
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizeText(image)
            }
        } catch (e: IOException) {
            Timber.e(e, "이미지 처리 오류")
            Toast.makeText(requireContext(), "이미지 로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                try {
                    val usage = visionText.textBlocks
                        .asSequence()
                        .map { it.text }
                        .filter { it.contains("사용량") || it.contains("당월") }
                        .mapNotNull { extractUsage(it) }
                        .firstOrNull() ?: extractUsage(visionText.text)

                    if (usage != null) {
                        val carbonEmission = usage * EMISSION_FACTOR
                        if (saveInputTimeAndResult(usage, carbonEmission)) {
                            requireActivity().runOnUiThread {
                                calculateAndDisplayCarbon(usage, carbonEmission)
                            }
                            addCarbonEmissionAndScore(carbonEmission)
                        } else {
                            requireActivity().runOnUiThread {
                                binding.resultText.text = "데이터 저장 실패. 다시 시도해주세요."
                            }
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            binding.resultText.text = "전기 사용량을 찾을 수 없습니다.\n다른 이미지를 시도해보세요."
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "텍스트 인식 처리 오류")
                    requireActivity().runOnUiThread {
                        binding.resultText.text = "텍스트 처리 중 오류 발생"
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "텍스트 인식 실패")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "텍스트 인식 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun extractUsage(text: String): Double? {
        Timber.d("분석할 텍스트: $text")

        val meterPattern = "당월지침\\s*(\\d+\\.?\\d*)".toRegex()
        val prevPattern = "전월지침\\s*(\\d+\\.?\\d*)".toRegex()

        val currentMeter = meterPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val previousMeter = prevPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()

        if (currentMeter != null && previousMeter != null) {
            val usage = currentMeter - previousMeter
            Timber.d("계량기 지침으로 계산된 사용량: $usage")
            return usage
        }

        val kwhPattern = "(\\d+)\\s*kWh".toRegex()
        val kwhMatch = text.lines()
            .filter { it.contains("당월", ignoreCase = true) }.firstNotNullOfOrNull { line ->
                kwhPattern.find(line)?.let {
                    val value = it.groupValues[1].toDoubleOrNull()
                    Timber.d("kWh 패턴 발견: $value")
                    value
                }
            }

        if (kwhMatch != null) return kwhMatch

        val numberPattern = "\\b(\\d+)\\b".toRegex()
        val numberMatch = text.lines()
            .filter { it.contains("당월", ignoreCase = true) }.firstNotNullOfOrNull { line ->
                numberPattern.find(line)?.let {
                    val value = it.groupValues[1].toDoubleOrNull()
                    Timber.d("숫자 발견: $value")
                    value
                }
            }

        return numberMatch
    }

    private fun calculateAndDisplayCarbon(usage: Double, carbonEmission: Double) {
        binding.resultText.text = buildString {
            append("전기 사용량: $usage kWh\n")
            append("탄소 배출량: %.2f kg CO2".format(carbonEmission))
        }
    }

    private fun addCarbonEmissionAndScore(carbonEmission: Double) {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Timber.w("User not logged in")
                Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return
            }

            val userRef = database.getReference("users").child(userId)
            val pointsToAdd = (carbonEmission * 1000).toInt() // kgCO2 to gCO2e
            homeViewModel.addPoints(pointsToAdd)
            Timber.d("탄소 배출량으로 ${pointsToAdd}점 추가됨")

            userRef.child("score").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val currentScore = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentScore + 50 // 고정 점수 50점 추가
                    return com.google.firebase.database.Transaction.success(currentData)
                }

                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                    if (error != null || !committed) {
                        Timber.tag("Firebase").e(error?.toException() ?: Exception("Transaction failed"), "addScore:onComplete")
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "점수 추가 실패", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "50점 추가! 총 탄소 배출량: ${pointsToAdd}gCO2e", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })

            val today = Calendar.getInstance()
            calendarViewModel.updateProductEmissions(today, pointsToAdd) { success ->
                requireActivity().runOnUiThread {
                    if (success) {
                        Timber.d("오늘의 탄소 배출량 업데이트됨: $pointsToAdd")
                    } else {
                        Toast.makeText(requireContext(), "캘린더 데이터 업데이트 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "addCarbonEmissionAndScore 오류 발생")
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "데이터 추가 중 오류 발생", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addAnalysisScore() {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Timber.w("User not logged in")
                Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return
            }

            val userRef = database.getReference("users").child(userId)
            userRef.child("score").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val currentScore = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentScore + 10 // 분석 성공 시 10점 추가
                    return com.google.firebase.database.Transaction.success(currentData)
                }

                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                    if (error != null || !committed) {
                        Timber.tag("Firebase").e(error?.toException() ?: Exception("Transaction failed"), "addAnalysisScore:onComplete")
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "분석 점수 추가 실패", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "분석 성공! 10점 추가", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "addAnalysisScore 오류 발생")
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "점수 추가 중 오류 발생", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun analyzeImageAndSendToGPT() {
        val bitmap = capturedBitmap ?: run {
            Toast.makeText(requireContext(), "분석할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        MLKitHelper.analyzeImage(requireContext(), bitmap) { combinedResult ->
            val textPart = combinedResult.substringAfter("텍스트:", "").trim()
            val labelPart = combinedResult.substringAfter("라벨:", "").substringBefore("/").trim()

            if (textPart.isNotEmpty() || labelPart.isNotEmpty()) {
                sendToGPT(textPart.takeIf { it.isNotEmpty() } ?: labelPart)
            } else {
                // MLKit 실패 시 Vision API로 대체
                sendToGPTWithImage(bitmap)
            }
        }
    }

    private fun sendToGPT(query: String) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        val msgList = ArrayList<ChatMsg>()
        msgList.add(ChatMsg("user", "가장 가능성 높은 제품의 명사를 한 단어로 추정하고, 평균 탄소배출량을 gCO2e 단위로 간단히 한 줄로 알려줘. 결과: $query"))

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build())
            .build()
        val api = retrofit.create(ChatGPTApi::class.java)

        val request = ChatGPTRequest("gpt-3.5-turbo", msgList)

        api.getChatResponse(request).enqueue(object : Callback<ChatGPTResponse> {
            override fun onResponse(call: Call<ChatGPTResponse>, response: Response<ChatGPTResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.choices[0].message.content
                    requireActivity().runOnUiThread {
                        binding.resultText.text = reply
                        addAnalysisScore()
                        val emissions = extractEmissionsFromReply(reply)
                        if (emissions != null) {
                            val today = Calendar.getInstance()
                            calendarViewModel.updateObjectEmissions(today, emissions) { success ->
                                if (success) {
                                    Timber.d("사물 탄소 배출량 저장 성공: $emissions gCO2e")
                                    Toast.makeText(requireContext(), "사물 탄소 배출량 $emissions gCO2e 저장", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "사물 탄소 배출량 저장 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "탄소 배출량 파싱 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    requireActivity().runOnUiThread {
                        binding.resultText.text = "GPT 응답 오류: ${response.message()}"
                    }
                }
            }

            override fun onFailure(call: Call<ChatGPTResponse>, t: Throwable) {
                requireActivity().runOnUiThread {
                    binding.resultText.text = "GPT 호출 실패: ${t.message}"
                }
            }
        })
    }

    private fun sendToGPTWithImage(bitmap: Bitmap) {
        val base64Image = bitmapToBase64(bitmap) ?: run {
            Toast.makeText(requireContext(), "이미지 인코딩 실패", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = BuildConfig.OPENAI_API_KEY
        val msgList = ArrayList<ChatMsg>()
        msgList.add(ChatMsg("user", """
            이 이미지에 있는 물체를 식별하고, 해당 물체의 탄소 배출량을 gCO2e 단위로 추정해 주세요.
            결과는 '제품: {name}, 탄소배출량: {amount}gCO2e' 형식으로 반환해 주세요.
            이미지: data:image/jpeg;base64,$base64Image
        """.trimIndent()))

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build())
            .build()
        val api = retrofit.create(ChatGPTApi::class.java)
        val request = ChatGPTRequest("gpt-4-vision-preview", msgList)

        api.getChatResponse(request).enqueue(object : Callback<ChatGPTResponse> {
            override fun onResponse(call: Call<ChatGPTResponse>, response: Response<ChatGPTResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.choices[0].message.content
                    requireActivity().runOnUiThread {
                        binding.resultText.text = reply
                        addAnalysisScore()

                        val emissions = extractEmissionsFromReply(reply)
                        if (emissions != null) {
                            val today = Calendar.getInstance()
                            calendarViewModel.updateObjectEmissions(today, emissions) { success ->
                                if (success) {
                                    Timber.d("사물 탄소 배출량 저장 성공: $emissions gCO2e")
                                    Toast.makeText(requireContext(), "사물 탄소 배출량 $emissions gCO2e 저장", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "사물 탄소 배출량 저장 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "탄소 배출량 파싱 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("ChatGPT 요청 실패: $errorBody")
                    requireActivity().runOnUiThread {
                        binding.resultText.text = "GPT 응답 오류: ${response.code()}"
                    }
                }
            }

            override fun onFailure(call: Call<ChatGPTResponse>, t: Throwable) {
                Timber.e(t, "ChatGPT 요청 오류")
                requireActivity().runOnUiThread {
                    binding.resultText.text = "GPT 호출 실패: ${t.message}"
                }
            }
        })
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Bitmap to Base64 conversion failed")
            null
        }
    }

    private fun extractEmissionsFromReply(reply: String): Int? {
        try {
            // "제품: {name}, 탄소배출량: {amount}gCO2e" 형식
            val gCO2ePattern = "탄소배출량:\\s*(\\d+\\.?\\d*)\\s*gCO2e".toRegex()
            gCO2ePattern.find(reply)?.let {
                return it.groupValues[1].toDouble().toInt()
            }

            // "탄소배출량: {amount}kgCO2e" 형식
            val kgCO2ePattern = "탄소배출량:\\s*(\\d+\\.?\\d*)\\s*kgCO2e".toRegex()
            kgCO2ePattern.find(reply)?.let {
                return (it.groupValues[1].toDouble() * 1000).toInt()
            }

            // 단순 숫자 형식 (예: "123gCO2e")
            val numberPattern = "(\\d+\\.?\\d*)\\s*(gCO2e|kgCO2e)".toRegex()
            numberPattern.find(reply)?.let {
                val value = it.groupValues[1].toDouble()
                val unit = it.groupValues[2]
                return if (unit == "kgCO2e") (value * 1000).toInt() else value.toInt()
            }

            Timber.w("Failed to parse emissions from reply: $reply")
            return null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse emissions from reply: $reply")
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}