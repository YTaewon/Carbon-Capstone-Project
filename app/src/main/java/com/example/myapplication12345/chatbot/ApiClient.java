package com.example.myapplication12345.chatbot;

import androidx.annotation.NonNull; // androidx 어노테이션 사용 권장
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor; // OkHttp 인터셉터 임포트
import okhttp3.OkHttpClient; // OkHttpClient 임포트
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor; // 로깅 인터셉터 (선택 사항)
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber; // 로깅 사용 예시

public class ApiClient {
    private static final String BASE_URL = "https://api.openai.com/";
    private static final String TAG = "ApiClient";

    // Retrofit 인스턴스 캐싱은 키가 달라질 수 있으므로 주의 필요. 여기서는 매번 생성.
    // private static Retrofit retrofit = null;

    /**
     * 주어진 API 키를 사용하여 ChatGPT API 서비스를 반환합니다.
     * 이 메소드는 인터페이스에 하드코딩된 Authorization 헤더를 덮어씁니다.
     *
     * @param apiKey 사용할 OpenAI API 키
     * @return ChatGPTApi 서비스 인스턴스
     */
    public static ChatGPTApi getChatGPTApi(String apiKey) { // 파라미터 이름 apikey -> apiKey (Java Naming Convention)

        if (apiKey == null || apiKey.isEmpty()) {
            Timber.tag(TAG).e("API Key provided to getChatGPTApi is null or empty. Cannot create authenticated client.");
            // 유효하지 않은 키로 클라이언트 생성 방지
            throw new IllegalArgumentException("API Key cannot be null or empty.");
        }

        // 로깅 인터셉터 설정 (선택 사항)
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").d(message));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient 설정 (AuthHeaderOverwritingInterceptor 추가)
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                // API 키를 사용하여 헤더를 덮어쓰는 인터셉터 추가
                .addInterceptor(new AuthHeaderOverwritingInterceptor(apiKey))
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        // Retrofit 인스턴스 생성 (커스텀 OkHttpClient 사용)
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient) // 인터셉터가 포함된 OkHttpClient 설정!
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // ChatGPTApi 인터페이스의 구현체 생성 및 반환
        // 이 구현체는 요청 시 OkHttpClient를 사용하게 됨
        return retrofit.create(ChatGPTApi.class);
    }

    /**
     * Authorization 헤더를 주어진 API 키로 덮어쓰는 Interceptor.
     */
    private static class AuthHeaderOverwritingInterceptor implements Interceptor {
        private final String apiKey;

        AuthHeaderOverwritingInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request originalRequest = chain.request();

            // API 키 유효성 재확인 (필수는 아님, getChatGPTApi에서 이미 확인)
            if (apiKey == null || apiKey.isEmpty()) {
                Timber.tag(TAG).e("Interceptor: API Key is missing. Proceeding with original headers (including 'mykey').");
                return chain.proceed(originalRequest); // 헤더 변경 없이 진행
            }

            // 새 요청 빌더 생성
            Request.Builder builder = originalRequest.newBuilder();

            // "Authorization" 헤더를 설정합니다.
            // .header() 메소드는 이름이 같은 기존 헤더(예: "Authorization: Bearer mykey")를 제거하고
            // 새로운 헤더("Authorization: Bearer 실제키")를 추가합니다.
            builder.header("Authorization", "Bearer " + apiKey);
            Timber.tag(TAG).i("Interceptor: Overwriting 'Authorization' header.");

            Request newRequest = builder.build();
            return chain.proceed(newRequest);
        }
    }
}