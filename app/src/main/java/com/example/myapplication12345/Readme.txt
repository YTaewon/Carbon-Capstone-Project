│  ApiService.kt
│  CameraActivity.kt
│  MainActivity.kt
│  PageTransfomer.kt
│  Profiles.kt
│  RankingActivity.kt
│  RankingAdapter.kt
│  Readme.txt
│  ScoreManager.kt
│  SplashActivity.kt
│  viewPager.kt
│
├─AI
│  │  AITest.java                   메인에 들어갈 코그 태스트
│  │  PyTorchHelper.java            모델 실행
│  │  SensorDataProcessor.java      전처리
│  │  SensorDataService.java        센서 데이터
│  │
│  ├─AP                             AP 데이터 전처리
│  │      APProcessor.java
│  │
│  ├─BTS                            BTS 데이터 전처리
│  │      BTSProcessor.java
│  │
│  ├─GPS                            GPS 데이터 전처리
│  │      GPSProcessor.java
│  │
│  └─IMU                            IMU 데이터 전처리
│          IMUConfig.java
│          IMUFeatureExtractor.java
│          IMUProcessoing.java
│          IMUProcessor.java
│          IMUUtils.java
│
├─network
│      BarcodeAnalyzer.kt
│
└─ui
    ├─login
    │      IntroActivity.kt             로딩 화면
    │      JoinActivity.kt              회원 가입 화면
    │      LoginActivity.kt             로그인 화면
    │
    ├─ranking
    │      NowFragment.kt               전체 랭킹
    │      DailyFragment.kt             24시간 랭킹
    │      WeeklyFragment.kt            주간 랭킹
    │      MonthlyFragment.kt           월간 랭킹
    │      RankingFragment.kt           랭킹 백엔드
    ├─calendar
    │      MapFragment.java             지도
    │      CalendarFragment.kt          캘린더 백엔드
    │      CalendarViewModel.kt         캘린터 실행
    │
    ├─camera
    │      CameraFragment.kt            카메라 백엔드
    │      CameraViewModel.kt           카메라 실행
    │      ResultActivity.kt            카메라 출력 값
    │
    ├─home
    │      HomeFragment.kt              홈 백엔드
    │      HomeViewModel.kt             홈 실행
    │
    ├─stepper
    │      StepperFragment.kt           만보기 백엔드
    │      StepperViewModel.kt          만보기 실행
    │
    └─map
           MapFragment.kt              맵 백엔드
           MapViewModel.kt             맵 실행