package com.example.myapplication12345.chatbot

import com.google.gson.annotations.SerializedName

// --- GPT 요청(Request) 관련 데이터 클래스 ---

data class ChatGPTRequest(
    val model: String,
    val messages: List<ChatMsg>,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null
)

/**
 * 하나의 메시지를 나타내는 클래스.
 * content는 텍스트(String)가 될 수도 있고, 텍스트와 이미지의 조합(List<ContentPart>)이 될 수도 있다.
 */
data class ChatMsg(
    val role: String,
    val content: Any
) {
    // ★★★★★ 이 부분을 수정합니다 ★★★★★
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant" // 이 줄을 추가합니다.
    }
}

sealed class ContentPart {
    abstract val type: String

    data class Text(
        val text: String
    ) : ContentPart() {
        override val type: String = "text"
    }

    data class Image(
        @SerializedName("image_url")
        val imageUrl: ImageUrl
    ) : ContentPart() {
        override val type: String = "image_url"
    }
}

data class ImageUrl(
    val url: String
)

// --- GPT 응답(Response) 관련 데이터 클래스 ---
data class ChatGPTResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

data class Message(
    val role: String,
    val content: String
)