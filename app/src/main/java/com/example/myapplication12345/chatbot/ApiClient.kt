package com.example.myapplication12345.chatbot

import com.example.myapplication12345.BuildConfig // BuildConfig 임포트
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://api.openai.com/"

    fun getChatGPTApi(apiKey: String): ChatGPTApi {
        // 1. 로깅 인터셉터 생성
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 2. 디버그 빌드일 때만 Body를 로그로 남기고, 릴리스 빌드에서는 로그를 남기지 않음
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.NONE
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // 인증 헤더를 추가하는 인터셉터
        val authInterceptor = Interceptor { chain ->
            val newRequest = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(newRequest)
        }

        // OkHttpClient에 인터셉터 추가
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ChatGPTApi::class.java)
    }
}