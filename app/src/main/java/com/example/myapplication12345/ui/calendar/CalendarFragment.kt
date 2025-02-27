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

        // 날짜 설정 및 UI 초기화
        updateDateText()
        initCalendar()

        gridAdapter = GridAdapter(dayList)
        binding.gridview.adapter = gridAdapter

        binding.btnPrevMonth.setOnClickListener {
            //날짜 선택 초기화
            hideIndicatorAtPosition(selectedPosition)
            selectedPosition = -1;
            //이전 달
            mCal.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            //날짜 선택 초기화
            hideIndicatorAtPosition(selectedPosition)
            selectedPosition = -1;
            //다음 달
            mCal.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        // btn_open_map 클릭 시 동작 (예: MapFragment로 이동)
        binding.btnOpenMap.setOnClickListener {
            // TODO: MapFragment로 이동하는 로직 추가
            // 예: findNavController().navigate(R.id.action_calendarFragment_to_mapFragment)
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

    // 달력 업데이트
    private fun updateCalendar() {
        updateDateText()
        initCalendar()
        gridAdapter.notifyDataSetChanged()
    }

    // 날짜 데이터 클래스
    data class Day(val date: String, var productEmissions: Int, var transportEmissions: Int)

    // GridView 어댑터
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
            if (day.productEmissions == 0 && day.date.isNotEmpty() && day.date.toInt() == today.get(Calendar.DAY_OF_MONTH) &&
                mCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                mCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
            } else if (day.productEmissions == -1) {
                when (position % 7) {
                    0 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                    6 -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                    else -> holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                }
            } else {
                holder.tvItemGridView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            }

            // 날짜 클릭 이벤트 추가
            view.setOnClickListener {
                if (day.date.isNotEmpty() && day.productEmissions >= 0) {
                    hideIndicatorAtPosition(selectedPosition) // 이전 선택된 위치의 이미지를 숨김
                    holder.ivIndicator.visibility = View.VISIBLE
                    selectedPosition = position // 현재 선택된 위치 업데이트
                    showPointVeiw(day)
                    // CSV 파일 존재 여부 확인 및 버튼 상태 업데이트
                    val selectedDate = formatDateForFile(day.date)
                    if (checkCsvFileExists(selectedDate)) {
                        binding.btnOpenMap.isEnabled = true
                    } else {
                        binding.btnOpenMap.isEnabled = false
                    }
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

    // 선택된 날짜를 "yyyyMMdd" 형식으로 변환
    private fun formatDateForFile(day: String): String {
        val year = mCal.get(Calendar.YEAR)
        val month = String.format("%02d", mCal.get(Calendar.MONTH) + 1) // 0부터 시작하므로 +1
        val dayFormatted = String.format("%02d", day.toInt())
        return "$year$month$dayFormatted"
    }

    // CSV 파일 존재 여부 확인
    private fun checkCsvFileExists(date: String): Boolean {
        val fileName = date + "_predictions.csv"
        val file = File(requireContext().getExternalFilesDir(null), "SensorData/$fileName")
        return file.exists()
    }

    private fun hideIndicatorAtPosition(position: Int) {
        if (position >= 0) {
            val previousView = binding.gridview.getChildAt(position) as? ViewGroup
            previousView?.findViewById<ImageView>(R.id.iv_indicator)?.visibility = View.INVISIBLE
        }
    }
    
    //출력
    private fun showPointVeiw(day: Day) {
        val totalEmissions = day.productEmissions + day.transportEmissions
        val resultText = """
        현재 총 배출량: $totalEmissions
        전기 탄소 배출량: ${day.productEmissions}
        이동경로 탄소 배출량: ${day.transportEmissions}
    """.trimIndent()
        binding.tvDayResult.setText(resultText)
    }

    // 포인트 추가 다이얼로그
    private fun showPointsPopup(day: Day) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Date: ${day.date}")

        // Labels for emissions
        val productLabel = TextView(requireContext()).apply {
            text = "전기 탄소 배출량"
        }

        val transportLabel = TextView(requireContext()).apply {
            text = "이동경로 탄소 배출량"
        }

        // EditTexts for emissions
        val productInput = EditText(requireContext()).apply {
            setText(day.productEmissions.toString())
        }

        val transportInput = EditText(requireContext()).apply {
            setText(day.transportEmissions.toString())
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(productLabel)
            addView(productInput)
            addView(transportLabel)
            addView(transportInput)
        }
        builder.setView(layout)

        // "수정" 버튼으로 포인트를 업데이트
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
