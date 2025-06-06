package com.example.myapplication12345

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume

class ServerManager(private val context: Context) {

    // Firebase 인스턴스와 인증 객체 초기화
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    // 점수 및 닉네임 캐싱을 위한 SharedPreferences 초기화
    private val prefs: SharedPreferences = context.getSharedPreferences("ScoreCache", Context.MODE_PRIVATE)

    // 현재 사용자의 Firebase 참조를 반환하는 헬퍼 함수
    private fun getUserReference() = auth.currentUser?.uid?.let { database.getReference("users").child(it) }

    // 공통 에러 처리 헬퍼 함수 (사용자 친화적 메시지)
    private fun handleError(exception: Exception, operation: String) {
        val friendlyMessage = when (exception.message?.contains("network", true)) {
            true -> "네트워크 오류가 발생했어요. 다시 시도해주세요."
            else -> "$operation 중 문제가 발생했어요."
        }
        Timber.tag("FirebaseDatabase").w(exception, "$operation:onFailure")
        Toast.makeText(context, friendlyMessage, Toast.LENGTH_SHORT).show()
    }

    // 로컬 캐시에서 점수 가져오기
    private fun getCachedScore(): Int = prefs.getInt("cachedScore", 0)

    // 로컬 캐시에 점수 저장하기
    private fun cacheScore(score: Int) {
        prefs.edit { putInt("cachedScore", score) }
    }

    // 로컬 캐시에서 닉네임 가져오기
    private fun getCachedNickname(): String = prefs.getString("cachedNickname", "익명") ?: "익명"

    // 로컬 캐시에 닉네임 저장하기
    private fun cacheNickname(nickname: String) {
        prefs.edit { putString("cachedNickname", nickname) }
    }

