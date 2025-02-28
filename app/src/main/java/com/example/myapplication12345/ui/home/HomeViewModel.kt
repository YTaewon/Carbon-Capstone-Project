package com.example.myapplication12345.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    // 기존 인사말 텍스트용 LiveData
    private val _text = MutableLiveData<String>().apply {
        value = "메인화면(홈화면)"
    }
    val text: LiveData<String> = _text

    // 20개의 탄소 절약 팁 리스트
    private val carbonSavingTips = listOf(
        "자전거를 타고 출근해보세요. 교통비도 절약되고 탄소 배출도 줄일 수 있습니다.",
        "LED 전구로 교체하면 전기 사용량을 줄일 수 있습니다.",
        "사용하지 않는 전자제품의 플러그를 뽑아 대기 전력을 줄이세요.",
        "대중교통을 이용하면 개인 차량 사용보다 탄소 배출이 적습니다.",
        "일회용 플라스틱 대신 재사용 가능한 물병을 사용하세요.",
        "가까운 거리는 걸어다니며 건강도 챙기고 탄소도 줄이세요.",
        "고기 소비를 줄이고 채식 위주의 식단을 시도해보세요.",
        "집에서 음식물 쓰레기를 줄이기 위해 필요한 만큼만 요리하세요.",
        "에어컨 대신 선풍기를 사용해 전기 사용을 줄이세요.",
        "세탁 시 찬물 사용으로 에너지 소비를 줄일 수 있습니다.",
        "재활용을 철저히 분리배출하여 자원을 아껴보세요.",
        "전자책을 이용해 종이책 사용을 줄여보세요.",
        "샤워 시간을 5분 줄이면 물과 에너지를 절약할 수 있습니다.",
        "집에 단열재를 추가해 냉난방 효율을 높이세요.",
        "지역 농산물을 구매해 운송으로 인한 탄소 배출을 줄이세요.",
        "불필요한 조명을 끄고 자연광을 활용해보세요.",
        "에너지 효율이 높은 가전제품을 선택하세요.",
        "나무를 심어 탄소 흡수를 도와보세요.",
        "카풀을 이용해 차량 배출가스를 줄이세요.",
        "중고 제품을 구매해 새 물건 생산으로 인한 탄소를 줄이세요."
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