package com.example.myapplication12345.chatbot

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatGPTApi {
    @POST("v1/chat/completions")
    fun getChatResponse(@Body request: ChatGPTRequest): Call<ChatGPTResponse>
}