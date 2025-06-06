package com.example.myapplication12345.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication12345.ui.news.NaverNewsApiService
import com.example.myapplication12345.ui.news.NewsItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeViewModel : ViewModel() {
    // 인사말 텍스트용 LiveData
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
        "집에 단열재를 추가해 냉난방 효율을 높이 세요.\n집 내부가 쾌적 해지고 돈을 아낄수 있어요",
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

    // 뉴스 리스트 LiveData (다수의 뉴스를 저장)
    private val _newsList = MutableLiveData<List<NewsItem>>()

    // 현재 표시할 단일 뉴스 LiveData
    private val _news = MutableLiveData<NewsItem>()
    val news: LiveData<NewsItem> get() = _news

    // 오늘의 탄소 절약 목표 진행률 LiveData
    private val _progress = MutableLiveData<Int>(100) // 기본값 100으로 변경 [수정]
    val progress: LiveData<Int> get() = _progress

    // Firebase 관련 변수
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // Retrofit 설정
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openapi.naver.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val naverNewsApiService = retrofit.create(NaverNewsApiService::class.java)

    // 네이버 API 인증 정보
    private val clientId = "bK__n4pTB2lv0XNNkyTF"
    private val clientSecret = "KcPkPF7NRp"

    init {
        showRandomTip()
        fetchNews()
        loadMonthlyPoints() // 초기화 시 월별 포인트 로드
    }

//    fun setProgressFromEmissions(emissions: Int) {
//        // 배출량을 0-100%로 매핑 (최대값 조정 필요)
//        val maxEmissions = 10000 // 예: 최대 배출량 10000g
//        val progressValue = (emissions * 100 / maxEmissions).coerceIn(0, 100)
//        _progress.value = progressValue
//        Timber.tag("HomeViewModel").d("진행률 업데이트: $progressValue% (배출량: $emissions)")
//    }

    // 현재 월(YYYY-MM) 반환
    private fun getCurrentMonth(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }

    // 월별 포인트 로드 및 진행률 계산
    private fun loadMonthlyPoints() {
        val userId = auth.currentUser?.uid ?: return
        val currentMonth = getCurrentMonth()
        val monthlyPointRef = database.getReference("users").child(userId).child("monthly_points").child(currentMonth)

        monthlyPointRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val point = dataSnapshot.child("point").getValue(Int::class.java) ?: 0
                // 진행률 계산: 포인트 0 = 100%, 920 이상 = 0% [수정]
                val progress = maxOf(100 - ((point.toFloat() / 920) * 100).toInt(), 0)
                _progress.value = progress
                Timber.tag("HomeViewModel").d("Monthly points: $point, Progress: $progress%")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Timber.tag("Firebase").w(databaseError.toException(), "loadMonthlyPoints:onCancelled")
                _progress.value = 100 // Firebase 오류 시 진행률 100% [수정]
            }
        })
    }

    // 포인트 추가 (예: 챌린지 완료 시 호출)
    fun addPoints(pointsToAdd: Int) {
        val userId = auth.currentUser?.uid ?: return
        val currentMonth = getCurrentMonth()
        val monthlyPointRef = database.getReference("users").child(userId).child("monthly_points").child(currentMonth)

        monthlyPointRef.child("point").runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: MutableData): com.google.firebase.database.Transaction.Result {
                val currentPoints = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentPoints + pointsToAdd
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Timber.tag("Firebase").e(error.toException(), "addPoints:onComplete")
                }
            }
        })
    }

    // 랜덤 팁 표시 함수
    fun showRandomTip() {
        _currentTip.value = carbonSavingTips.random()
    }

    // 뉴스 가져오기 함수 (더 많은 뉴스 가져오기)
    fun fetchNews() {
        viewModelScope.launch {
            try {
                val response = naverNewsApiService.getNews(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    query = "탄소 배출 환경",
                    display = 100
                )

                val newsItems = response.items.map {
                    NewsItem(
                        title = it.title.replace("<b>", "").replace("</b>", ""),
                        description = it.description.replace("<b>", "").replace("</b>", ""),
                        originallink = it.originallink,
                        pubDate = it.pubDate
                    )
                }.filter {
                    it.title.contains("탄소") || it.description.contains("탄소") ||
                            it.title.contains("배출") || it.description.contains("배출") ||
                            it.title.contains("환경") || it.description.contains("환경")
                }
                _newsList.value = newsItems
                _news.value = newsItems.random()
            } catch (e: Exception) {
                e.printStackTrace()
                val errorItem = NewsItem("오류", "뉴스를 불러오지 못했습니다.", "", "")
                _newsList.value = listOf(errorItem)
                _news.value = errorItem
            }
        }
    }

    // 새로운 랜덤 뉴스 선택 함수
    fun showRandomNews() {
        _news.value = _newsList.value?.random() ?: NewsItem("뉴스가 없음", "최신 환경 뉴스가 없습니다.", "", "")
    }

    // 진행률 설정 함수 (디버깅 또는 수동 설정용)
    fun setProgress(value: Int) {
        if (value in 0..100) {
            _progress.value = value
        }
    }
}