package com.example.myapplication12345.ui.sidebar.foodcalculator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication12345.databinding.ActivityFoodCalculatorBinding

class FoodCalculatorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFoodCalculatorBinding
    private lateinit var categories: MutableList<FoodCategory>
    private lateinit var adapter: FoodExpandableListAdapter
    private lateinit var filterManager: FilterManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뒤로가기 버튼
        binding.backButton.setOnClickListener {
            finish()
        }

        // 필터 버튼
        binding.selectTransportButton.setOnClickListener {
            filterManager.showFilter()
        }

        // 새로고침 버튼
        binding.loadButton.setOnClickListener {
            filterManager.resetFilter()
        }

        // 음식 데이터 초기화
        val foodDataManager = FoodDataManager()
        categories = foodDataManager.getFoodCategories()

        // 어댑터 설정 (수정된 어댑터 사용)
        adapter = FoodExpandableListAdapter(this, categories)
        binding.expandableListView.setAdapter(adapter)

        // FilterManager 초기화
        filterManager = FilterManager(this, categories) {
            adapter.updateCategories(categories)
        }

        // 계산 버튼 클릭
        binding.calculateButton.setOnClickListener {
            var totalCarbon = 0.0
            categories.filter { it.isSelected }.forEach { category ->
                category.items.forEach { food ->
                    // *** 중요: 계산 로직 수정 ***
                    // carbonValue는 100g당 배출량이므로, g단위로 입력된 quantity를 100으로 나눠서 곱해야 합니다.
                    if (food.quantity > 0) {
                        totalCarbon += food.carbonValue * (food.quantity / 100.0)
                    }
                }
            }
            binding.textViewResult.text = "결과: %.2f gCO2e".format(totalCarbon)
        }
    }
}