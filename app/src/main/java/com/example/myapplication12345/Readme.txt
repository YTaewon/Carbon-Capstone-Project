│
 myapplication12345
     │  MainActivity.kt                     매인
     │  ScoreManager.kt                     서버 연결 및 점수 설정
     │  SplashActivity.kt                   로딩 화면 및 퍼미셜
     │  ViewPagerAdapter.kt                 네비게이션바 설정
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
     └─ui
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
         │      IntroActivity.kt            로딩 화면
         │      JoinActivity.kt             회원 가입 화면
         │      LoginActivity.kt            로그인 화면
         │
         ├─map
         │      MapFragment.kt              지도 백엔드
         │      MapViewModel.kt             지도 실행
         │
         ├─pedometer
         │      PedometerFragment.kt        만보기 백엔드
         │      PedometerViewModel.kt       만보기 실행
         │
         └─ranking
                 Profiles.kt
                 RankingAdapter.kt          랭킹 실행
                 RankingFragment.kt         랭킹 백엔드

