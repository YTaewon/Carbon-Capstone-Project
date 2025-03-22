package com.example.myapplication12345

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import java.util.Calendar

class ScoreManager(private val context: Context) {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences("ScoreCache", Context.MODE_PRIVATE)

    // Firebase 참조 헬퍼 함수
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
        prefs.edit().putInt("cachedScore", score).apply()
    }

    /**
     * 현재 사용자에게 점수를 추가하는 메서드 (트랜잭션 사용)
     */
    fun addScoreToCurrentUser(scoreToAdd: Int, onComplete: (() -> Unit)? = null) {
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            return
        }

        // push 키를 트랜잭션 외부에서 생성
        val newScoreKey = userRef.child("scores").push().key ?: run {
            handleError(Exception("Failed to generate push key"), "점수 추가")
            return
        }

        // 트랜잭션을 사용해 점수 추가와 업데이트를 원자적으로 처리
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
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            return
        }

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
     * 점수를 가져오는 메서드 (캐시 우선)
     */
    fun getScore(onScoreRetrieved: (Int) -> Unit) {
        val cachedScore = getCachedScore()
        onScoreRetrieved(cachedScore) // 캐시된 점수를 먼저 반환

        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            return
        }

        userRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val score = dataSnapshot.getValue(Int::class.java) ?: cachedScore
                cacheScore(score)
                onScoreRetrieved(score) // 최신 점수로 콜백 다시 호출
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "점수 가져오기")
            }
        })
    }

    /**
     * 특정 날짜의 점수를 가져오는 메서드
     */
    fun getScoresForDate(date: Calendar, onScoresRetrieved: (Int) -> Unit) {
        val userRef = getUserReference() ?: run {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show()
            return
        }

        val startOfDayMillis = date.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDayMillis = Calendar.getInstance().apply { timeInMillis = startOfDayMillis }
            .apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

        userRef.child("scores").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dailyScore = snapshot.children
                    .mapNotNull { scoreSnapshot ->
                        val scoreValue = scoreSnapshot.child("value").getValue(Int::class.java) ?: 0
                        val timestamp = scoreSnapshot.child("timestamp").getValue(Long::class.java) ?: 0
                        if (timestamp in startOfDayMillis..<endOfDayMillis) scoreValue else null
                    }
                    .sum()
                onScoresRetrieved(dailyScore)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                handleError(databaseError.toException(), "점수 가져오기")
            }
        })
    }
}