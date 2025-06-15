package com.example.myapplication12345.ui.sidebar.foodcalculator

class FoodDataManager {
    /**
     * 음식 데이터를 초기화하고 반환하는 메서드.
     * 모든 탄소 발자국 수치는 음식 100g당 배출되는 g CO2e(이산화탄소 환산량)의 추정치입니다.
     */
    fun getFoodCategories(): MutableList<FoodCategory> {
        return mutableListOf(
            FoodCategory("밥, 죽, 면", listOf(
                FoodItem("쌀밥", 130.0),            // 쌀(생산) + 조리 과정 포함
                FoodItem("비빔밥", 180.0),           // 재료 구성에 따라 편차 큼
                FoodItem("김밥", 160.0),
                FoodItem("김치볶음밥", 250.0),       // 돼지고기 등 재료 추가 시 증가
                FoodItem("칼국수", 150.0),           // 밀가루 기반
                FoodItem("물냉면", 350.0),           // 육수(소고기) 포함
                FoodItem("비빔냉면", 220.0)
            )),
            FoodCategory("국, 탕, 찌개", listOf(
                FoodItem("된장찌개", 150.0),       // 두부, 채소 위주
                FoodItem("김치찌개", 450.0),       // 돼지고기 포함 기준
                FoodItem("미역국", 520.0),         // 소고기 포함 기준
                FoodItem("콩나물국", 50.0),
                FoodItem("설렁탕", 600.0),         // 소고기 및 뼈 기반
                FoodItem("갈비탕", 700.0),         // 소고기 기반
                FoodItem("육개장", 550.0),         // 소고기 기반
                FoodItem("순두부찌개", 180.0)      // 해산물/계란 포함 가능
            )),
            FoodCategory("반찬, 전", listOf(
                FoodItem("배추김치", 25.0),
                FoodItem("콩나물무침", 30.0),
                FoodItem("깍두기", 28.0),
                FoodItem("시금치나물", 40.0),
                FoodItem("장조림", 2800.0),        // 소고기 기반
                FoodItem("멸치볶음", 150.0),
                FoodItem("콩자반", 80.0),
                FoodItem("제육볶음", 1200.0),      // 돼지고기 기반
                FoodItem("불고기", 2500.0),        // 소고기 기반
                FoodItem("고등어구이", 450.0),
                FoodItem("삼겹살구이", 1150.0),     // 돼지고기
                FoodItem("소고기구이", 2700.0),     // 소고기
                FoodItem("달걀프라이", 480.0),
                FoodItem("잡채", 300.0),          // 당면, 채소, 고기 혼합
                FoodItem("계란찜", 470.0)
            )),
            FoodCategory("유제품", listOf(
                FoodItem("우유", 250.0),
                FoodItem("두유", 100.0),
                FoodItem("치즈", 2100.0),         // 우유 농축으로 탄소발자국 높음
                FoodItem("요거트", 300.0)
            )),
            FoodCategory("외국 음식", listOf(
                FoodItem("피자", 900.0),          // 치즈, 육류 토핑 포함
                FoodItem("햄버거", 1500.0),        // 소고기 패티 기준
                FoodItem("후라이드 치킨", 700.0),
                FoodItem("카레", 200.0),           // 채소 카레 기준, 육류 추가 시 증가
                FoodItem("볼로네제 파스타", 650.0) // 다진 소고기 포함
            )),
            FoodCategory("베이커리", listOf(
                FoodItem("베이글", 140.0),
                FoodItem("식빵", 130.0),
                FoodItem("케이크", 350.0),         // 버터, 우유, 계란 포함
                FoodItem("도넛", 400.0),
                FoodItem("머핀", 380.0),
                FoodItem("팬케이크", 250.0),
                FoodItem("스콘", 370.0),           // 버터 함량 높음
                FoodItem("와플", 260.0)
            )),
            FoodCategory("과자류", listOf(
                FoodItem("크래커", 150.0),
                FoodItem("감자칩", 550.0),         // 팜유 등 기름 사용
                FoodItem("초콜릿", 1900.0),        // 카카오 생산 과정 포함
                FoodItem("마시멜로", 250.0),
                FoodItem("사탕", 180.0)
            )),
            FoodCategory("과일", listOf(
                FoodItem("사과", 40.0),
                FoodItem("아보카도", 160.0),        // 장거리 운송
                FoodItem("바나나", 80.0),          // 장거리 운송
                FoodItem("딸기", 120.0),
                FoodItem("포도", 110.0),
                FoodItem("오렌지", 50.0),
                FoodItem("복숭아", 60.0),
                FoodItem("배", 55.0),
                FoodItem("수박", 45.0)
            )),
            FoodCategory("채소", listOf(
                FoodItem("브로콜리", 60.0),
                FoodItem("토마토", 140.0),         // 온실 재배 포함
                FoodItem("양파", 35.0),
                FoodItem("버섯", 100.0),
                FoodItem("마늘", 30.0),
                FoodItem("고추", 40.0),
                FoodItem("당근", 30.0),
                FoodItem("양배추", 20.0),
                FoodItem("배추", 22.0),
                FoodItem("오이", 80.0)            // 온실 재배 포함
            )),
            FoodCategory("육류", listOf(
                FoodItem("베이컨", 1200.0),        // 돼지고기 + 가공
                FoodItem("소고기", 2700.0),       // 부위/사육 방식에 따라 편차 큼
                FoodItem("닭고기", 690.0),
                FoodItem("소시지", 900.0),         // 돼지고기 + 가공
                FoodItem("햄", 950.0),            // 돼지고기 + 가공
                FoodItem("돼지고기", 1100.0),
                FoodItem("오리고기", 650.0)
            )),
            FoodCategory("음료", listOf(
                FoodItem("에스프레소", 1500.0),      // 원두 100g 기준, 물이 아닌
                FoodItem("라떼", 350.0),           // 우유 포함
                FoodItem("인스턴트 커피", 1600.0),   // 원두 100g 기준, 가공 과정 포함
                FoodItem("과일주스", 120.0),
                FoodItem("물", 0.1),             // 생산 및 운송 과정 포함
                FoodItem("콜라/사이다", 100.0),
                FoodItem("소주", 280.0),
                FoodItem("와인", 160.0)
            ))
        )
    }
}