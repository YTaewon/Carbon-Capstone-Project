package com.example.myapplication12345.ui.sidebar.carbonquiz

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.myapplication12345.R

class QuizActivity : AppCompatActivity() {

    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
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
            2, // 정답
            "채소는 소고기나 닭고기에 비해 생산 과정에서 탄소 배출이 훨씬 적습니다."
        ),
        QuizQuestion(
            "하루 한 끼를 고기 대신 채식으로 바꾸면 얼마나 탄소 배출을 줄일 수 있을까요?",
            listOf("약 0.5kg", "약 2.5kg", "약 10kg"),
            1,
            "연구에 따르면 고기 한 끼를 채식으로 대체하면 약 2.5kg의 탄소 배출을 줄일 수 있습니다."
        ),
        QuizQuestion(
            "가장 탄소 배출이 높은 음식은?",
            listOf("돼지고기", "소고기", "생선", "두부"),
            1,
            "소고기는 사육과 가공 과정에서 메탄과 이산화탄소 배출이 많아 탄소 발자국이 큽니다."
        ),
        QuizQuestion(
            "다음 중 일회용품을 줄일 수 있는 실천은 무엇일까요?",
            listOf("테이크아웃 시 일회용 컵 사용", "장볼 때 비닐봉지 사용", "개인 텀블러와 장바구니 사용", "매일 새 플라스틱 병 물 구입"),
            2,
            "개인 텀블러와 장바구니를 사용하면 일회용 플라스틱과 종이 사용을 줄일 수 있습니다."
        ),
        QuizQuestion(
            "다음 중 재활용이 가능한 것은?",
            listOf("음식물 묻은 피자박스", "투명 플라스틱 용기", "이물질이 붙은 페트병", "코팅된 종이컵"),
            1,
            "투명 플라스틱 용기는 깨끗하면 재활용이 가능하지만, 오염되거나 코팅된 물질은 재활용이 어렵습니다."
        ),
        QuizQuestion(
            "지구 평균기온이 1.5도 이상 오르면 어떤 현상이 더 심해질까요?",
            listOf("눈 내리는 날이 증가", "해수면 상승", "오존층 두꺼워짐", "산소 농도 증가"),
            1,
            "기온 상승으로 빙하가 녹아 해수면이 상승하며, 이는 기후 변화의 주요 결과 중 하나입니다."
        ),
        QuizQuestion(
            "탄소중립을 위한 개인 실천이 아닌 것은?",
            listOf("대중교통 이용", "친환경 제품 구매", "음식물 쓰레기 줄이기", "전자기기 오래 켜두기"),
            3,
            "전자기기를 오래 켜두면 불필요한 전기 소모로 탄소 배출이 증가합니다."
        ),
        QuizQuestion(
            "전기를 아끼기 위한 행동으로 적절하지 않은 것은?",
            listOf("사용하지 않는 플러그 뽑기", "냉장고 문 자주 열기", "LED 조명 사용", "대기전력 차단 멀티탭 사용"),
            1,
            "냉장고 문을 자주 열면 냉기가 손실되어 전기 소모가 늘어납니다."
        ),
        QuizQuestion(
            "친환경 교통수단으로 분류되지 않는 것은?",
            listOf("자전거", "전기차", "도보", "항공기"),
            3,
            "항공기는 연료 소모가 많아 탄소 배출이 높으며, 친환경 교통수단으로 분류되지 않습니다."
        ),
        QuizQuestion(
            "다음 중 탄소 배출을 줄이기 위한 식생활 실천은?",
            listOf("고기 중심 식단 유지", "가공식품 위주 식사", "제철 채소 섭취", "잦은 배달 음식 주문"),
            2,
            "제철 채소는 운송과 저장 과정에서의 에너지 소모가 적어 탄소 배출을 줄입니다."
        ),
        QuizQuestion(
            "일상에서 물을 절약하는 방법은?",
            listOf("양치할 때 수도 틀어놓기", "샤워기 대신 욕조 사용", "세탁 시 물 가득 사용", "양치할 때 컵 사용하기"),
            3,
            "양치할 때 컵을 사용하면 물 낭비를 줄여 물을 절약할 수 있습니다."
        ),
        QuizQuestion(
            "태양광 에너지의 장점은?",
            listOf("연료 비용이 많이 든다", "탄소 배출이 많다", "재생 가능한 에너지다", "항상 전기 공급이 된다"),
            2,
            "태양광 에너지는 태양빛을 이용한 재생 가능 에너지로, 탄소 배출이 거의 없습니다."
        ),
        QuizQuestion(
            "모든 플라스틱은 재활용이 가능하다.",
            listOf("O", "X"),
            1,
            "플라스틱은 종류와 오염 여부에 따라 재활용 가능 여부가 달라집니다."
        ),
        QuizQuestion(
            "에코백을 오래 사용할수록 환경에 더 도움이 된다.",
            listOf("O", "X"),
            0,
            "에코백을 오래 사용하면 일회용 비닐봉지 사용을 줄여 환경에 기여합니다."
        ),
        QuizQuestion(
            "음식물 쓰레기를 줄이는 것도 탄소배출을 줄이는 데 도움이 된다.",
            listOf("O", "X"),
            0,
            "음식물 쓰레기가 줄면 분해 과정에서 발생하는 메탄 가스 배출이 감소합니다."
        ),
        QuizQuestion(
            "전자레인지는 전기를 가장 많이 쓰는 가전제품이다.",
            listOf("O", "X"),
            1,
            "전자레인지보다 에어컨이나 히터 같은 가전이 전기를 더 많이 소모합니다."
        ),
        QuizQuestion(
            "일회용 종이컵은 종이라서 재활용이 잘 된다.",
            listOf("O", "X"),
            1,
            "일회용 종이컵은 플라스틱 코팅 때문에 재활용이 어려운 경우가 많습니다."
        ),
        QuizQuestion(
            "대중교통을 이용하면 탄소 배출을 줄일 수 있다.",
            listOf("O", "X"),
            0,
            "대중교통은 개인 차량보다 1인당 탄소 배출량이 적어 환경에 도움이 됩니다."
        ),
        QuizQuestion(
            "LED 전구는 일반 전구보다 에너지를 더 많이 소비한다.",
            listOf("O", "X"),
            1,
            "LED 전구는 일반 전구보다 에너지 효율이 높아 전기를 덜 소비합니다."
        ),
        QuizQuestion(
            "플라스틱 병은 라벨을 제거하고 버리는 것이 좋다.",
            listOf("O", "X"),
            0,
            "라벨을 제거하면 재활용 과정에서 플라스틱 분리가 쉬워집니다."
        ),
        QuizQuestion(
            "기후 변화는 생물 다양성에 영향을 미친다.",
            listOf("O", "X"),
            0,
            "기후 변화로 서식지 변화와 멸종 위험이 커져 생물 다양성이 감소합니다."
        ),
        QuizQuestion(
            "집에 나무를 심는 것은 탄소를 흡수하는 데 도움이 된다.",
            listOf("O", "X"),
            0,
            "나무는 광합성을 통해 이산화탄소를 흡수하여 탄소 배출을 줄이는 데 기여합니다."
        )
    )

    private var currentQuestionIndex = 0
    private var questionAnswersIndex = 0
    private var maxQuestion = 10

    private lateinit var shuffledQuestions: MutableList<QuizQuestion>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // UI 요소 초기화
        progress = findViewById(R.id.progress_Monthly)
        progressText = findViewById(R.id.progress_Text)
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
            shuffledQuestions = quizData.shuffled().toMutableList()
            currentQuestionIndex = 0
            showQuestion()
            resultText.text = ""
            progress.progress = currentQuestionIndex
            progressText.setText("$currentQuestionIndex/10")
            nextButton.visibility = View.GONE
            enableOptions()
        }

        shuffledQuestions = quizData.shuffled().toMutableList()
        // 첫 번째 질문 표시
        showQuestion()

        // 퀴즈 버튼 리스너
        option1.setOnClickListener { checkAnswer(0) }
        option2.setOnClickListener { checkAnswer(1) }
        option3.setOnClickListener { checkAnswer(2) }
        option4.setOnClickListener { checkAnswer(3) }
        nextButton.setOnClickListener {
            currentQuestionIndex++
            if (currentQuestionIndex < maxQuestion) {
                showQuestion()
                resultText.text = ""
                nextButton.visibility = View.GONE
                progress.progress = currentQuestionIndex
                progressText.setText("$currentQuestionIndex/10")
                enableOptions()
            } else {
                progress.progress = currentQuestionIndex
                progressText.setText("$currentQuestionIndex/10")
                questionAnswersIndex *= 10
                questionText.text = "점수 : $questionAnswersIndex"
                if(questionAnswersIndex == 100){
                    //추후 추가 예정
                }
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
        val currentQuestion = shuffledQuestions[currentQuestionIndex]
        questionText.text = currentQuestion.question

        // 모든 버튼을 기본적으로 숨김
        goneOption()
        resultText.visibility = View.GONE

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
        val currentQuestion = shuffledQuestions[currentQuestionIndex]
        goneOption()
        resultText.visibility = View.VISIBLE
        if (selectedOption == currentQuestion.correctAnswer) {
            resultText.text = "정답입니다!\n${currentQuestion.result}"
            questionAnswersIndex++
        } else {
            resultText.text = "오답입니다.\n${currentQuestion.result}"
        }
        nextButton.visibility = View.VISIBLE
        disableOptions()
    }

    private fun goneOption(){
        option1.visibility = View.GONE
        option2.visibility = View.GONE
        option3.visibility = View.GONE
        option4.visibility = View.GONE
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
    val correctAnswer: Int,
    val result: String
)