package com.example.myapplication12345.ui.camera

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val getContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (canInputThisMonth()) {
                processGalleryImage(it)
            } else {
                Toast.makeText(requireContext(), "이 달에는 이미 전기 고지서를 입력했습니다.", Toast.LENGTH_SHORT).show()
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
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
                binding.startCameraButton.visibility = View.GONE
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.galleryButton.setOnClickListener {
            getContentLauncher.launch("image/*")
        }

        binding.analyzeButton.setOnClickListener {
            analyzeImageAndSendToGPT()
        }

        binding.resultText.text = "사진 촬영 혹은 갤러리에서 이미지를 선택하세요."
        displaySavedResult()
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
                Timber.d("Camera started")
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind camera use cases")
                Toast.makeText(requireContext(), "카메라 시작 실패", Toast.LENGTH_SHORT).show()
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
                    Timber.e(e, "Error capturing image")
                    Toast.makeText(requireContext(), "이미지 처리 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "Capture failed: ${exception.message}")
                Toast.makeText(requireContext(), "사진 촬영 실패", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
            Timber.e(e, "Image processing error")
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
                    Timber.e(e, "Text recognition processing error")
                    requireActivity().runOnUiThread {
                        binding.resultText.text = "텍스트 처리 중 오류 발생"
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Text recognition failed")
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
            .filter { it.contains("당월", ignoreCase = true) }
            .mapNotNull { line ->
                kwhPattern.find(line)?.let {
                    val value = it.groupValues[1].toDoubleOrNull()
                    Timber.d("kWh 패턴 발견: $value")
                    value
                }
            }
            .firstOrNull()

        if (kwhMatch != null) return kwhMatch

        val numberPattern = "\\b(\\d+)\\b".toRegex()
        val numberMatch = text.lines()
            .filter { it.contains("당월", ignoreCase = true) }
            .mapNotNull { line ->
                numberPattern.find(line)?.let {
                    val value = it.groupValues[1].toDoubleOrNull()
                    Timber.d("숫자 발견: $value")
                    value
                }
            }
            .firstOrNull()

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
            Timber.d("Added $pointsToAdd points for carbon emission")

            userRef.child("score").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val currentScore = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentScore + 50
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
                        Timber.d("Updated productEmissions for today: $pointsToAdd")
                    } else {
                        Toast.makeText(requireContext(), "캘린더 데이터 업데이트 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in addCarbonEmissionAndScore")
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
                    currentData.value = currentScore + 10
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
            Timber.e(e, "Error in addAnalysisScore")
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

        // MLKit으로 텍스트/라벨 분석 (기존 로직 유지)
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