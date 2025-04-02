package com.example.myapplication12345.ui.sidebar.foodcalculator

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.EditText
import android.widget.TextView
import com.example.myapplication12345.R

class FoodExpandableListAdapter(
    private val context: Context,
    private var categories: List<FoodCategory>
) : BaseExpandableListAdapter() {

    private val filteredCategories: List<FoodCategory>
        get() = categories.filter { it.isSelected }

    override fun getGroupCount(): Int = filteredCategories.size

    override fun getChildrenCount(groupPosition: Int): Int = filteredCategories[groupPosition].items.size

    override fun getGroup(groupPosition: Int): Any = filteredCategories[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): Any =
        filteredCategories[groupPosition].items[childPosition]

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()

    override fun hasStableIds(): Boolean = false

    override fun getGroupView(
        groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?
    ): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_expandable_list_item_1, parent, false
        )
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = filteredCategories[groupPosition].name
        return view
    }

    override fun getChildView(
        groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?
    ): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_food, parent, false
        )
        val foodItem = filteredCategories[groupPosition].items[childPosition]

        val nameText = view.findViewById<TextView>(R.id.food_name)
        val input = view.findViewById<EditText>(R.id.food_quantity)

        nameText.text = foodItem.name
        input.setText(if (foodItem.quantity > 0) foodItem.quantity.toString() else "")

        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                foodItem.quantity = input.text.toString().toDoubleOrNull() ?: 0.0
            }
        }

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    fun updateCategories(newCategories: List<FoodCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}