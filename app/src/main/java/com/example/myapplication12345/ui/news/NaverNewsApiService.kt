package com.example.myapplication12345.ui.news

import com.example.myapplication12345.ui.news.NewsResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NaverNewsApiService {
    @GET("v1/search/news.json")
    suspend fun getNews(
        @Header("X-Naver-Client-Id") clientId: String,
        @Header("X-Naver-Client-Secret") clientSecret: String,
        @Query("query") query: String,
        @Query("sort") sort: String = "date", // 최신순 정렬
        @Query("display") display: Int = 1 // 1개만 반환
    ): NewsResponse
}