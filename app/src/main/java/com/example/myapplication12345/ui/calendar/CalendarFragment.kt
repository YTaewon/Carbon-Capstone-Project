package com.example.myapplication12345.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
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

    // 프래그먼트 뷰 생성
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ServerManager 초기화
        serverManager = ServerManager(requireContext())
        // ViewModel 초기화
        calendarViewModel = activityViewModels<CalendarViewModel> {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return CalendarViewModel(serverManager) as T
                }
            }
        }.value
        // View binding 설정
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 날짜 텍스트 업데이트
        updateDateText()
        // 캘린더 초기화
        initCalendar()

        // 그리드뷰 어댑터 설정
        gridAdapter = GridAdapter(dayList)
        binding.gridview.adapter = gridAdapter

        // 이전 달 버튼 클릭 리스너
        binding.btnPrevMonth.setOnClickListener {
            selectedPosition = -1
            mCal.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        // 다음 달 버튼 클릭 리스너
        binding.btnNextMonth.setOnClickListener {
            selectedPosition = -1
            mCal.add(Calendar.MONTH, 1)
            updateCalendar()
        }
        return root
    }

    // 날짜 텍스트뷰 업데이트
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

    // 캘린더 초기화
    private fun initCalendar() {
        dayList.clear()
        val daysOfWeek = arrayOf("일", "월", "화", "수", "목", "금", "토")
        // 요일 헤더 추가
        for (day in daysOfWeek) {
            dayList.add(Day(day, -1, -1, -1))
        }

        // 첫 번째 날짜 설정
        mCal.set(Calendar.DAY_OF_MONTH, 1)
        val dayNum = mCal.get(Calendar.DAY_OF_WEEK)
        // 빈 날짜 추가
        repeat(dayNum - 1) {
            dayList.add(Day("", 0, 0, 0))
        }

        // 해당 월의 모든 날짜 추가
        val daysInMonth = mCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            val dayCal = Calendar.getInstance().apply {
                set(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH), i)
            }
            dayList.add(Day(i.toString(), 0, 0, 0))
            // 각 날짜의 배출량 데이터 가져오기
            fetchEmissionsForDay(dayCal, i - 1 + dayNum - 1 + daysOfWeek.size)
        }
    }

    // 특정 날짜의 배출량 데이터 가져오기
    private fun fetchEmissionsForDay(date: Calendar, position: Int, retryCount: Int = 0) {
        // 제품 배출량 가져오기
        serverManager.getScoresForDate(date) { productScore ->
            // 이동경로 배출량 가져오기
            serverManager.getTransportEmissionsForDate(date) { transportScore ->
                // 사물 배출량 가져오기
                serverManager.getObjectEmissionsForDate(date) { objectScore ->
                    activity?.runOnUiThread {
                        if (position < dayList.size) {
                            val day = dayList[position]
                            day.productEmissions = productScore
                            day.transportEmissions = transportScore
                            day.objectEmissions = objectScore
                            Timber.d("Updated day $position with productEmissions: $productScore, transportEmissions: $transportScore, objectEmissions: $objectScore")
                            gridAdapter.notifyDataSetChanged()
                            // 오늘 날짜가 선택된 경우 포인트 뷰 업데이트
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
                    if ((productScore == 0 || transportScore == 0 || objectScore == 0) && retryCount < 2) {
                        Timber.w("Zero emissions for ${getFormattedDate(date)}, retrying (${retryCount + 1}/2)")
                        fetchEmissionsForDay(date, position, retryCount + 1)
                    }
                }
            }
        }
    }

    // 날짜 포맷팅 (YYYY-MM-DD)
    private fun getFormattedDate(date: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date.time)
    }

    // 캘린더 업데이트
    private fun updateCalendar() {
        updateDateText()
        initCalendar()
        gridAdapter.notifyDataSetChanged()
    }

    // 날짜 데이터 클래스
    data class Day(
        var date: String,
        var productEmissions: Int,
        var transportEmissions: Int,
        var objectEmissions: Int = 0 // 사물 배출량 필드 추가
    )

    // 그리드뷰 어댑터 클래스
    private inner class GridAdapter(private val list: List<Day>) : BaseAdapter() {
        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Day = list[position]
        override fun getItemId(position: Int): Long = position.toLong()

        // 그리드뷰 아이템 뷰 생성
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
            // 총 배출량 계산
            val totalEmissions = day.productEmissions + day.transportEmissions + day.objectEmissions
            holder.tvItemGridView.text = day.date
            holder.tvPoints.text = if (totalEmissions > 0) totalEmissions.toString() else ""

            val today = Calendar.getInstance()
            holder.tvItemGridView.isSelected = false
            holder.tvItemGridView.isPressed = false

            // 날짜가 유효한 경우 스타일 적용
            if (day.date.isNotEmpty() && day.productEmissions >= 0 && day.date.all { it.isDigit() }) {
                val tempCal = mCal.clone() as Calendar
                tempCal.set(Calendar.DAY_OF_MONTH, day.date.toInt())
                val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)

                val isToday = day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                        mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                if (isToday) {
                    holder.tvItemGridView.isSelected = true
                }

                if (position == selectedPosition) {
                    holder.tvItemGridView.isPressed = true
                }

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
                // 요일 헤더 스타일
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

            // 날짜 클릭 리스너
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

            // 날짜 롱클릭 리스너 (팝업 표시)
            view.setOnLongClickListener {
                if (day.date.isNotEmpty() && day.productEmissions >= 0 && day.date.all { it.isDigit() }) {
                    showPointsPopup(day)
                }
                true
            }

            return view
        }
    }

    // 선택된 날짜의 배출량 정보를 표시
    private fun showPointVeiw(day: Day) {
        val totalEmissions = day.productEmissions + day.transportEmissions + day.objectEmissions
        val resultText = """
            현재 총 배출량: $totalEmissions
            전기 탄소 배출량: ${day.productEmissions}
            이동경로 탄소 배출량: ${day.transportEmissions}
            사물 탄소 배출량: ${day.objectEmissions}
        """.trimIndent()
        binding.tvDayResult.text = resultText
        Timber.d("Displayed point view for date ${day.date}: productEmissions=${day.productEmissions}, transportEmissions=${day.transportEmissions}, objectEmissions=${day.objectEmissions}")
    }

    // 배출량 수정 팝업 표시
    private fun showPointsPopup(day: Day) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Date: ${day.date}")

        // 입력 필드 및 라벨 생성
        val productLabel = TextView(requireContext()).apply { text = "전기 탄소 배출량" }
        val transportLabel = TextView(requireContext()).apply { text = "이동경로 탄소 배출량" }
        val objectLabel = TextView(requireContext()).apply { text = "사물 탄소 배출량" }
        val productInput = EditText(requireContext()).apply { setText(day.productEmissions.toString()) }
        val transportInput = EditText(requireContext()).apply { setText(day.transportEmissions.toString()) }
        val objectInput = EditText(requireContext()).apply { setText(day.objectEmissions.toString()) }

        // 레이아웃 구성
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(productLabel)
            addView(productInput)
            addView(transportLabel)
            addView(transportInput)
            addView(objectLabel)
            addView(objectInput)
        }
        builder.setView(layout)

        // 수정 버튼 클릭 리스너
        builder.setPositiveButton("수정") { _, _ ->
            val newProductEmissions = productInput.text.toString().toIntOrNull() ?: day.productEmissions
            val newTransportEmissions = transportInput.text.toString().toIntOrNull() ?: day.transportEmissions
            val newObjectEmissions = objectInput.text.toString().toIntOrNull() ?: day.objectEmissions
            day.productEmissions = newProductEmissions
            day.transportEmissions = newTransportEmissions
            day.objectEmissions = newObjectEmissions
            val dayCal = Calendar.getInstance().apply {
                set(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH), day.date.toInt())
            }
            // 제품 배출량 업데이트
            calendarViewModel.updateProductEmissions(dayCal, newProductEmissions) { success ->
                activity?.runOnUiThread {
                    if (success) {
                        // 이동경로 배출량 업데이트
                        calendarViewModel.updateTransportEmissions(dayCal, newTransportEmissions) { transportSuccess ->
                            if (transportSuccess) {
                                // 사물 배출량 업데이트
                                calendarViewModel.updateObjectEmissions(dayCal, newObjectEmissions) { objectSuccess ->
                                    if (objectSuccess) {
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
                                        Toast.makeText(requireContext(), "사물 배출량 업데이트 실패", Toast.LENGTH_SHORT).show()
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

        // 취소 버튼
        builder.setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    // 월 포맷팅 (YYYY-MM)
    private fun getFormattedMonth(date: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return sdf.format(date.time)
    }

    // 프래그먼트 뷰 파괴 시 리소스 정리
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ViewHolder 데이터 클래스
    private data class ViewHolder(val tvItemGridView: TextView, val tvPoints: TextView)
}