package com.example.myapplication12345.ui.news

import android.text.Html
import androidx.core.text.HtmlCompat

// 뉴스 항목 모델
data class NewsItem(
    // API에서 받아온 원본, HTML 인코딩된 문자열
    val title: String,
    val description: String, // API에서 받아온 원본 설명
    val originallink: String,
    val pubDate: String
) {
    // Data Binding에서 이 속성에 접근하여 디코딩된 제목을 가져올 수 있습니다.
    val decodedTitle: String
        get() = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    // 필요한 경우 설명(description)도 디코딩합니다.
    val decodedDescription: String
        get() = HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
}

// API 응답 모델
data class NewsResponse(
    val items: List<NewsItem>
)