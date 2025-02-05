package com.example.myapplication12345.ui.dashboard

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication12345.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ResultActivity : AppCompatActivity() {

    private val foodSafetyApiKey = "a0e02a461adc46d593b5"

    // UI Components
    private lateinit var progressOverlay: android.view.View
    private lateinit var progressBar: android.view.View
    private lateinit var productNameTextView: TextView
    private lateinit var servingSizeTextView: TextView
    private lateinit var decreaseButton: Button
    private lateinit var quantityTextView: TextView
    private lateinit var increaseButton: Button
    private lateinit var energyTextView: TextView
    private lateinit var fatTextView: TextView
    private lateinit var carbsTextView: TextView
    private lateinit var proteinTextView: TextView
    private lateinit var sugarTextView: TextView
    private lateinit var sodiumTextView: TextView
    private lateinit var carbonTextView: TextView
    private lateinit var missingNutrientsTextView: TextView
    private lateinit var rescanButton: Button

    // Data Variables
    private var quantity: Int = 1
    private var originalNutrition: NutritionInfo? = null
    private var originalCarbonEmission: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // Initialize views
        progressOverlay = findViewById(R.id.progressOverlay)
        progressBar = findViewById(R.id.progressBar)
        productNameTextView = findViewById(R.id.productNameTextView)
        servingSizeTextView = findViewById(R.id.servingSizeTextView)
        decreaseButton = findViewById(R.id.decreaseButton)
        quantityTextView = findViewById(R.id.quantityTextView)
        increaseButton = findViewById(R.id.increaseButton)
        energyTextView = findViewById(R.id.energyTextView)
        fatTextView = findViewById(R.id.fatTextView)
        carbsTextView = findViewById(R.id.carbsTextView)
        proteinTextView = findViewById(R.id.proteinTextView)
        sugarTextView = findViewById(R.id.sugarTextView)
        sodiumTextView = findViewById(R.id.sodiumTextView)
        carbonTextView = findViewById(R.id.carbonTextView)
        missingNutrientsTextView = findViewById(R.id.missingNutrientsTextView)
        rescanButton = findViewById(R.id.rescanButton)

        // Set initial quantity
        quantityTextView.text = quantity.toString()

        // Button listeners
        increaseButton.setOnClickListener {
            if (quantity < 100) {
                quantity++
                quantityTextView.text = quantity.toString()
                updateNutritionAndCarbonEmission()
            }
        }
        decreaseButton.setOnClickListener {
            if (quantity > 1) {
                quantity--
                quantityTextView.text = quantity.toString()
                updateNutritionAndCarbonEmission()
            }
        }
        rescanButton.setOnClickListener {
            // 재촬영 버튼 클릭 시 현재 액티비티 종료 (이전 화면 복귀)
            finish()
        }

        // Get barcode from Intent
        val barcode = intent.getStringExtra(EXTRA_BARCODE)
        if (barcode.isNullOrEmpty()) {
            displayError("바코드 정보가 없습니다.")
        } else {
            showLoading(true)
            searchProductByBarcode(barcode)
        }
    }

    private data class NutritionInfo(
        val energy: Double?,
        val fat: Double?,
        val carbs: Double?,
        val protein: Double?,
        val sugar: Double?,
        val sodium: Double?
    )

    private data class CarbonEmissionResult(
        val emission: Double,
        val missingNutrients: List<String>
    )

    private fun calculateCarbonEmission(
        energy: Double?,
        fat: Double?,
        carbs: Double?,
        protein: Double?,
        sugar: Double?,
        sodium: Double?
    ): CarbonEmissionResult {
        val missingNutrients = mutableListOf<String>()
        var penaltyMultiplier = 1.0

        val fatEmissionFactor = 0.06
        val carbsEmissionFactor = 0.03
        val proteinEmissionFactor = 0.1
        val sugarEmissionFactor = 0.02
        val sodiumEmissionFactor = 0.001

        if (fat == null) {
            missingNutrients.add("지방")
            penaltyMultiplier *= 1.2
        }
        if (carbs == null) {
            missingNutrients.add("탄수화물")
            penaltyMultiplier *= 1.2
        }
        if (protein == null) {
            missingNutrients.add("단백질")
            penaltyMultiplier *= 1.2
        }

        val fatEmission = (fat ?: 5.0) * fatEmissionFactor
        val carbsEmission = (carbs ?: 15.0) * carbsEmissionFactor
        val proteinEmission = (protein ?: 3.0) * proteinEmissionFactor
        val sugarEmission = (sugar ?: 8.0) * sugarEmissionFactor
        val sodiumEmission = (sodium ?: 100.0) * sodiumEmissionFactor / 1000

        val totalEmission = (fatEmission + carbsEmission + proteinEmission + sugarEmission + sodiumEmission) * penaltyMultiplier

        return CarbonEmissionResult(totalEmission, missingNutrients)
    }

    private fun searchProductByBarcode(barcode: String) {
        lifecycleScope.launch {
            try {
                val productName = withContext(Dispatchers.IO) {
                    val url = URL("http://openapi.foodsafetykorea.go.kr/api/$foodSafetyApiKey/C005/json/1/1/BAR_CD=$barcode")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    try {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()
                        val jsonObject = JSONObject(response)
                        val items = jsonObject.getJSONObject("C005").getJSONArray("row")
                        if (items.length() > 0) {
                            val rawProductName = items.getJSONObject(0).getString("PRDLST_NM")
                            rawProductName.replace(" ", "")
                        } else null
                    } finally {
                        connection.disconnect()
                    }
                }
                if (productName != null) {
                    searchNutritionInfo(productName)
                } else {
                    displayError("바코드에 해당하는 제품을 찾을 수 없습니다.")
                }
            } catch (e: Exception) {
                displayError("오류가 발생했습니다: ${e.message}")
            }
        }
    }

    private fun searchNutritionInfo(productName: String) {
        lifecycleScope.launch {
            try {
                val serviceKey = "/htUdqATshOhd/y8WDXw/QmL/8YHT86WpwNOjkwAFrxVmaoqsgFG+sMw/JHmkXREz7MtYrzPTXqLseQsZGxTyQ=="
                val encodedServiceKey = URLEncoder.encode(serviceKey, "UTF-8")
                val encodedFoodName = URLEncoder.encode(productName, "UTF-8")
                val urlString = "https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo01/getFoodNtrCpntDbInq01" +
                        "?serviceKey=$encodedServiceKey" +
                        "&pageNo=1" +
                        "&numOfRows=3" +
                        "&type=json" +
                        "&FOOD_NM_KR=$encodedFoodName"
                val result = withContext(Dispatchers.IO) {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    try {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()
                        response
                    } finally {
                        connection.disconnect()
                    }
                }
                withContext(Dispatchers.Main) {
                    try {
                        val jsonObject = JSONObject(result)
                        val body = jsonObject.getJSONObject("body")
                        val items = body.getJSONArray("items")
                        if (items.length() > 0) {
                            val food = items.getJSONObject(0)
                            val energy = food.optString("AMT_NUM1").toDoubleOrNull()
                            val fat = food.optString("AMT_NUM4").toDoubleOrNull()
                            val carbs = food.optString("AMT_NUM7").toDoubleOrNull()
                            val protein = food.optString("AMT_NUM3").toDoubleOrNull()
                            val sugar = food.optString("AMT_NUM8").toDoubleOrNull()
                            val sodium = food.optString("AMT_NUM14").toDoubleOrNull()
                            val servingSize = food.optString("SERVING_SIZE", "정보 없음")
                            val carbonResult = calculateCarbonEmission(energy, fat, carbs, protein, sugar, sodium)
                            originalNutrition = NutritionInfo(energy, fat, carbs, protein, sugar, sodium)
                            originalCarbonEmission = carbonResult.emission
                            productNameTextView.text = "제품명: $productName"
                            servingSizeTextView.text = "1회 제공량: $servingSize"
                            updateNutritionAndCarbonEmission()
                            if (carbonResult.missingNutrients.isNotEmpty()) {
                                missingNutrientsTextView.visibility = android.view.View.VISIBLE
                                missingNutrientsTextView.text = "누락된 영양정보: ${carbonResult.missingNutrients.joinToString(", ")}\n(누락된 정보는 평균값으로 대체되어 계산되었습니다)"
                            } else {
                                missingNutrientsTextView.visibility = android.view.View.GONE
                            }
                            showLoading(false)
                        } else {
                            displayError("영양정보를 찾을 수 없습니다.")
                        }
                    } catch (e: Exception) {
                        displayError("데이터 파싱 오류: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                displayError("네트워크 오류: ${e.message}")
            }
        }
    }

    private fun updateNutritionAndCarbonEmission() {
        originalNutrition?.let { nutrition ->
            val multiplier = quantity.toDouble()
            val updatedEnergy = nutrition.energy?.times(multiplier)
            val updatedFat = nutrition.fat?.times(multiplier)
            val updatedCarbs = nutrition.carbs?.times(multiplier)
            val updatedProtein = nutrition.protein?.times(multiplier)
            val updatedSugar = nutrition.sugar?.times(multiplier)
            val updatedSodium = nutrition.sodium?.times(multiplier)
            energyTextView.text = updatedEnergy?.let { String.format("%.2f kcal", it) } ?: "정보 없음"
            fatTextView.text = updatedFat?.let { String.format("%.2f g", it) } ?: "정보 없음"
            carbsTextView.text = updatedCarbs?.let { String.format("%.2f g", it) } ?: "정보 없음"
            proteinTextView.text = updatedProtein?.let { String.format("%.2f g", it) } ?: "정보 없음"
            sugarTextView.text = updatedSugar?.let { String.format("%.2f g", it) } ?: "정보 없음"
            sodiumTextView.text = updatedSodium?.let { String.format("%.2f mg", it) } ?: "정보 없음"
            val updatedCarbonEmission = originalCarbonEmission * multiplier
            carbonTextView.text = "총 탄소 배출량: ${String.format("%.2f", updatedCarbonEmission)} kg CO2e"
        }
    }

    private fun displayError(message: String) {
        productNameTextView.text = message
        servingSizeTextView.text = ""
        energyTextView.text = ""
        fatTextView.text = ""
        carbsTextView.text = ""
        proteinTextView.text = ""
        sugarTextView.text = ""
        sodiumTextView.text = ""
        carbonTextView.text = ""
        missingNutrientsTextView.visibility = android.view.View.GONE
        showLoading(false)
    }

    private fun showLoading(isLoading: Boolean) {
        progressOverlay.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
    }

    companion object {
        const val EXTRA_BARCODE = "extra_barcode"
    }
}
