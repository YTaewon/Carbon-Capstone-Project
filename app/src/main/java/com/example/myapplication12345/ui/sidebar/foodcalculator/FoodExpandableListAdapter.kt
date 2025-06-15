package com.example.myapplication12345.ui.sidebar.foodcalculator

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
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

    // ViewHolder 클래스를 정의하여 뷰와 리스너를 함께 관리 (성능 및 안정성 향상)
    private class ChildViewHolder(view: View) {
        val nameText: TextView = view.findViewById(R.id.food_name)
        val quantityInput: EditText = view.findViewById(R.id.food_quantity)
        // 각 EditText에 연결된 TextWatcher를 저장하기 위한 변수
        var textWatcher: TextWatcher? = null
    }

    override fun getGroupCount(): Int = filteredCategories.size
    override fun getChildrenCount(groupPosition: Int): Int = filteredCategories[groupPosition].items.size
    override fun getGroup(groupPosition: Int): Any = filteredCategories[groupPosition]
    override fun getChild(groupPosition: Int, childPosition: Int): Any = filteredCategories[groupPosition].items[childPosition]
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
        textView.text = (getGroup(groupPosition) as FoodCategory).name
        return view
    }

    override fun getChildView(
        groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?
    ): View {
        val view: View
        val holder: ChildViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_food, parent, false)
            holder = ChildViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ChildViewHolder
        }

        val foodItem = getChild(groupPosition, childPosition) as FoodItem

        // --- View 재사용 문제 해결 로직 ---
        // 1. 기존에 연결되어 있던 TextWatcher를 제거합니다. (이것이 없으면 뷰가 재사용될 때 데이터가 엉킵니다)
        holder.textWatcher?.let { holder.quantityInput.removeTextChangedListener(it) }
        // ------------------------------------

        // 현재 아이템의 이름과 수량을 설정합니다.
        holder.nameText.text = foodItem.name
        if (foodItem.quantity > 0.0) {
            holder.quantityInput.setText(foodItem.quantity.toString())
        } else {
            holder.quantityInput.setText("")
        }
        holder.quantityInput.hint = "0" // 힌트 추가

        // --- 새로운 TextWatcher 설정 ---
        // 2. 현재 아이템에 맞는 새로운 TextWatcher를 생성하고 연결합니다.
        val newTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // 텍스트가 변경될 때마다 즉시 데이터 모델(foodItem)의 quantity를 업데이트합니다.
                foodItem.quantity = s.toString().toDoubleOrNull() ?: 0.0
            }
        }
        holder.quantityInput.addTextChangedListener(newTextWatcher)
        // 3. 새로 만든 리스너를 holder에 저장해 둡니다. (나중에 제거하기 위해)
        holder.textWatcher = newTextWatcher
        // -----------------------------

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    fun updateCategories(newCategories: List<FoodCategory>) {
        categories = newCategories
        notifyDataSetChanged() // 데이터가 변경되었음을 알려 UI를 갱신합니다.
    }
}