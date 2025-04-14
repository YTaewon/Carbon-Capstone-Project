package com.example.myapplication12345.ui.sidebar.foodcalculator

import android.os.Bundle
import android.widget.ImageView
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
        val backButton = binding.backButton
        backButton.setOnClickListener {
            finish()
        }

        // 필터 버튼
        val filterButton = binding.selectTransportButton
        filterButton.setOnClickListener {
            filterManager.showFilter()
        }

        // 새로고침 버튼
        val loadButton = binding.loadButton
        loadButton.setOnClickListener {
            filterManager.resetFilter()
        }

        // 음식 데이터 초기화
        val foodDataManager = FoodDataManager()
        categories = foodDataManager.getFoodCategories()

        // 어댑터 설정
        adapter = FoodExpandableListAdapter(this, categories)
        binding.expandableListView.setAdapter(adapter) // setAdapter 사용

        // FilterManager 초기화
        filterManager = FilterManager(this, categories) {
            adapter.updateCategories(categories)
        }

        // 계산 버튼 클릭
        binding.calculateButton.setOnClickListener {
            var totalCarbon = 0.0
            categories.filter { it.isSelected }.forEach { category ->
                category.items.forEach { food ->
                    totalCarbon += food.carbonValue * food.quantity
                }
            }
            binding.textViewResult.text = buildString {
                append("결과: %.2f gCO2e")
            }.format(totalCarbon)
        }
    }
}