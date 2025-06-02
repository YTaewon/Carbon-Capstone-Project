package com.example.myapplication12345.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.myapplication12345.R
import com.example.myapplication12345.ServerManager
import com.example.myapplication12345.databinding.FragmentCalendarBinding
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val dayList = mutableListOf<Day>()
    private lateinit var gridAdapter: GridAdapter
    private val mCal: Calendar = Calendar.getInstance()
    private lateinit var calendarViewModel: CalendarViewModel
    private var selectedPosition: Int = -1
    private lateinit var serverManager: ServerManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        serverManager = ServerManager(requireContext())
        calendarViewModel = activityViewModels<CalendarViewModel> {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return CalendarViewModel(serverManager) as T
                }
            }
        }.value
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        val root: View = binding.root

        updateDateText()
        initCalendar()

        gridAdapter = GridAdapter(dayList)
        binding.gridview.adapter = gridAdapter

        binding.btnPrevMonth.setOnClickListener {
            selectedPosition = -1
            mCal.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
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
        binding.tvDate.text = buildString {
            append(year)
            append("년 ")
            append(month)
            append("월")
        }
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
            fetchEmissionsForDay(dayCal, i - 1 + dayNum - 1 + daysOfWeek.size)
        }
    }

    private fun fetchEmissionsForDay(date: Calendar, position: Int, retryCount: Int = 0) {
        // 제품 탄소 배출량 가져오기
        serverManager.getScoresForDate(date) { productScore ->
            // 이동경로 탄소 배출량 가져오기
            serverManager.getTransportEmissionsForDate(date) { transportScore ->
                activity?.runOnUiThread {
                    if (position < dayList.size) {
                        val day = dayList[position]
                        day.productEmissions = productScore
                        day.transportEmissions = transportScore
                        Timber.d("Updated day $position with productEmissions: $productScore, transportEmissions: $transportScore")
                        gridAdapter.notifyDataSetChanged()
                        // 선택된 날짜에 대해 UI 업데이트
                        val today = Calendar.getInstance()
                        if (position == selectedPosition &&
                            day.date.toIntOrNull() == today.get(Calendar.DAY_OF_MONTH) &&
                            mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                        ) {
                            showPointVeiw(day)
                        }
                    } else {
                        Timber.w("Invalid position $position for dayList size ${dayList.size}")
                    }
                }
                // 배출량이 0인 경우 최대 2회 재시도
                if ((productScore == 0 || transportScore == 0) && retryCount < 2) {
                    Timber.w("Zero emissions for ${getFormattedDate(date)}, retrying (${retryCount + 1}/2)")
                    fetchEmissionsForDay(date, position, retryCount + 1)
                }
            }
        }
    }

    private fun getFormattedDate(date: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date.time)
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
                    view.findViewById(R.id.tv_points)
                ).also { view.tag = it }
            } else {
                view.tag as ViewHolder
            }

            val day = getItem(position)
            val totalEmissions = day.productEmissions + day.transportEmissions
            holder.tvItemGridView.text = day.date
            holder.tvPoints.text = if (totalEmissions > 0) totalEmissions.toString() else ""

            val today = Calendar.getInstance()
            holder.tvItemGridView.isSelected = false
            holder.tvItemGridView.isPressed = false

            if (day.date.isNotEmpty() && day.productEmissions >= 0 && day.date.all { it.isDigit() }) {
                val tempCal = mCal.clone() as Calendar
                tempCal.set(Calendar.DAY_OF_MONTH, day.date.toInt())
                val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)

                // 오늘 날짜 강조
                val isToday = day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                        mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                if (isToday) {
                    holder.tvItemGridView.isSelected = true
                }

                // 선택된 날짜 강조
                if (position == selectedPosition) {
                    holder.tvItemGridView.isPressed = true
                }

                // 요일별 색상
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
                    selectedPosition = position
                    holder.tvItemGridView.isPressed = true
                    val isTodayInClick = day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                            mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    if (isTodayInClick) {
                        holder.tvItemGridView.isSelected = true
                    }
                    showPointVeiw(day)
                    gridAdapter.notifyDataSetChanged()
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

    private fun showPointVeiw(day: Day) {
        val totalEmissions = day.productEmissions + day.transportEmissions
        val resultText = """
            현재 총 배출량: $totalEmissions
            전기 탄소 배출량: ${day.productEmissions}
            이동경로 탄소 배출량: ${day.transportEmissions}
        """.trimIndent()
        binding.tvDayResult.text = resultText
        Timber.d("Displayed point view for date ${day.date}: productEmissions=${day.productEmissions}")
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
            val dayCal = Calendar.getInstance().apply {
                set(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH), day.date.toInt())
            }
            calendarViewModel.updateProductEmissions(dayCal, newProductEmissions) { success ->
                activity?.runOnUiThread {
                    if (success) {
                        calendarViewModel.updateTransportEmissions(dayCal, newTransportEmissions) { transportSuccess ->
                            if (transportSuccess) {
                                gridAdapter.notifyDataSetChanged()
                                showPointVeiw(day)
                                // 월별 포인트 업데이트
                                serverManager.updateMonthlyPointsForMonth(getFormattedMonth(dayCal)) { updateSuccess ->
                                    if (updateSuccess) {
                                        Timber.d("월별 포인트 업데이트 성공: ${getFormattedMonth(dayCal)}")
                                    } else {
                                        Toast.makeText(requireContext(), "월별 포인트 업데이트 실패", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(requireContext(), "이동경로 배출량 업데이트 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), "배출량 업데이트 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        builder.setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    // Helper method to get formatted month (yyyy-MM)
    private fun getFormattedMonth(date: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return sdf.format(date.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ViewHolder(val tvItemGridView: TextView, val tvPoints: TextView)
}