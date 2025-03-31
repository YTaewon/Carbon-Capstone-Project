package com.example.myapplication12345.ui.sidebar.foodcalculator

import android.content.Context
import androidx.appcompat.app.AlertDialog

class FilterManager(
    private val context: Context,
    private val categories: MutableList<FoodCategory>,
    private val onFilterApplied: () -> Unit
) {
    fun showFilter() {
        val categoryNames = categories.map { it.name }.toTypedArray()
        val checkedItems = categories.map { it.isSelected }.toBooleanArray()

        AlertDialog.Builder(context)
            .setTitle("카테고리 선택")
            .setMultiChoiceItems(categoryNames, checkedItems) { _, which, isChecked ->
                categories[which].isSelected = isChecked
            }
            .setPositiveButton("확인") { _, _ ->
                onFilterApplied.invoke()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    fun resetFilter() {
        categories.forEach { it.isSelected = true }
        onFilterApplied.invoke()
    }
}