package com.example.myapplication12345.ui.dashboard

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication12345.databinding.FragmentDashboardBinding
import com.example.myapplication12345.network.BarcodeAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import android.Manifest

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private lateinit var previewUseCase: Preview
    private lateinit var analysisUseCase: ImageAnalysis

    private var isScanning = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 카메라 권한 체크 및 요청
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview UseCase 설정
            previewUseCase = Preview.Builder().build()
            previewUseCase.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

            // ImageAnalysis UseCase 설정
            analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysisUseCase.setAnalyzer(
                ContextCompat.getMainExecutor(requireContext()),
                BarcodeAnalyzer { barcodeValue ->
                    if (isScanning) {
                        isScanning = false
                        fetchFoodInfo(barcodeValue)
                    }
                }
            )

            // 후면 카메라 선택
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase
                )
            } catch (e: Exception) {
                Log.e(TAG, "카메라 바인딩 실패", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun fetchFoodInfo(barcode: String) {
        val apiKey = "a0e02a461adc46d593b5"
        // C005 서비스 ID를 사용하고 BAR_CD 파라미터로 검색
        val url = "http://openapi.foodsafetykorea.go.kr/api/$apiKey/I2570/xml/1/1/BAR_CD=$barcode"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL(url).readText()

                withContext(Dispatchers.Main) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(response.reader())

                    var eventType = parser.eventType
                    var result = StringBuilder()
                    var isResultFound = false

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "RESULT" -> {
                                        parser.next()
                                        if (parser.text == "NO_RESULT") {
                                            result.append("해당 바코드의 제품을 찾을 수 없습니다.")
                                            break
                                        }
                                    }
                                    "PRDLST_NM" -> {
                                        parser.next()
                                        result.append("제품명: ${parser.text}\n")
                                        isResultFound = true
                                    }
                                    "NUTR_CONT1" -> {
                                        parser.next()
                                        result.append("열량: ${parser.text}kcal\n")
                                    }
                                    "NUTR_CONT2" -> {
                                        parser.next()
                                        result.append("탄수화물: ${parser.text}g\n")
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }

                    if (!isResultFound) {
                        binding.productInfoText.text = "해당 바코드의 제품을 찾을 수 없습니다."
                    } else {
                        binding.productInfoText.text = result.toString()
                    }
                    binding.scanText.text = "바코드: $barcode"

                    Handler(Looper.getMainLooper()).postDelayed({
                        isScanning = true
                    }, 3000)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.productInfoText.text = "정보를 불러오는데 실패했습니다."
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "DashboardFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}