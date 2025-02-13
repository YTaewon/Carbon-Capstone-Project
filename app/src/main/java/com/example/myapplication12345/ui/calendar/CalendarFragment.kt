package com.example.myapplication12345.ui.calendar

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication12345.R
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        calendarViewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 날짜 설정 및 UI 초기화
        updateDateText()
        initCalendar()

        gridAdapter = GridAdapter(dayList)
        binding.gridview.adapter = gridAdapter

        binding.btnPrevMonth.setOnClickListener {
            mCal.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            mCal.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        return root
    }

    // 날짜 텍스트 업데이트
    private fun updateDateText() {
        val curYearFormat = SimpleDateFormat("yyyy", Locale.KOREA)
        val curMonthFormat = SimpleDateFormat("MM", Locale.KOREA)
        binding.tvDate.text = getString(R.string.date_format, curYearFormat.format(mCal.time), curMonthFormat.format(mCal.time))
    }

    // 달력 데이터 초기화
    private fun initCalendar() {
        dayList.clear()
        val daysOfWeek = arrayOf("일", "월", "화", "수", "목", "금", "토")
        for (day in daysOfWeek) {
            dayList.add(Day(day, -1))
        }

        mCal.set(Calendar.DAY_OF_MONTH, 1)
        val dayNum = mCal.get(Calendar.DAY_OF_WEEK)
        repeat(dayNum - 1) {
            dayList.add(Day("", 0))
        }

        for (i in 1..mCal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            dayList.add(Day(i.toString(), 0))
        }
    }

    // 달력 업데이트
    private fun updateCalendar() {
        updateDateText()
        initCalendar()
        gridAdapter.notifyDataSetChanged()
    }

    // 날짜 데이터 클래스
    data class Day(val date: String, var points: Int)

    // GridView 어댑터
    private inner class GridAdapter(private val list: List<Day>) : BaseAdapter() {

        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Day = list[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_calendar_gridview, parent, false)
            val holder = if (convertView == null) {
                ViewHolder(view.findViewById(R.id.tv_item_gridview), view.findViewById(R.id.tv_points)).also {
                    view.tag = it
                }
            } else {
                view.tag as ViewHolder
            }

            val day = getItem(position)
            holder.tvItemGridView.text = day.date
            holder.tvPoints.text = if (day.points > 0) "${day.points}" else ""

            val today = Calendar.getInstance()
            if (day.points == 0 && day.date.isNotEmpty() && day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
            } else if (day.points == -1) {
                when (position % 7) {
                    0 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                    6 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                    else -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                }
            } else {
                holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            }

            view.setOnLongClickListener {
                if (day.date.isNotEmpty() && day.points >= 0) {
                    showAddPointsDialog(day)
                }
                true
            }

            return view
        }
    }

    // 포인트 추가 다이얼로그
    private fun showAddPointsDialog(day: Day) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Add Points for ${day.date}")

        val input = EditText(requireContext()).apply {
            hint = "Enter points"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        builder.setView(input)

        builder.setPositiveButton("Add") { _, _ ->
            val points = input.text.toString().toIntOrNull() ?: 0
            day.points += points
            gridAdapter.notifyDataSetChanged()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ViewHolder(val tvItemGridView: TextView, val tvPoints: TextView)
}
