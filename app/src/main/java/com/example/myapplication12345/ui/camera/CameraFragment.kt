package com.example.myapplication12345.ui.camera

import android.Manifest
import android.content.Context
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
import com.example.myapplication12345.databinding.FragmentCameraBinding
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
            binding.swipeRefreshLayout.isRefreshing = false // 새로고침 완료
        }
    }

    // 저장된 결과 표시
    private fun displaySavedResult() {
        val prefs = requireContext().getSharedPreferences("CameraInputPrefs", Context.MODE_PRIVATE)
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
        val prefs = requireContext().getSharedPreferences("CameraInputPrefs", Context.MODE_PRIVATE)
        val lastInputTime = prefs.getLong("lastInputTime", 0L)
        return lastInputTime == 0L || !isSameMonth(lastInputTime)
    }

    // 입력 시간과 결과 저장
    private fun saveInputTimeAndResult(usage: Double, carbonEmission: Double) {
        val prefs = requireContext().getSharedPreferences("CameraInputPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putLong("lastInputTime", System.currentTimeMillis())
            putFloat("usage", usage.toFloat())
            putFloat("carbonEmission", carbonEmission.toFloat())
            apply()
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
                    Toast.makeText(requireContext(), "카메라 시작에 실패했습니다: ${exc.message}", Toast.LENGTH_LONG).show()
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
                    val bitmap = imageProxy.toBitmap
                    imageProxy.close()
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    recognizeText(inputImage)
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "Capture failed: ${exception.message}")
                    Toast.makeText(requireContext(), "사진 캡처에 실패했습니다.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "이미지 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val usage = visionText.textBlocks
                    .asSequence()
                    .map { it.text }
                    .filter { it.contains("사용량") || it.contains("당월") }
                    .mapNotNull { extractUsage(it) }
                    .firstOrNull() ?: extractUsage(visionText.text)

                if (usage != null) {
                    val carbonEmission = usage * EMISSION_FACTOR
                    calculateAndDisplayCarbon(usage, carbonEmission)
                    saveInputTimeAndResult(usage, carbonEmission)
                } else {
                    binding.resultText.text = buildString {
                        append("전기 사용량을 찾을 수 없습니다.\n다른 이미지를 시도해보세요.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Text recognition failed")
                Toast.makeText(requireContext(), "텍스트 인식에 실패했습니다.", Toast.LENGTH_SHORT).show()
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