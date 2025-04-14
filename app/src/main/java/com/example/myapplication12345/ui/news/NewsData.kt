package com.example.myapplication12345.ui.news

// 뉴스 항목 모델
data class NewsItem(
    val title: String,
    val description: String,
    val originallink: String,
    val pubDate: String
)

// API 응답 모델
data class NewsResponse(
    val items: List<NewsItem>
)