![Image](https://github.com/user-attachments/assets/98050b46-fbe9-4b01-912d-49d2b8eefb0b)

```
 myapplication12345
     │  MainActivity.kt                     메인 네비게이션바 및 상단 툴바 설정
     │  ServerManager.kt                    서버 연결
     │  SplashActivity.kt                   로딩 화면 및 퍼미셜
     │  ViewPagerAdapter.kt                 네비게이션바 설정
     │  AlarmBootReceiver.kt                알람
     │
     ├─AI
     │  │  MovementAnalyzer.java            이동 거리 계산
     │  │  SensorDataProcessor.java         전처리 및 AI 작동
     │  │  SensorDataService.java           센서 데이터 수집
     │  │
     │  ├─AP
     │  │      APProcessor.java             AP 데이터 전처리
     │  │
     │  ├─BTS
     │  │      BTSProcessor.java            BTS 데이터 전처리
     │  │
     │  ├─GPS
     │  │      GPSProcessor.java            GPS 데이터 전처리
     │  │
     │  └─IMU
     │          IMUConfig.java              IMU 실행 설정
     │          IMUFeatureExtractor.java    IMU 전처리 개산부
     │          IMUProcessoing.java         IMU 전처리 백엔드
     │          IMUProcessor.java           IMU 전처리 실행
     │          IMUUtils.java               IMU 전처리 도구 모음
     │
     ├─chatbot
     │      ApiClient.java                  chatgpt api 요청
     │      ChatbotUi.java                  chatgpt UI 연결
     │      ChatGPTApi.java                 chatgpt api 인터페이스
     │      ChatGPTRequest.java             chatgpt 요청
     │      ChatGPTResponse.java            chatgpt 응답
     │      ChatMsg.java                    메세지 변수들
     │      ChatMsgAdapter.java             메세지 보내는 알고리즘
     │
     └─ui
         ├─sidebar
         │  ├─carbonquiz
         │  │       QuizActivity.kt         퀴즈 실행
         │  │
         │  ├─foodcalculator
         │  │       FilterManager.kt                카태고리 매니저
         │  │       FoodCalculatorActivity.kt       음식 탄소 계산기 실행
         │  │       FoodCategory.kt                 카테고리 분류
         │  │       FoodDataManager.kt              음식 탄소량 데이터
         │  │       FoodExpandableListAdapter.kt    음식 탄소 계산기 백엔드
         │  │       FoodItem.kt                     음식 분류
         │  │
         │  └─setting
         │          SettingActivity.kt
         │
         ├─calendar
         │      CalendarFragment.kt         지도 백엔드
         │      CalendarViewModel.kt        지도 실행
         │
         ├─camera
         │      BarcodeAnalyzer.kt          바코드 인식 (미사용)
         │      CameraFragment.kt           카메라 백엔드
         │      CameraViewModel.kt          카메라 실행
         │      ResultActivity.kt           카메라 결과
         │
         ├─home
         │      HomeFragment.kt             홈 백엔드
         │      HomeViewModel.kt            홈 실행
         │
         ├─login
         │      IntroActivity.kt            로그인 및 회원 가입 이동 버튼 화면
         │      JoinActivity.kt             회원 가입 화면
         │
         ├─map
         │      MapFragment.kt              지도 실행 및 백엔드
         │
         ├─news
         │      NaverNewsApiService.kt      네이비 뉴스 api
         │      NewsData.kt                 네이버 뉴스 가져올 데이터 설정
         │
         ├─pedometer
         │      PedometerFragment.kt        만보기 백엔드
         │      PedometerViewModel.kt       만보기 실행
         │
         └─ranking
                 Profiles.kt
                 RankingAdapter.kt          랭킹 실행
                 RankingFragment.kt         랭킹 백엔드


    중요
    local.properties
        MAPS_API_KEY = 구글 맵 api
        OPENAI_API_KEY = 챗 gpt api
```
