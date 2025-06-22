package com.example.myapplication12345.chatbot

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication12345.R
import com.example.myapplication12345.databinding.ActivityChatbotBinding
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class ChatbotUi : AppCompatActivity() {

    // ViewBinding을 사용하여 UI 요소에 안전하게 접근
    private lateinit var binding: ActivityChatbotBinding
    private lateinit var adapter: ChatMsgAdapter
    private val chatMsgList: MutableList<ChatMsg> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding 초기화
        binding = ActivityChatbotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        setupTextWatcher()
    }

    private fun setupRecyclerView() {
        adapter = ChatMsgAdapter()
        adapter.setDataList(chatMsgList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.btnSend.setOnClickListener {
            val msg = binding.etMsg.text.toString().trim() // 공백 제거 추가
            if (msg.isEmpty()) return@setOnClickListener

            // 1. 액티비티가 관리하는 리스트에 먼저 메시지를 추가합니다.
            val userMessage = ChatMsg(ChatMsg.ROLE_USER, msg)
            chatMsgList.add(userMessage)

            // 2. 어댑터에게 마지막 아이템이 추가되었음을 알립니다.
            adapter.notifyItemInserted(chatMsgList.size - 1)

            // UI 업데이트
            binding.etMsg.text.clear()
            hideKeyboard()
            scrollToBottom()

            // 3. 이제 리스트에 메시지가 담긴 상태로 API를 호출합니다.
            sendMsgToChatGPT()
        }
    }

    private fun setupTextWatcher() {
        // KTX 확장 함수를 사용하여 TextWatcher를 간결하게 구현
        binding.etMsg.doAfterTextChanged { text ->
            binding.btnSend.isEnabled = text.toString().isNotEmpty()
        }
    }

    private fun sendMsgToChatGPT() {
        // 로딩 UI 시작
        binding.progressBar.visibility = View.VISIBLE
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        val apiKey = getOpenAiApiKey(this)
        // API 키가 없는 경우 처리
        if (apiKey == null) {
            Timber.e("OpenAI API Key is not found.")
            handleError("API 키를 찾을 수 없습니다.")
            return
        }

        val api = ApiClient.getChatGPTApi(apiKey)
        val request = ChatGPTRequest("gpt-3.5-turbo", chatMsgList)

        api.getChatResponse(request).enqueue(object : Callback<ChatGPTResponse> {
            override fun onResponse(call: Call<ChatGPTResponse>, response: Response<ChatGPTResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    // --- 성공 로직 (기존과 동일) ---
                    val chatResponse = response.body()?.choices?.firstOrNull()?.message?.content ?: "응답이 없습니다."
                    val botMessage = ChatMsg(ChatMsg.ROLE_ASSISTANT, chatResponse)
                    adapter.addChatMsg(botMessage)
                    scrollToBottom()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("GPT 응답 오류: $errorBody")

                    var errorMessage = "응답을 받지 못했습니다. (에러: ${response.code()})"

                    // 오류 JSON 파싱 시도
                    if (errorBody != null) {
                        try {
                            val gson = Gson()
                            val errorResponse = gson.fromJson(errorBody, ErrorResponse::class.java)
                            errorResponse.errorDetail?.message?.let {
                                errorMessage = it // 챗봇이 말하는 형식이므로 상세 메시지만 전달
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "오류 응답 JSON 파싱 실패")
                        }
                    }
                    // 기존 handleError 함수를 사용하여 오류 메시지를 채팅창에 표시
                    handleError(errorMessage)
                }
                // 로딩 UI 종료
                hideLoading()
            }

            override fun onFailure(call: Call<ChatGPTResponse>, t: Throwable) {
                Timber.e(t, "onFailure")
                handleError("네트워크 오류가 발생했습니다.")
                // 로딩 UI 종료
                hideLoading()
            }
        })
    }

    private fun handleError(errorMessage: String) {
        val botMessage = ChatMsg(ChatMsg.ROLE_ASSISTANT, errorMessage)
        adapter.addChatMsg(botMessage)
        scrollToBottom()
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun scrollToBottom() {
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    companion object {
        private const val TAG = "ChatbotUi"

        /**
         * Context를 사용하여 AndroidManifest.xml의 meta-data에서 OpenAI API 키를 읽어옵니다.
         */
        fun getOpenAiApiKey(context: Context): String? {
            val metaDataKey = "com.example.myapplication12345.OPENAI_API_KEY"
            return try {
                val appInfo = context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA
                )
                appInfo.metaData?.getString(metaDataKey)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.tag(TAG).e(e, "Failed to load meta-data, package not found.")
                null
            }
        }
    }
}