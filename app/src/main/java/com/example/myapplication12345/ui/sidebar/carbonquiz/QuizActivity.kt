package com.example.myapplication12345.ui.sidebar.carbonquiz

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.myapplication12345.R

class QuizActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var backButton: ImageView
    private lateinit var loadButton: ImageView
    private lateinit var questionText: TextView
    private lateinit var option1: Button
    private lateinit var option2: Button
    private lateinit var option3: Button
    private lateinit var option4: Button
    private lateinit var resultText: TextView
    private lateinit var nextButton: Button

    // 음식 관련 탄소 발자국 퀴즈 데이터
    private val quizData = listOf(
        QuizQuestion(
            "어떤 음식이 탄소 발자국이 가장 적을까요?",
            listOf("소고기", "닭고기", "채소"),
            2 // 채소가 정답 (인덱스 2)
        ),
        QuizQuestion(
            "하루 한 끼를 고기 대신 채식으로 바꾸면 얼마나 탄소 배출을 줄일 수 있을까요?",
            listOf("약 0.5kg", "약 2.5kg", "약 10kg"),
            1 // 약 2.5kg (인덱스 1)
        ),
        QuizQuestion(
            "가장 탄소 배출이 높은 음식은?",
            listOf("돼지고기", "소고기", "생선", "두부"),
            1 // 소고기 (인덱스 1)
        )
    )

    private var currentQuestionIndex = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // UI 요소 초기화
        toolbar = findViewById(R.id.toolbar)
        backButton = findViewById(R.id.back_button)
        loadButton = findViewById(R.id.load_button)
        questionText = findViewById(R.id.questionText)
        option1 = findViewById(R.id.option1)
        option2 = findViewById(R.id.option2)
        option3 = findViewById(R.id.option3)
        option4 = findViewById(R.id.option4)
        resultText = findViewById(R.id.resultText)
        nextButton = findViewById(R.id.nextButton)

        // 툴바 설정
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 툴바 버튼 리스너
        backButton.setOnClickListener { finish() }

        loadButton.setOnClickListener {
            currentQuestionIndex = 0
            showQuestion()
            resultText.text = ""
            nextButton.visibility = View.GONE
            enableOptions()
        }

        // 첫 번째 질문 표시
        showQuestion()

        // 퀴즈 버튼 리스너
        option1.setOnClickListener { checkAnswer(0) }
        option2.setOnClickListener { checkAnswer(1) }
        option3.setOnClickListener { checkAnswer(2) }
        option4.setOnClickListener { checkAnswer(3) }
        nextButton.setOnClickListener {
            currentQuestionIndex++
            if (currentQuestionIndex < quizData.size) {
                showQuestion()
                resultText.text = ""
                nextButton.visibility = View.GONE
                enableOptions()
            } else {
                questionText.text = "퀴즈 완료!"
                option1.visibility = View.GONE
                option2.visibility = View.GONE
                option3.visibility = View.GONE
                option4.visibility = View.GONE
                resultText.text = "수고하셨습니다!"
                nextButton.visibility = View.GONE
            }
        }
    }
    // 질문 표시 함수
    private fun showQuestion() {
        val currentQuestion = quizData[currentQuestionIndex]
        questionText.text = currentQuestion.question

        // 모든 버튼을 기본적으로 숨김
        option1.visibility = View.GONE
        option2.visibility = View.GONE
        option3.visibility = View.GONE
        option4.visibility = View.GONE

        // 선택지 개수에 따라 버튼 표시
        val options = currentQuestion.options
        when (options.size) {
            1 -> {
                option1.visibility = View.VISIBLE
                option1.text = options[0]
            }
            2 -> {
                option1.visibility = View.VISIBLE
                option2.visibility = View.VISIBLE
                option1.text = options[0]
                option2.text = options[1]
            }
            3 -> {
                option1.visibility = View.VISIBLE
                option2.visibility = View.VISIBLE
                option3.visibility = View.VISIBLE
                option1.text = options[0]
                option2.text = options[1]
                option3.text = options[2]
            }
            4 -> {
                option1.visibility = View.VISIBLE
                option2.visibility = View.VISIBLE
                option3.visibility = View.VISIBLE
                option4.visibility = View.VISIBLE
                option1.text = options[0]
                option2.text = options[1]
                option3.text = options[2]
                option4.text = options[3]
            }
            // 5개 이상의 경우는 현재 UI가 4개 버튼만 지원하므로 제한
            else -> {
                option1.visibility = View.VISIBLE
                option2.visibility = View.VISIBLE
                option3.visibility = View.VISIBLE
                option4.visibility = View.VISIBLE
                option1.text = options[0]
                option2.text = options[1]
                option3.text = options[2]
                option4.text = options[3]
            }
        }
    }

    // 정답 확인 함수
    private fun checkAnswer(selectedOption: Int) {
        val currentQuestion = quizData[currentQuestionIndex]
        if (selectedOption == currentQuestion.correctAnswer) {
            resultText.text = "정답입니다!"
        } else {
            resultText.text = "오답입니다. 정답은 '${currentQuestion.options[currentQuestion.correctAnswer]}'입니다."
        }
        nextButton.visibility = View.VISIBLE
        disableOptions()
    }

    // 선택지 버튼 비활성화
    private fun disableOptions() {
        option1.isEnabled = false
        option2.isEnabled = false
        option3.isEnabled = false
        option4.isEnabled = false
    }

    // 선택지 버튼 활성화
    private fun enableOptions() {
        option1.isEnabled = true
        option2.isEnabled = true
        option3.isEnabled = true
        option4.isEnabled = true
    }
}

// 퀴즈 데이터 클래스
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctAnswer: Int
)