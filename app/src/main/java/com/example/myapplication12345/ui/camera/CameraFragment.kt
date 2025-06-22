package com.example.myapplication12345.ui.camera

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.myapplication12345.BuildConfig
import com.example.myapplication12345.ServerManager
import com.example.myapplication12345.chatbot.*
import com.example.myapplication12345.databinding.FragmentCameraBinding
import com.example.myapplication12345.ui.calendar.CalendarViewModel
import com.example.myapplication12345.ui.calendar.CalendarViewModelFactory
import com.example.myapplication12345.ui.home.HomeViewModel
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private enum class CameraMode { BILL, OBJECT }

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var capturedBitmap: Bitmap? = null
    private lateinit var cameraExecutor: ExecutorService

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val serverManager by lazy { ServerManager(requireContext()) }
    private val calendarViewModel: CalendarViewModel by activityViewModels {
        CalendarViewModelFactory(serverManager)
    }

    private var currentMode: CameraMode = CameraMode.OBJECT

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startCamera() else Toast.makeText(requireContext(), "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    private val getContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { if (currentMode == CameraMode.BILL) processBillImageFromGallery(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        cameraExecutor = Executors.newSingleThreadExecutor()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        switchMode(CameraMode.OBJECT)
    }

    private fun setupUI() {
        binding.startCameraButton.setOnClickListener { checkCameraPermissionAndStartCamera() }
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.galleryButton.setOnClickListener { openGalleryForBill() }
        binding.analyzeButton.setOnClickListener { analyzeObjectImage() }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> switchMode(CameraMode.OBJECT)
                    1 -> switchMode(CameraMode.BILL)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun switchMode(mode: CameraMode) {
        currentMode = mode
        capturedBitmap = null
        if (mode == CameraMode.OBJECT) {
            binding.captureButton.text = "물건 촬영"
            binding.galleryButton.visibility = View.GONE
            binding.analyzeButton.visibility = View.VISIBLE
            binding.resultText.text = "탄소 배출량을 분석할 물건을 촬영하세요."
        } else {
            binding.captureButton.text = "고지서 스캔"
            binding.galleryButton.visibility = View.VISIBLE
            binding.analyzeButton.visibility = View.GONE
            binding.resultText.text = "전기 고지서를 촬영하거나 갤러리에서 선택하세요."
            displaySavedResult()
        }
    }

    private fun checkCameraPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                imageCapture = ImageCapture.Builder().build()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                binding.startCameraButton.visibility = View.GONE
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "카메라 시작 실패", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        imageCapture?.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxy.toBitmap().also { imageProxy.close() }
                activity?.runOnUiThread {
                    when (currentMode) {
                        CameraMode.OBJECT -> {
                            capturedBitmap = bitmap
                            binding.resultText.text = "사진이 촬영되었습니다. '분석' 버튼을 눌러주세요."
                        }
                        CameraMode.BILL -> processBillImage(bitmap)
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "사진 촬영 실패")
                activity?.runOnUiThread { Toast.makeText(requireContext(), "사진 촬영 실패", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    // --- 물건 (OBJECT) 관련 기능 ---
    private fun analyzeObjectImage() {
        val bitmap = capturedBitmap ?: run {
            Toast.makeText(requireContext(), "분석할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.resultText.text = "물건을 분석 중입니다..."
        sendObjectImageToGPT(bitmap)
    }

    private fun sendObjectImageToGPT(bitmap: Bitmap) {
        val base64Image = bitmapToBase64(bitmap) ?: return
        val contentParts = listOf(
            ContentPart.Text("이 이미지에 있는 주요 물체의 명칭과 평균 탄소 배출량을 '제품: {이름}, 탄소배출량: {숫자}gCO2e' 형식으로만 알려줘."),
            ContentPart.Image(ImageUrl("data:image/jpeg;base64,$base64Image"))
        )
        val request = ChatGPTRequest("gpt-4-turbo", listOf(ChatMsg(ChatMsg.ROLE_USER, contentParts)), 300)

        ApiClient.getChatGPTApi(BuildConfig.OPENAI_API_KEY).getChatResponse(request).enqueue(object : Callback<ChatGPTResponse> {
            override fun onResponse(call: Call<ChatGPTResponse>, response: Response<ChatGPTResponse>) {
                val reply = response.body()?.choices?.firstOrNull()?.message?.content
                if (response.isSuccessful && reply != null) {
                    // --- 성공 로직 (기존과 동일) ---
                    binding.resultText.text = reply
                    addAnalysisScore()
                    extractEmissionsFromReply(reply)?.let { emissions ->
                        calendarViewModel.updateObjectEmissions(Calendar.getInstance(), emissions) { success ->
                            if (success) Toast.makeText(requireContext(), "사물 탄소 배출량 $emissions gCO2e 저장", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("GPT 응답 오류: $errorBody")

                    var errorMessage = "GPT 응답 오류: ${response.code()} ${response.message()}"

                    // 오류 JSON 파싱 시도
                    if (errorBody != null) {
                        try {
                            val gson = Gson()
                            val errorResponse = gson.fromJson(errorBody, ErrorResponse::class.java)
                            // 파싱된 상세 메시지가 있다면 에러 메시지를 교체
                            errorResponse.errorDetail?.message?.let {
                                errorMessage = "오류 (${response.code()}): $it"
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "오류 응답 JSON 파싱 실패")
                        }
                    }
                    binding.resultText.text = errorMessage
                }
            }
            override fun onFailure(call: Call<ChatGPTResponse>, t: Throwable) {
                binding.resultText.text = "GPT 호출 실패: ${t.message}"
                Timber.e(t, "GPT 호출 실패")
            }
        })
    }

    private fun addAnalysisScore() {
        val userId = auth.currentUser?.uid ?: return

        val scoreRef = database.getReference("users").child(userId).child("score")
        scoreRef.get().addOnSuccessListener { dataSnapshot ->
            val currentScore = dataSnapshot.getValue(Int::class.java) ?: 0

            scoreRef.setValue(currentScore + 10)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "분석 성공! 10점 추가", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun extractEmissionsFromReply(reply: String): Int? = "탄소배출량:\\s*(\\d+)".toRegex().find(reply)?.groupValues?.get(1)?.toIntOrNull()

    // --- 고지서 (BILL) 관련 기능 ---
    private val EMISSION_FACTOR = 0.4567

    private fun processBillImage(bitmap: Bitmap) {
        if (canInputThisMonth()) {
            binding.resultText.text = "고지서 텍스트를 분석 중입니다..."
            recognizeTextForBill(InputImage.fromBitmap(bitmap, 0))
        } else {
            Toast.makeText(requireContext(), "이 달에는 이미 전기 고지서를 입력했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processBillImageFromGallery(uri: Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use {
                processBillImage(BitmapFactory.decodeStream(it))
            }
        } catch (e: IOException) {
            Toast.makeText(requireContext(), "이미지 로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGalleryForBill() {
        if (canInputThisMonth()) getContentLauncher.launch("image/*") else Toast.makeText(requireContext(), "이 달에는 이미 전기 고지서를 입력했습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun recognizeTextForBill(image: InputImage) {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()).process(image)
            .addOnSuccessListener { visionText ->
                val usage = extractUsageFromText(visionText.text)
                if (usage != null) {
                    val carbonEmission = usage * EMISSION_FACTOR
                    if (saveInputTimeAndResult(usage, carbonEmission)) {
                        calculateAndDisplayCarbon(usage, carbonEmission)
                        addCarbonEmissionAndScore(carbonEmission)
                    }
                } else {
                    binding.resultText.text = "전기 사용량을 찾을 수 없습니다.\n다른 이미지를 시도해보세요."
                }
            }
            .addOnFailureListener { e -> Timber.e(e, "텍스트 인식 실패") }
    }

    private fun extractUsageFromText(text: String): Double? {
        val currentMeter = "당월지침\\s*(\\d+\\.?\\d*)".toRegex().find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val previousMeter = "전월지침\\s*(\\d+\\.?\\d*)".toRegex().find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        if (currentMeter != null && previousMeter != null) return currentMeter - previousMeter
        return "(\\d+)\\s*kWh".toRegex().find(text.lines().firstOrNull { it.contains("당월") } ?: "")?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun addCarbonEmissionAndScore(carbonEmission: Double) {
        val userId = auth.currentUser?.uid ?: return
        val pointsToAdd = (carbonEmission * 1000).toInt()
        homeViewModel.addPoints(pointsToAdd)

        val scoreRef = database.getReference("users").child(userId).child("score")
        scoreRef.get().addOnSuccessListener { dataSnapshot ->
            val currentScore = dataSnapshot.getValue(Int::class.java) ?: 0

            scoreRef.setValue(currentScore + 50)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "50점 추가! 총 탄소 배출량: ${pointsToAdd}gCO2e", Toast.LENGTH_SHORT).show()
                }
        }

        calendarViewModel.updateProductEmissions(Calendar.getInstance(), pointsToAdd) { success ->
            if (!success) Toast.makeText(requireContext(), "캘린더 데이터 업데이트 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateAndDisplayCarbon(usage: Double, carbonEmission: Double) {
        binding.resultText.text = "전기 사용량: $usage kWh\n탄소 배출량: %.2f kg CO2".format(carbonEmission)
    }

    private fun getUserPrefs(): SharedPreferences? = auth.currentUser?.uid?.let { requireContext().getSharedPreferences("CameraInputPrefs_$it", Context.MODE_PRIVATE) }

    private fun displaySavedResult() {
        getUserPrefs()?.let { prefs ->
            if (isSameMonth(prefs.getLong("lastInputTime", 0L))) {
                val usage = prefs.getFloat("usage", -1f).toDouble()
                if (usage >= 0) calculateAndDisplayCarbon(usage, prefs.getFloat("carbonEmission", -1f).toDouble())
            }
        }
    }

    private fun isSameMonth(timestamp: Long): Boolean {
        val current = Calendar.getInstance()
        val lastInput = Calendar.getInstance().apply { timeInMillis = timestamp }
        return current.get(Calendar.YEAR) == lastInput.get(Calendar.YEAR) && current.get(Calendar.MONTH) == lastInput.get(Calendar.MONTH)
    }

    private fun canInputThisMonth(): Boolean = getUserPrefs()?.getLong("lastInputTime", 0L)?.let { it == 0L || !isSameMonth(it) } ?: false

    private fun saveInputTimeAndResult(usage: Double, carbonEmission: Double): Boolean {
        return try {
            getUserPrefs()?.edit()?.apply {
                putLong("lastInputTime", System.currentTimeMillis())
                putFloat("usage", usage.toFloat())
                putFloat("carbonEmission", carbonEmission.toFloat())
                apply()
            }; true
        } catch (e: Exception) { false }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return try {
            Base64.encodeToString(ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, this) }.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}