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
├─auth
│      IntroActivity.kt
│      JoinActivity.kt
│      LoginActivity.kt
│
├─fragments
│      DailyFragment.kt
│      MonthlyFragment.kt
│      NowFragment.kt
│      RankingFragment.kt
│      WeeklyFragment.kt
│
├─network
│      BarcodeAnalyzer.kt
│
└─ui
    ├─calendar
    │      MapFragment.java             지도
    │      CalendarFragment.kt          캘린더 백엔드
    │      CalendarViewModel.kt         캘린터 실행
    │
    ├─dashboard
    │      DashboardFragment.kt
    │      DashboardViewModel.kt
    │      ResultActivity.kt
    │
    ├─home
    │      HomeFragment.kt
    │      HomeViewModel.kt
    │
    └─Ranking
            NoFragment.kt
            RankingViewModel.kt