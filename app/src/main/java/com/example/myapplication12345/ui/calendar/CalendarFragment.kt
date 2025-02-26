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
import com.example.myapplication12345.databinding.FragmentCalendarBinding
import java.io.File
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        calendarViewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
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
            updateTestMapButtonState()
        }

        binding.btnNextMonth.setOnClickListener {
            hideIndicatorAtPosition(selectedPosition)
            selectedPosition = -1
            mCal.add(Calendar.MONTH, 1)
            updateCalendar()
            updateTestMapButtonState()
        }

        binding.btnOpenMap.setOnClickListener {
            if (selectedPosition != -1) {
                val selectedDay = dayList[selectedPosition]
                val selectedDate = formatDateForFile(selectedDay.date)
                val intent = Intent(requireContext(), MapActivity::class.java).apply {
                    putExtra("selectedDate", selectedDate)
                }
                startActivity(intent)
            }
        }

        binding.btnTestMap.setOnClickListener {
            if (selectedPosition != -1) {
                val selectedDay = dayList[selectedPosition]
                val selectedDateStr = formatDateForFile(selectedDay.date)
                val selectedDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(selectedDateStr)
                if (selectedDate != null) {
                    MapFragment.createTestCsvFile(requireContext(), selectedDate)
                    updateTestMapButtonState()
                    binding.btnOpenMap.isEnabled = true
                }
            }
        }

        updateTestMapButtonState()
        return root
    }

    private fun updateDateText() {
        val yearFormat = SimpleDateFormat("yyyy", Locale.KOREA)
        val monthFormat = SimpleDateFormat("MM", Locale.KOREA)
        val dayFormat = SimpleDateFormat("dd", Locale.KOREA)
        val selectedDay = if (selectedPosition != -1 && dayList[selectedPosition].productEmissions >= 0) {
            dayList[selectedPosition].date
        } else {
            "01" // 기본값
        }
        mCal.set(Calendar.DAY_OF_MONTH, selectedDay.toInt())
        val year = yearFormat.format(mCal.time)
        val month = monthFormat.format(mCal.time)
        val day = dayFormat.format(mCal.time)
        binding.tvDate.text = "${year}년 ${month}월 ${day}일"
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

        for (i in 1..mCal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            dayList.add(Day(i.toString(), 0, 0))
        }
    }

    private fun updateCalendar() {
        updateDateText()
        initCalendar()
        gridAdapter.notifyDataSetChanged()
    }

    data class Day(val date: String, var productEmissions: Int, var transportEmissions: Int)

    private inner class GridAdapter(private val list: List<Day>) : BaseAdapter() {
        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Day = list[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_calendar_gridview, parent, false)
            val holder = if (convertView == null) {
                ViewHolder(view.findViewById(R.id.tv_item_gridview), view.findViewById(R.id.tv_points), view.findViewById(R.id.iv_indicator)).also {
                    view.tag = it
                }
            } else {
                view.tag as ViewHolder
            }

            val day = getItem(position)
            val totalEmissions = day.productEmissions + day.transportEmissions
            holder.tvItemGridView.text = day.date
            holder.tvPoints.text = if (totalEmissions > 0) totalEmissions.toString() else ""

            val today = Calendar.getInstance()
            if (day.date.isNotEmpty() && day.productEmissions >= 0) {
                val tempCal = mCal.clone() as Calendar
                tempCal.set(Calendar.DAY_OF_MONTH, day.date.toInt())
                val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)

                if (day.productEmissions == 0 && day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                    mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                    holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
                } else if (dayOfWeek == Calendar.SATURDAY) {
                    holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                } else if (dayOfWeek == Calendar.SUNDAY) {
                    holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                } else {
                    holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                }
            } else if (day.productEmissions == -1) {
                when (position % 7) {
                    0 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                    6 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                    else -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                }
            }

            view.setOnClickListener {
                if (day.date.isNotEmpty() && day.productEmissions >= 0) {
                    hideIndicatorAtPosition(selectedPosition)
                    holder.ivIndicator.visibility = View.VISIBLE
                    selectedPosition = position
                    showPointVeiw(day)
                    val selectedDate = formatDateForFile(day.date)
                    binding.btnOpenMap.isEnabled = checkCsvFileExists(selectedDate)
                    updateTestMapButtonState()
                }
            }

            view.setOnLongClickListener {
                if (day.date.isNotEmpty() && day.productEmissions >= 0) {
                    showPointsPopup(day)
                }
                true
            }

            return view
        }
    }

    private fun formatDateForFile(day: String): String {
        val year = mCal.get(Calendar.YEAR)
        val month = String.format("%02d", mCal.get(Calendar.MONTH) + 1)
        val dayFormatted = String.format("%02d", day.toInt())
        return "$year$month$dayFormatted"
    }

    private fun checkCsvFileExists(date: String): Boolean {
        val fileName = "${date}_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")
        return file.exists()
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

    private fun updateTestMapButtonState() {
        if (selectedPosition != -1) {
            val selectedDay = dayList[selectedPosition]
            val selectedDate = formatDateForFile(selectedDay.date)
            binding.btnTestMap.isEnabled = !checkCsvFileExists(selectedDate)
        } else {
            binding.btnTestMap.isEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ViewHolder(val tvItemGridView: TextView, val tvPoints: TextView, val ivIndicator: ImageView)
}