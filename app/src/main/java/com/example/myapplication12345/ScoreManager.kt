package com.example.myapplication12345

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ScoreManager(private val context: Context) {

    private val database = FirebaseDatabase.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * 점수를 업데이트하는 메서드
     */
    fun updateScore(newScore: Int) {
        if (userId != null) {
            val userRef = database.getReference("users").child(userId)
            userRef.child("score").setValue(newScore)
                .addOnSuccessListener {
                    // 성공적으로 업데이트된 경우
                    Toast.makeText(context, "점수가 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    // 업데이트 실패한 경우
                    Log.w("FirebaseDatabase", "updateScore:onFailure", exception)
                    Toast.makeText(context, "점수 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "사용자 인증이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 점수를 가져오는 메서드
     */
    fun getScore(onScoreRetrieved: (Int) -> Unit) {
        if (userId != null) {
            val userRef = database.getReference("users").child(userId)
            userRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val score = dataSnapshot.getValue(Int::class.java) ?: 0
                    onScoreRetrieved(score)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.w("FirebaseDatabase", "getScore:onCancelled", databaseError.toException())
                    Toast.makeText(context, "점수 가져오기에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Toast.makeText(context, "사용자 인증이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
//점수 가저오기 ex)
//class HomeFragment : Fragment() {
//
//    private lateinit var scoreManager: ScoreManager
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        val binding = FragmentHomeBinding.inflate(inflater, container, false)
//
//        // ScoreManager 인스턴스 생성
//        scoreManager = ScoreManager(requireContext())
//
//        // 예시: 버튼 클릭 시 점수 업데이트
//        binding.updateScoreButton.setOnClickListener {
//            val newScore = 100 // 업데이트할 점수
//            scoreManager.updateScore(newScore)
//        }
//
//        return binding.root
//    }
//}

//점수 변경 ex)
//class HomeFragment : Fragment() {
//
//    private lateinit var scoreManager: ScoreManager
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        val binding = FragmentHomeBinding.inflate(inflater, container, false)
//
//        // ScoreManager 인스턴스 생성
//        scoreManager = ScoreManager(requireContext())
//
//        // 예시: 점수 가져와서 텍스트 뷰에 표시
//        scoreManager.getScore { score ->
//            binding.scoreText.text = "점수: $score"
//        }
//
//        return binding.root
//    }
//}

