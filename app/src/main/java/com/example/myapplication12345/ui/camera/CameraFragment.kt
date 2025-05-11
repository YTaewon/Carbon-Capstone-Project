package com.example.myapplication12345.ui.camera

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import com.example.myapplication12345.databinding.FragmentCameraBinding
import com.example.myapplication12345.ui.calendar.CalendarViewModel
import com.example.myapplication12345.ui.home.HomeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.Calendar

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val EMISSION_FACTOR = 0.4567
    private var isPermissionRequested = false

    // ViewModel 초기화
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()

    // Firebase 관련 변수
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (canInputThisMonth()) {
                processImageUri(it)
            } else {
                Toast.makeText(requireContext(), "이 달에는 이미 입력했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            binding.startCameraButton.isEnabled = true
            Timber.d("Camera permission granted")
        } else {
            Toast.makeText(
                requireContext(),
                "카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
            binding.startCameraButton.isEnabled = false
        }
        isPermissionRequested = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (allPermissionsGranted()) {
            binding.startCameraButton.isEnabled = true
            binding.startCameraButton.visibility = View.VISIBLE
        } else if (!isPermissionRequested) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            binding.startCameraButton.isEnabled = false
        }

        binding.startCameraButton.setOnClickListener {
            if (allPermissionsGranted()) {
                startCamera()
                binding.startCameraButton.visibility = View.GONE
            } else {
                Toast.makeText(requireContext(), "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.captureButton.setOnClickListener {
            if (canInputThisMonth()) {
                capturePhoto()
            } else {
                Toast.makeText(requireContext(), "이 달에는 이미 입력했습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.galleryButton.setOnClickListener {
            getContent.launch("image/*")
        }

        // 저장된 값 표시
        displaySavedResult()

        // Pull-to-Refresh 설정
        binding.swipeRefreshLayout.setOnRefreshListener {
            displaySavedResult()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    // 사용자 ID별 SharedPreferences 가져오기
    private fun getUserPrefs(): SharedPreferences? {
        val userId = auth.currentUser?.uid ?: run {
            Timber.w("User not logged in")
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return null
        }
        return requireContext().getSharedPreferences("CameraInputPrefs_$userId", Context.MODE_PRIVATE)
    }

    // 저장된 결과 표시
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

    // 현재 달과 같은 달인지 확인
    private fun isSameMonth(timestamp: Long): Boolean {
        val currentCalendar = Calendar.getInstance()
        val lastInputCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return currentCalendar.get(Calendar.YEAR) == lastInputCalendar.get(Calendar.YEAR) &&
                currentCalendar.get(Calendar.MONTH) == lastInputCalendar.get(Calendar.MONTH)
    }

    // 월별 입력 가능 여부 확인
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

    // 입력 시간과 결과 저장
    private fun saveInputTimeAndResult(usage: Double, carbonEmission: Double): Boolean {
        try {
            val prefs = getUserPrefs() ?: return false
            with(prefs.edit()) {
                putLong("lastInputTime", System.currentTimeMillis())
                putFloat("usage", usage.toFloat())
                putFloat("carbonEmission", carbonEmission.toFloat())
                commit() // 동기 저장
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
            _binding?.let { binding ->
                val cameraProvider = cameraProviderFuture.get()
                this.cameraProvider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    Timber.d("Camera successfully bound")
                } catch (exc: Exception) {
                    Timber.e(exc, "Camera binding failed")
                    Toast.makeText(requireContext(), "카메라 시작 실패: ${exc.message}", Toast.LENGTH_LONG).show()
                    binding.startCameraButton.visibility = View.VISIBLE
                }
            } ?: Timber.w("Binding is null, camera setup skipped")
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val bitmap = imageProxy.toBitmap
                        imageProxy.close()
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        recognizeText(inputImage)
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing captured image")
                        Toast.makeText(requireContext(), "이미지 처리 오류", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "Capture failed: ${exception.message}")
                    Toast.makeText(requireContext(), "사진 캡처 실패", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private val ImageProxy.toBitmap: Bitmap
        get() {
            val buffer: ByteBuffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

    private fun processImageUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizeText(image)
            }
        } catch (e: Exception) {
            Timber.e(e, "Image processing error")
            Toast.makeText(requireContext(), "이미지 처리 중 오류 발생", Toast.LENGTH_SHORT).show()
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
                        // 먼저 SharedPreferences 저장
                        if (saveInputTimeAndResult(usage, carbonEmission)) {
                            // 저장 성공 시 UI 업데이트 및 데이터 추가
                            activity?.runOnUiThread {
                                calculateAndDisplayCarbon(usage, carbonEmission)
                            }
                            addCarbonEmissionAndScore(carbonEmission)
                        } else {
                            activity?.runOnUiThread {
                                binding.resultText.text = "데이터 저장 실패. 다시 시도해주세요."
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            binding.resultText.text = "전기 사용량을 찾을 수 없습니다.\n다른 이미지를 시도해보세요."
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Text recognition processing error")
                    activity?.runOnUiThread {
                        binding.resultText.text = "텍스트 처리 중 오류 발생"
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Text recognition failed")
                activity?.runOnUiThread {
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

    // 탄소 배출량 및 점수 추가
    private fun addCarbonEmissionAndScore(carbonEmission: Double) {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Timber.w("User not logged in")
                Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return
            }

            val userRef = database.getReference("users").child(userId)

            // 1. 탄소 배출량을 포인트로 추가
            val pointsToAdd = carbonEmission.toInt()
            homeViewModel.addPoints(pointsToAdd)
            Timber.d("Added $pointsToAdd points for carbon emission")

            // 2. 점수 50점 추가
            userRef.child("score").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val currentScore = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentScore + 50
                    return com.google.firebase.database.Transaction.success(currentData)
                }

                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                    if (error != null || !committed) {
                        Timber.tag("Firebase").e(error?.toException() ?: Exception("Transaction failed"), "addScore:onComplete")
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "점수 추가 실패", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "50점 추가! 총 탄소 배출량: ${pointsToAdd}kg CO2", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })

            // 3. 오늘 날짜의 productEmissions 업데이트
            val today = Calendar.getInstance()
            calendarViewModel.updateProductEmissions(today, pointsToAdd) { success ->
                activity?.runOnUiThread {
                    if (success) {
                        Timber.d("Updated productEmissions for today: $pointsToAdd")
                    } else {
                        Toast.makeText(requireContext(), "캘린더 데이터 업데이트 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in addCarbonEmissionAndScore")
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "데이터 추가 중 오류 발생", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        cameraProvider = null
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}