package com.example.myapplication12345.ui.calendar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication12345.R
import com.example.myapplication12345.ScoreManager
import com.example.myapplication12345.databinding.FragmentCalendarBinding
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val dayList = mutableListOf<Day>()
    private lateinit var gridAdapter: GridAdapter
    private val mCal: Calendar = Calendar.getInstance()
    private lateinit var calendarViewModel: CalendarViewModel
    private var selectedPosition: Int = -1
    private lateinit var scoreManager: ScoreManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        calendarViewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        scoreManager = ScoreManager(requireContext())
        val root: View = binding.root

        updateDateText()
        initCalendar()

        gridAdapter = GridAdapter(dayList)
        binding.gridview.adapter = gridAdapter

        binding.btnPrevMonth.setOnClickListener {
            hideIndicatorAtPosition(selectedPosition)
            selectedPosition = -1
            mCal.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            hideIndicatorAtPosition(selectedPosition)
            selectedPosition = -1
            mCal.add(Calendar.MONTH, 1)
            updateCalendar()
        }
        return root
    }

    private fun updateDateText() {
        val yearFormat = SimpleDateFormat("yyyy", Locale.KOREA)
        val monthFormat = SimpleDateFormat("MM", Locale.KOREA)
        val selectedDay = if (selectedPosition != -1 && dayList[selectedPosition].productEmissions >= 0) {
            dayList[selectedPosition].date
        } else {
            "01"
        }
        if (selectedDay.isNotEmpty() && selectedDay.all { it.isDigit() }) {
            mCal.set(Calendar.DAY_OF_MONTH, selectedDay.toInt())
        }
        val year = yearFormat.format(mCal.time)
        val month = monthFormat.format(mCal.time)
        binding.tvDate.text = "${year}년 ${month}월"
    }

    private fun initCalendar() {
        dayList.clear()
        val daysOfWeek = arrayOf("일", "월", "화", "수", "목", "금", "토")
        for (day in daysOfWeek) {
            dayList.add(Day(day, -1, -1))
        }

        mCal.set(Calendar.DAY_OF_MONTH, 1)
        val dayNum = mCal.get(Calendar.DAY_OF_WEEK)
        repeat(dayNum - 1) {
            dayList.add(Day("", 0, 0))
        }

        val daysInMonth = mCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            val dayCal = Calendar.getInstance().apply {
                set(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH), i)
            }
            dayList.add(Day(i.toString(), 0, 0))
            fetchScoreForDay(dayCal, i - 1 + dayNum - 1 + daysOfWeek.size)
        }
    }

    private fun fetchScoreForDay(date: Calendar, position: Int) {
        scoreManager.getScoresForDate(date) { score ->
            if (position < dayList.size) {
                val day = dayList[position]
                day.productEmissions = score
                activity?.runOnUiThread {
                    gridAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun updateCalendar() {
        updateDateText()
        initCalendar()
        gridAdapter.notifyDataSetChanged()
    }

    data class Day(var date: String, var productEmissions: Int, var transportEmissions: Int)

    private inner class GridAdapter(private val list: List<Day>) : BaseAdapter() {
        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Day = list[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.item_calendar_gridview, parent, false)
            val holder = if (convertView == null) {
                ViewHolder(
                    view.findViewById(R.id.tv_item_gridview),
                    view.findViewById(R.id.tv_points),
                    view.findViewById(R.id.iv_indicator)
                ).also { view.tag = it }
            } else {
                view.tag as ViewHolder
            }

            val day = getItem(position)
            val totalEmissions = day.productEmissions + day.transportEmissions
            holder.tvItemGridView.text = day.date
            holder.tvPoints.text = if (totalEmissions > 0) totalEmissions.toString() else ""

            val today = Calendar.getInstance()
            holder.tvItemGridView.isSelected = false // 기본적으로 선택 해제
            holder.tvItemGridView.isPressed = false // 기본적으로 눌림 해제

            if (day.date.isNotEmpty() && day.productEmissions >= 0 && day.date.all { it.isDigit() }) {
                val tempCal = mCal.clone() as Calendar
                tempCal.set(Calendar.DAY_OF_MONTH, day.date.toInt())
                val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)

                // 오늘 날짜 강조
                val isToday = day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                        mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                if (isToday) {
                    holder.tvItemGridView.isSelected = true // 오늘 날짜: 회색 동그라미
                }

                // 선택된 날짜 강조 (클릭 시)
                if (position == selectedPosition) {
                    holder.tvItemGridView.isPressed = true // 선택 상태: 검정 테두리 추가
                    holder.ivIndicator.visibility = View.VISIBLE
                } else {
                    holder.ivIndicator.visibility = View.INVISIBLE
                }

                // 요일별 색상 (오늘 날짜나 선택된 날짜가 아닌 경우에만 적용)
                if (!holder.tvItemGridView.isSelected && !holder.tvItemGridView.isPressed) {
                    when (dayOfWeek) {
                        Calendar.SATURDAY -> holder.tvItemGridView.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                        )
                        Calendar.SUNDAY -> holder.tvItemGridView.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                        )
                        else -> holder.tvItemGridView.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.black)
                        )
                    }
                } else {
                    holder.tvItemGridView.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.black)
                    )
                }
            } else if (day.productEmissions == -1) {
                when (position % 7) {
                    0 -> holder.tvItemGridView.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    )
                    6 -> holder.tvItemGridView.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                    )
                    else -> holder.tvItemGridView.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.black)
                    )
                }
            }

            view.setOnClickListener {
                if (day.date.isNotEmpty() && day.productEmissions >= 0 && day.date.all { it.isDigit() }) {
                    hideIndicatorAtPosition(selectedPosition)
                    selectedPosition = position
                    holder.tvItemGridView.isPressed = true // 선택 상태: 검정 테두리 추가
                    val isTodayInClick = day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                            mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    if (isTodayInClick) {
                        holder.tvItemGridView.isSelected = true // 오늘 날짜 선택 시 회색 배경 유지
                    }
                    holder.ivIndicator.visibility = View.VISIBLE
                    showPointVeiw(day)
                    gridAdapter.notifyDataSetChanged() // UI 갱신
                }
            }

            view.setOnLongClickListener {
                if (day.date.isNotEmpty() && day.productEmissions >= 0 && day.date.all { it.isDigit() }) {
                    showPointsPopup(day)
                }
                true
            }

            return view
        }
    }

    private fun hideIndicatorAtPosition(position: Int) {
        if (position >= 0) {
            val previousView = binding.gridview.getChildAt(position) as? ViewGroup
            previousView?.findViewById<ImageView>(R.id.iv_indicator)?.visibility = View.INVISIBLE
        }
    }

    private fun showPointVeiw(day: Day) {
        val totalEmissions = day.productEmissions + day.transportEmissions
        val resultText = """
            현재 총 배출량: $totalEmissions
            전기 탄소 배출량: ${day.productEmissions}
            이동경로 탄소 배출량: ${day.transportEmissions}
        """.trimIndent()
        binding.tvDayResult.text = resultText
    }

    private fun showPointsPopup(day: Day) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Date: ${day.date}")

        val productLabel = TextView(requireContext()).apply { text = "전기 탄소 배출량" }
        val transportLabel = TextView(requireContext()).apply { text = "이동경로 탄소 배출량" }
        val productInput = EditText(requireContext()).apply { setText(day.productEmissions.toString()) }
        val transportInput = EditText(requireContext()).apply { setText(day.transportEmissions.toString()) }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(productLabel)
            addView(productInput)
            addView(transportLabel)
            addView(transportInput)
        }
        builder.setView(layout)

        builder.setPositiveButton("수정") { _, _ ->
            val newProductEmissions = productInput.text.toString().toIntOrNull() ?: day.productEmissions
            val newTransportEmissions = transportInput.text.toString().toIntOrNull() ?: day.transportEmissions
            day.productEmissions = newProductEmissions
            day.transportEmissions = newTransportEmissions
            gridAdapter.notifyDataSetChanged()
        }

        builder.setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ViewHolder(val tvItemGridView: TextView, val tvPoints: TextView, val ivIndicator: ImageView)
}