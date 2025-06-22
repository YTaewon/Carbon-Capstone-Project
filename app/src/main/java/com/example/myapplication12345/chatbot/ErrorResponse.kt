package com.example.myapplication12345.chatbot

import com.google.gson.annotations.SerializedName

/**
 * OpenAI API의 오류 응답을 파싱하기 위한 데이터 클래스
 */
data class ErrorResponse(
    @SerializedName("error")
    val errorDetail: ErrorDetail?
)

data class ErrorDetail(
    @SerializedName("message")
    val message: String?,

    @SerializedName("type")
    val type: String?,

    @SerializedName("code")
    val code: String?
)