    // 날짜 포맷팅 (YYYY-MM-DD)
    private fun getFormattedDate(date: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date.time)
    }

    // 특정 날짜의 제품 배출량 점수를 가져오는 메서드 (getProductEmissions 호출)
    fun getScoresForDate(date: Calendar, onScoresRetrieved: (Int) -> Unit) {
        getProductEmissions(date, onScoresRetrieved)
    }

    // 특정 날짜의 이동경로 배출량 점수를 가져오는 메서드 (getTransportEmissions 호출)
    fun getTransportEmissionsForDate(date: Calendar, onScoresRetrieved: (Int) -> Unit) {
        getTransportEmissions(date, onScoresRetrieved)
    }

    // 특정 날짜의 사물 배출량 점수를 가져오는 메서드
    fun getObjectEmissionsForDate(date: Calendar, onScoresRetrieved: (Int) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            onScoresRetrieved(0)
            return
        }

        // 날짜 포맷팅 및 Firebase 참조 설정
        val dateString = getFormattedDate(date)
        val emissionRef = userRef.child("emissions").child(dateString).child("objectEmissions")

        // Firebase에서 데이터 조회
        emissionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val emissions = snapshot.getValue(Int::class.java) ?: 0
                Timber.tag("ServerManager").d("사물 배출량 가져오기 성공: $dateString, 값: $emissions")
                onScoresRetrieved(emissions)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "사물 배출량 가져오기")
                onScoresRetrieved(0)
            }
        })
    }

    // 월별 포인트를 업데이트하는 메서드
    fun updateMonthlyPointsForMonth(month: String, callback: (Boolean) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        // emissions 노드에서 해당 월의 모든 날짜 데이터를 조회
        val emissionsRef = userRef.child("emissions")
        emissionsRef.orderByKey().startAt("$month-01").endAt("$month-31").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalEmissions = 0
                snapshot.children.forEach { dateSnapshot ->
                    val productEmissions = dateSnapshot.child("productEmissions").getValue(Int::class.java) ?: 0
                    val transportEmissions = dateSnapshot.child("transportEmissions").getValue(Int::class.java) ?: 0
                    // 사물 배출량 추가
                    val objectEmissions = dateSnapshot.child("objectEmissions").getValue(Int::class.java) ?: 0
                    totalEmissions += productEmissions + transportEmissions + objectEmissions
                }

                // monthly_points 노드에 총합 저장
                val monthlyPointRef = userRef.child("monthly_points").child(month).child("point")
                monthlyPointRef.setValue(totalEmissions)
                    .addOnSuccessListener {
                        Timber.tag("ServerManager").d("월별 포인트 업데이트 완료: $month, 총 배출량: $totalEmissions")
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        handleError(e, "월별 포인트 업데이트")
                        callback(false)
                    }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "월별 배출량 조회")
                callback(false)
            }
        })
    }

    /**
     * 현재 사용자에게 점수를 추가하는 메서드 (트랜잭션 사용)
     */
    fun addScoreToCurrentUser(scoreToAdd: Int, onComplete: (() -> Unit)? = null) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 새로운 점수 항목 키 생성
        val newScoreKey = userRef.child("scores").push().key ?: run {
            handleError(Exception("Failed to generate push key"), "점수 추가")
            return
        }

        // 점수 추가 트랜잭션 실행
        userRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val currentScore = currentData.child("score").getValue(Int::class.java) ?: getCachedScore()
                currentData.child("score").value = currentScore + scoreToAdd

                val scoresRef = currentData.child("scores").child(newScoreKey)
                scoresRef.child("value").value = scoreToAdd
                scoresRef.child("timestamp").value = ServerValue.TIMESTAMP

                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null || !committed) {
                    handleError(error?.toException() ?: Exception("Transaction failed"), "점수 추가")
                } else {
                    val newScore = snapshot?.child("score")?.getValue(Int::class.java) ?: (getCachedScore() + scoreToAdd)
                    cacheScore(newScore)
                    Toast.makeText(context, "점수가 추가되었어요!", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke()
                }
            }
        })
    }

    /**
     * 점수를 업데이트하는 메서드
     */
    fun updateScore(newScore: Int) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 점수 업데이트
        userRef.child("score").setValue(newScore)
            .addOnSuccessListener {
                cacheScore(newScore)
                Toast.makeText(context, "점수가 업데이트되었어요!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                handleError(exception, "점수 업데이트")
            }
    }

    /**
     * 점수를 가져오는 메서드
     */
    fun getScore(onScoreRetrieved: (Int) -> Unit) {
        // 캐시된 점수 먼저 반환
        val cachedScore = getCachedScore()
        onScoreRetrieved(cachedScore)

        // 사용자 인증 확인
        val userRef = getUserReference()
        if (userRef == null) {
            onScoreRetrieved(0)
            return
        }

        // Firebase에서 점수 조회
        userRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val score = dataSnapshot.getValue(Int::class.java) ?: cachedScore
                cacheScore(score)
                onScoreRetrieved(score)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "점수 가져오기")
                onScoreRetrieved(cachedScore)
            }
        })
    }

    /**
     * 특정 날짜의 productEmissions 가져오는 메서드
     */
    fun getProductEmissions(date: Calendar, onScoresRetrieved: (Int) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            onScoresRetrieved(0)
            return
        }

        // 날짜 포맷팅 및 Firebase 참조 설정
        val dateString = getFormattedDate(date)
        val emissionRef = userRef.child("emissions").child(dateString).child("productEmissions")

        // Firebase에서 데이터 조회
        emissionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val emissions = snapshot.getValue(Int::class.java) ?: 0
                Timber.tag("ServerManager").d("제품 배출량 가져오기 성공: $dateString, 값: $emissions")
                onScoresRetrieved(emissions)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "제품 배출량 가져오기")
                onScoresRetrieved(0)
            }
        })
    }

    /**
     * 특정 날짜의 transportEmissions 가져오는 메서드
     */
    fun getTransportEmissions(date: Calendar, onScoresRetrieved: (Int) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            onScoresRetrieved(0)
            return
        }

        // 날짜 포맷팅 및 Firebase 참조 설정
        val dateString = getFormattedDate(date)
        val emissionRef = userRef.child("emissions").child(dateString).child("transportEmissions")

        // Firebase에서 데이터 조회
        emissionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val emissions = snapshot.getValue(Int::class.java) ?: 0
                Timber.tag("ServerManager").d("이동경로 배출량 가져오기 성공: $dateString, 값: $emissions")
                onScoresRetrieved(emissions)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "이동경로 배출량 가져오기")
                onScoresRetrieved(0)
            }
        })
    }

    /**
     * 특정 날짜의 productEmissions 업데이트 메서드
     */
    fun updateProductEmissions(date: Calendar, emissions: Int, callback: (Boolean) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        // 날짜 포맷팅 및 Firebase 참조 설정
        val dateString = getFormattedDate(date)
        val emissionRef = userRef.child("emissions").child(dateString).child("productEmissions")

        // 데이터 업데이트
        emissionRef.setValue(emissions)
            .addOnSuccessListener {
                Timber.tag("ServerManager").d("제품 배출량 업데이트 완료: $dateString, emissions: $emissions")
                callback(true)
            }
            .addOnFailureListener { e ->
                handleError(e, "제품 배출량 업데이트")
                callback(false)
            }
    }

    /**
     * 특정 날짜의 transportEmissions 업데이트 메서드
     */
    fun updateTransportEmissions(date: Calendar, emissions: Int, callback: (Boolean) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        // 날짜 포맷팅 및 Firebase 참조 설정
        val dateString = getFormattedDate(date)
        val emissionRef = userRef.child("emissions").child(dateString).child("transportEmissions")

        // 데이터 업데이트
        emissionRef.setValue(emissions)
            .addOnSuccessListener {
                Timber.tag("ServerManager").d("이동경로 배출량 업데이트 완료: $dateString, emissions: $emissions")
                callback(true)
            }
            .addOnFailureListener { e ->
                handleError(e, "이동경로 배출량 업데이트")
                callback(false)
            }
    }

    /**
     * 특정 날짜의 objectEmissions 업데이트 메서드
     */
    fun updateObjectEmissions(date: Calendar, emissions: Int, callback: (Boolean) -> Unit) {
        // 사용자 인증 확인
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        // 날짜 포맷팅 및 Firebase 참조 설정
        val dateString = getFormattedDate(date)
        val emissionRef = userRef.child("emissions").child(dateString).child("objectEmissions")

        // 기존 배출량 조회 후 누적 업데이트
        emissionRef.get().addOnSuccessListener { snapshot ->
            val currentEmissions = snapshot.getValue(Int::class.java) ?: 0
            emissionRef.setValue(currentEmissions + emissions)
                .addOnSuccessListener {
                    Timber.tag("ServerManager").d("사물 배출량 업데이트 완료: $dateString, emissions: ${currentEmissions + emissions}")
                    callback(true)
                }
                .addOnFailureListener { e ->
                    handleError(e, "사물 배출량 업데이트")
                    callback(false)
                }
        }.addOnFailureListener { e ->
            handleError(e, "현재 사물 배출량 조회")
            callback(false)
        }
    }

    /**
     * 닉네임을 가져오는 메서드
     */
    suspend fun getNickname(): String = suspendCancellableCoroutine { continuation ->
        val cachedNickname = getCachedNickname()
        val userRef = getUserReference()

        if (userRef == null) {
            continuation.resume("익명")
            return@suspendCancellableCoroutine
        }

        userRef.child("nickname").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val nickname = dataSnapshot.getValue(String::class.java) ?: cachedNickname
                cacheNickname(nickname)
                continuation.resume(nickname)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "닉네임 가져오기")
                continuation.resume(cachedNickname)
            }
        })
    }
}