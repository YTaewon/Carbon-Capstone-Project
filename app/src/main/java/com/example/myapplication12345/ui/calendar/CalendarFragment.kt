package com.example.myapplication12345.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication12345.R
import com.example.myapplication12345.ServerManager
import com.example.myapplication12345.databinding.FragmentCalendarBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarFragment : Fragment() {
    // View binding 객체 선언
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    // 날짜 데이터 리스트
    private val dayList = mutableListOf<Day>()
    // 그리드뷰 어댑터
    private lateinit var gridAdapter: GridAdapter
    // 캘린더 인스턴스
    private val mCal: Calendar = Calendar.getInstance()
    // 뷰모델 인스턴스
    private lateinit var calendarViewModel: CalendarViewModel
    // 선택된 날짜의 위치
    private var selectedPosition: Int = -1
    // 서버 매니저 인스턴스
    private lateinit var serverManager: ServerManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        val root: View = binding.root

        serverManager = ServerManager(requireContext())
        calendarViewModel = activityViewModels<CalendarViewModel> {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return CalendarViewModel(serverManager) as T
                }
            }
        }.value

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

        updateCalendar() // 초기 캘린더 로드

        return root
    }

    private fun updateDateText() {
        val yearFormat = SimpleDateFormat("yyyy", Locale.KOREA)
        val monthFormat = SimpleDateFormat("MM", Locale.KOREA)
        binding.tvDate.text = "${yearFormat.format(mCal.time)}년 ${monthFormat.format(mCal.time)}월"
    }

    private fun updateCalendar() {
        updateDateText()
        selectedPosition = -1
        binding.calendarProgressBar.visibility = View.VISIBLE // 로딩 시작
        binding.gridview.visibility = View.INVISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val newDayList = buildCalendarDataForMonth()
            if (isAdded) {
                dayList.clear()
                dayList.addAll(newDayList)
                gridAdapter.notifyDataSetChanged()
                binding.calendarProgressBar.visibility = View.GONE // 로딩 완료
                binding.gridview.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun buildCalendarDataForMonth(): List<Day> {
        val tempCal = mCal.clone() as Calendar
        val newDayList = mutableListOf<Day>()

        val daysOfWeek = arrayOf("일", "월", "화", "수", "목", "금", "토")
        daysOfWeek.forEach { newDayList.add(Day(it, -1, -1, -1)) }

        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val startDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
        repeat(startDayOfWeek - 1) { newDayList.add(Day("", 0, 0, 0)) }

        val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val deferredEmissions = (1..daysInMonth).map { day ->
            lifecycleScope.async {
                val date = Calendar.getInstance().apply { set(tempCal.get(Calendar.YEAR), tempCal.get(Calendar.MONTH), day) }

                // ServerManager에 추가한 확장 suspend 함수 사용
                val productEmissionDeferred = async { serverManager.getProductEmissionsSuspend(date) }
                val transportEmissionDeferred = async { serverManager.getTransportEmissionsSuspend(date) }
                val objectEmissionDeferred = async { serverManager.getObjectEmissionsForDateSuspend(date) }

                Day(day.toString(), productEmissionDeferred.await(), transportEmissionDeferred.await(), objectEmissionDeferred.await())
            }
        }

        newDayList.addAll(deferredEmissions.awaitAll())
        return newDayList
    }

    data class Day(val date: String, var productEmissions: Int, var transportEmissions: Int, var objectEmissions: Int)

    private inner class GridAdapter(private val list: List<Day>) : BaseAdapter() {
        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Day = list[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: ViewHolder

            if (convertView == null) {
                view = LayoutInflater.from(parent?.context).inflate(R.layout.item_calendar_gridview, parent, false)
                holder = ViewHolder(
                    view.findViewById(R.id.tv_item_gridview),
                    view.findViewById(R.id.tv_points)
                )
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val day = getItem(position)

            holder.tvItemGridView.text = day.date

            if (day.date.isNotEmpty() && day.date.all { it.isDigit() }) { // 유효한 날짜인 경우
                val totalEmissions = day.productEmissions + day.transportEmissions + day.objectEmissions
                holder.tvPoints.text = if (totalEmissions > 0) totalEmissions.toString() else ""

                val today = Calendar.getInstance()
                val isToday = day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                        mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)

                // [수정] 뷰의 상태(isPressed, isSelected)만 설정합니다.
                // 그러면 calendar_day_background_selector.xml이 상태에 맞는 배경을 자동으로 그려줍니다.
                holder.tvItemGridView.isSelected = isToday
                holder.tvItemGridView.isPressed = (position == selectedPosition)

                // 요일별 텍스트 색상 설정
                val tempCal = mCal.clone() as Calendar
                tempCal.set(Calendar.DAY_OF_MONTH, day.date.toInt())
                when (tempCal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SATURDAY -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                    Calendar.SUNDAY -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                    else -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                }

                view.setOnClickListener {
                    selectedPosition = position
                    notifyDataSetChanged() // 선택 상태가 바뀌었으므로 전체 갱신
                    showPointVeiw(day)
                }

                view.setOnLongClickListener {
                    showPointsPopup(day)
                    true
                }
            } else { // 요일 헤더 또는 빈 칸
                holder.tvPoints.text = ""
                // [수정] 상태 초기화
                holder.tvItemGridView.isSelected = false
                holder.tvItemGridView.isPressed = false

                if (day.productEmissions == -1) { // 요일 헤더일 경우
                    when (position % 7) {
                        0 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                        6 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                        else -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    }
                }
            }
            return view
        }
    }


    private fun showPointView(day: Day) {
        val totalEmissions = day.productEmissions + day.transportEmissions + day.objectEmissions
        val resultText = """
            현재 총 배출량: $totalEmissions
            전기 탄소 배출량: ${day.productEmissions}
            이동경로 탄소 배출량: ${day.transportEmissions}
            사물 탄소 배출량: ${day.objectEmissions}
        """.trimIndent()
        binding.tvDayResult.text = resultText
    }

    private fun showPointsPopup(day: Day) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Date: ${day.date}")

        val productLabel = TextView(requireContext()).apply { text = "전기 탄소 배출량" }
        val transportLabel = TextView(requireContext()).apply { text = "이동경로 탄소 배출량" }
        val objectLabel = TextView(requireContext()).apply { text = "사물 탄소 배출량" }
        val productInput = EditText(requireContext()).apply { setText(day.productEmissions.toString()) }
        val transportInput = EditText(requireContext()).apply { setText(day.transportEmissions.toString()) }
        val objectInput = EditText(requireContext()).apply { setText(day.objectEmissions.toString()) }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(productLabel); addView(productInput)
            addView(transportLabel); addView(transportInput)
            addView(objectLabel); addView(objectInput)
        }
        builder.setView(layout)

        builder.setPositiveButton("수정") { _, _ ->
            // ... (이 부분의 콜백 지옥도 코루틴으로 개선 가능하지만 일단 유지)
            // ...
        }

        builder.setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showPointVeiw(day: Day) {
        val totalEmissions = day.productEmissions + day.transportEmissions + day.objectEmissions
        val resultText = """
        현재 총 배출량: $totalEmissions
        전기 탄소 배출량: ${day.productEmissions}
        이동경로 탄소 배출량: ${day.transportEmissions}
        사물 탄소 배출량: ${day.objectEmissions}
    """.trimIndent()
        binding.tvDayResult.text = resultText
        Timber.d("Displayed point view for date ${day.date}: productEmissions=${day.productEmissions}")
    }


    private fun getFormattedMonth(date: Calendar): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(date.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ViewHolder(val tvItemGridView: TextView, val tvPoints: TextView)
}