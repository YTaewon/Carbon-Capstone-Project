package com.example.myapplication12345.ui.sidebar.foodcalculator

class FoodDataManager {
    // 음식 데이터를 초기화하고 반환하는 메서드
    fun getFoodCategories(): MutableList<FoodCategory> {
        return mutableListOf(
            FoodCategory("밥, 죽, 면", listOf(
                FoodItem("쌀밥", 500.0), FoodItem("비빔밥", 700.0), FoodItem("김밥", 400.0),
                FoodItem("김치볶음밥", 400.0), FoodItem("칼국수", 400.0), FoodItem("물냉면", 2400.0),
                FoodItem("비빔냉면", 1100.0)
            )),
            FoodCategory("국, 탕, 찌개", listOf(
                FoodItem("된장찌개", 1500.0), FoodItem("김치찌개", 2300.0), FoodItem("미역국", 2600.0),
                FoodItem("콩나물국", 500.0), FoodItem("설렁탕", 1000.0), FoodItem("갈비탕", 5000.0),
                FoodItem("육개장", 3000.0), FoodItem("순두부찌개", 700.0)
            )),
            FoodCategory("반찬, 전", listOf(
                FoodItem("배추김치", 300.0), FoodItem("콩나물", 200.0), FoodItem("깍두기", 300.0),
                FoodItem("시금치나물", 500.0), FoodItem("장조림", 5500.0), FoodItem("멸치조림", 100.0),
                FoodItem("콩조림", 700.0), FoodItem("제육볶음", 1900.0), FoodItem("불고기", 13900.0),
                FoodItem("고등어 구이", 300.0), FoodItem("삼겹살 구이", 2000.0), FoodItem("소고기 구이", 7700.0),
                FoodItem("달걀프라이", 100.0), FoodItem("잡채", 600.0), FoodItem("계란찜", 200.0)
            )),
            FoodCategory("유제품", listOf(
                FoodItem("우유", 1200.0), FoodItem("두유", 300.0), FoodItem("치즈", 11300.0),
                FoodItem("요거트", 264.0)
            )),
            FoodCategory("외국 음식", listOf(
                FoodItem("피자", 2000.0), FoodItem("햄버거 세트", 3700.0), FoodItem("후라이드 치킨", 2100.0),
                FoodItem("카레", 2900.0), FoodItem("볼로네제 파스타", 4200.0)
            )),
            FoodCategory("베이커리", listOf(
                FoodItem("베이글", 100.0), FoodItem("식빵", 97.0), FoodItem("케이크", 300.0),
                FoodItem("도넛", 658.0), FoodItem("머핀", 658.0), FoodItem("팬케이크", 50.0),
                FoodItem("스콘", 129.0), FoodItem("와플", 53.0)
            )),
            FoodCategory("과자류", listOf(
                FoodItem("크래커", 28.0), FoodItem("감자칩", 306.0), FoodItem("초콜렛", 173.0),
                FoodItem("마시멜로", 68.0), FoodItem("사탕", 72.0)
            )),
            FoodCategory("과일", listOf(
                FoodItem("사과", 72.0), FoodItem("아보카도", 384.0), FoodItem("바나나", 104.0),
                FoodItem("딸기", 100.0), FoodItem("포도", 345.0), FoodItem("오렌지", 152.0),
                FoodItem("복숭아", 152.0), FoodItem("배", 82.0), FoodItem("수박", 100.0)
            )),
            FoodCategory("야채", listOf(
                FoodItem("브로콜리", 21.0), FoodItem("토마토", 100.0), FoodItem("양파", 84.0),
                FoodItem("버섯", 112.0), FoodItem("마늘", 1.0), FoodItem("고추", 30.0),
                FoodItem("당근", 91.0), FoodItem("양배추(80g)", 50.0), FoodItem("배추(80g)", 129.0),
                FoodItem("오이", 1.0)
            )),
            FoodCategory("고기류", listOf(
                FoodItem("베이컨(1슬라이스,33g)", 219.0), FoodItem("소고기(65g)", 2816.0),
                FoodItem("닭고기(160g)", 753.0), FoodItem("소세지(11g)", 73.0),
                FoodItem("햄(1슬라이스,15g)", 99.0), FoodItem("돼지고기(85g)", 563.0),
                FoodItem("오리고기(160g)", 809.0)
            )),
            FoodCategory("음료", listOf(
                FoodItem("에스프레소(1샷,30ml)", 21.0), FoodItem("라떼(240ml)", 387.0),
                FoodItem("인스턴트 커피(400ml)", 645.0), FoodItem("과일쥬스(150ml)", 300.0),
                FoodItem("물(500ml)", 0.0), FoodItem("사이다(500ml)", 438.0),
                FoodItem("소주(25ml)", 58.0), FoodItem("와인(150ml)", 205.0)
            ))
        )
    }
}