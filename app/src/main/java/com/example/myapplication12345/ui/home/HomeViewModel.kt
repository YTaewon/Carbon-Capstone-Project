package com.example.myapplication12345.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    // 기존 인사말 텍스트용 LiveData
    private val _text = MutableLiveData<String>().apply {
        value = "메인화면"
    }
    val text: LiveData<String> = _text

    // 20개의 탄소 절약 팁 리스트
    private val carbonSavingTips = listOf(
        "자전거를 타고 출근 해보 세요. \n교통비를 절약하고 탄소 배출도 줄일 수 있습니다.",
        "LED 전구로 교체 하세요. \n전기 사용량을 줄일 수 있습니다.",
        "사용 하지 않는 전자 제품의 플러그를 뽑아보아요. \n 대기 전력을 줄이고 전기 세도 아낄수 있어요.",
        "대중 교통을 이용 하세요. \n개인 차량 사용보다 탄소 배출이 적습니다.",
        "일회용 플라스틱 대신 \n재사용 가능한 물병을 사용하세요.",
        "가까운 거리는 걸서 이동해요. \n건강도 챙기고 탄소도 발생도 줄일 수 있어요.",
        "고기 소비를 줄이고 \n채식 위주의 식단을 시도 해보세요.",
        "집에서 음식물 쓰레기를 줄이기 위해 \n필요한 만큼만 요리하세요.",
        "에어컨 대신 선풍기를 사용해 보아요. \n전기 사용량을 줄일수 있어요.",
        "세탁 시 찬물을 사용해 보아요. \n에너지 소비를 줄일 수 있습니다.",
        "재활용을 철저히 분리배출하여 \n자원을 아껴보세요.",
        "전자책을 이용해 \n종이책 사용을 줄여보세요.",
        "샤워 시간을 5분 줄이면 \n물과 에너지를 절약할 수 있어요.",
        "집에 단열재를 추가해 냉난방 효율을 높이 세요.\n집 내부가 쾨적 해지고 돈을 아낄수 있어요",
        "지역 농산물을 구매해 \n운송으로 인한 탄소 배출을 줄이세요.",
        "불필요한 조명을 끄고 \n자연광을 활용해보세요.",
        "에너지 효율이 높은 \n가전제품을 선택하세요.",
        "나무를 심어 \n탄소 흡수를 도와보세요.",
        "카풀을 이용해 \n차량 배출가스를 줄이세요.",
        "중고 제품을 구매해 \n물건 생산으로 인한 탄소를 줄이세요."
    )

    // 현재 팁을 관리하는 LiveData
    private val _currentTip = MutableLiveData<String>()
    val currentTip: LiveData<String> get() = _currentTip

    init {
        // 초기 팁 설정
        showRandomTip()
    }

    // 랜덤 팁을 표시하는 함수
    fun showRandomTip() {
        _currentTip.value = carbonSavingTips.random()
    }
}