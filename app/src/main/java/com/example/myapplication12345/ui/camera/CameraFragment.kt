package com.example.myapplication12345.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication12345.ResultActivity
import com.example.myapplication12345.databinding.FragmentCameraBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import timber.log.Timber
import java.nio.ByteBuffer

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private val EMISSION_FACTOR = 0.4567

    // 갤러리에서 이미지 선택
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processImageUri(it) }
    }

    // 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
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

        // 카메라 권한 확인
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // 카메라 캡처 버튼 클릭 시 현재 프리뷰 이미지를 캡처
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        // 갤러리에서 이미지 선택 버튼
        binding.galleryButton.setOnClickListener {
            getContent.launch("image/*")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

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
            } catch (exc: Exception) {
                Timber.e(exc, "카메라 바인딩 실패")
                Toast.makeText(requireContext(), "카메라 시작에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxy.toBitmap()
                    imageProxy.close()
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    recognizeText(inputImage)
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "캡처 실패: ${exception.message}")
                    Toast.makeText(requireContext(), "사진 캡처에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun ImageProxy.toBitmap(): Bitmap {
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
            Timber.e(e, "이미지 처리 오류")
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
                    calculateAndDisplayCarbon(usage)
                } else {
                    binding.resultText.text = "전기 사용량을 찾을 수 없습니다.\n다른 이미지를 시도해보세요."
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "텍스트 인식 실패")
                Toast.makeText(requireContext(), "텍스트 인식에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractUsage(text: String): Double? {
        Timber.d("분석할 텍스트: $text")

        // 계량기 지침 비교에서 당월/전월 지침 찾기
        val meterPattern = "당월지침\\s*(\\d+\\.?\\d*)".toRegex()
        val prevPattern = "전월지침\\s*(\\d+\\.?\\d*)".toRegex()

        val currentMeter = meterPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val previousMeter = prevPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()

        if (currentMeter != null && previousMeter != null) {
            val usage = currentMeter - previousMeter
            Timber.d("계량기 지침으로 계산된 사용량: $usage")
            return usage
        }

        // 일반적인 kWh 패턴 찾기
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

        // 숫자만 찾기 (마지막 시도)
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

    private fun calculateAndDisplayCarbon(usage: Double) {
        val carbonEmission = usage * EMISSION_FACTOR
        val intent = Intent(requireContext(), ResultActivity::class.java).apply {
            putExtra("USAGE", usage)
            putExtra("CARBON_EMISSION", carbonEmission)
        }
        startActivity(intent)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}