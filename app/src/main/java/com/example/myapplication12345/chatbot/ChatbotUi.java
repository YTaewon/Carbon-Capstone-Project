package com.example.myapplication12345.chatbot;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication12345.R;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class ChatbotUi extends AppCompatActivity {

    private static final String TAG = "ChatbotUi";
    RecyclerView recyclerView;
    ChatMsgAdapter adapter;
    ImageButton btnSend;
    ImageView backButton;
    EditText etMsg;
    ProgressBar progressBar;
    List<ChatMsg> chatMsgList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        //뷰 객체 연결
        recyclerView = findViewById(R.id.recyclerView);
        btnSend = findViewById(R.id.btn_send);
        etMsg = findViewById(R.id.et_msg);
        progressBar = findViewById(R.id.progressBar);
        backButton = findViewById(R.id.back_button);

        //채팅 메시지 데이터를 담을 list 생성
        chatMsgList = new ArrayList<>();
        //리사이클러뷰 초기화
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new ChatMsgAdapter();
        adapter.setDataList(chatMsgList);
        recyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        //EditText 객체에 text가 변경될 때 실행될 리스너 설정
        etMsg.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                //입력창에 메시지가 입력되었을 때만 버튼이 클릭 가능하도록 설정
                btnSend.setEnabled(s.length() > 0);
            }
        });


        //메시지 전송버튼 클릭 리스너 설정 (람다식으로 작성함)
        btnSend.setOnClickListener(v -> {
            //etMsg에 쓰여있는 텍스트를 가져옵니다.
            String msg = etMsg.getText().toString();
            //새로운 ChatMsg 객체를 생성하여 어댑터에 추가합니다.
            ChatMsg chatMsg = new ChatMsg(ChatMsg.ROLE_USER, msg);
            adapter.addChatMsg(chatMsg);
            //etMsg의 텍스트를 초기화합니다.
            etMsg.setText(null);
            //키보드를 내립니다.
            InputMethodManager manager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

            //응답 기다리는 동안 로딩바 보이게 하기
            progressBar.setVisibility(View.VISIBLE);
            //응답 기다리는 동안 화면 터치 막기
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            //Retrofit으로 요청 보내고 응답받기
            sendMsgToChatGPT();

        });
    }

    /**
     * Context를 사용하여 AndroidManifest.xml의 meta-data에서 OpenAI API 키를 읽어옵니다.
     *
     * @param context 컨텍스트 객체
     * @return OpenAI API 키 문자열. 찾지 못하거나 오류 발생 시 null 반환.
     */
    public static String getOpenAiApiKey(Context context) {
        // AndroidManifest.xml의 <meta-data> 태그에 지정된 android:name 값
        final String META_DATA_KEY = "com.example.myapplication12345.OPENAI_API_KEY";

        if (context == null) {
            Timber.tag(TAG).e("Context is null. Cannot retrieve API key.");
            // 또는 Log.e(TAG, "Context is null. Cannot retrieve API key.");
            return null;
        }

        try {
            // PackageManager 가져오기
            PackageManager packageManager = context.getPackageManager();
            // 앱의 패키지 이름 가져오기
            String packageName = context.getPackageName();

            // ApplicationInfo 가져오기 (메타데이터 포함)
            ApplicationInfo appInfo = packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.GET_META_DATA // 메타데이터 포함 플래그
            );

            // 메타데이터 번들 가져오기 (null 체크 포함)
            Bundle bundle = appInfo.metaData;
            if (bundle != null) {
                // 번들에서 키 값 가져오기 (키가 없거나 값이 null이면 null 반환)
                String apiKey = bundle.getString(META_DATA_KEY);
                if (apiKey == null) {
                    Timber.tag(TAG).w("Meta-data key not found or value is null.");
                    // 또는 Log.w(TAG, "Meta-data key '" + META_DATA_KEY + "' not found or value is null.");
                }
                return apiKey; // 찾은 값 또는 null 반환
            } else {
                Timber.tag(TAG).w("Meta-data bundle is null. Cannot retrieve key.");
                // 또는 Log.w(TAG, "Meta-data bundle is null. Cannot retrieve key: " + META_DATA_KEY);
                return null;
            }

        } catch (PackageManager.NameNotFoundException e) {
            // 패키지를 찾지 못한 경우
            Timber.tag(TAG).e(e, "Failed to load meta-data, package not found");
            // 또는 Log.e(TAG, "Failed to load meta-data, package not found: " + context.getPackageName(), e);
            return null;
        } catch (NullPointerException e) {
            // appInfo.metaData가 null일 때 bundle.getString 호출 시 발생 가능성 (이론상 위 null 체크로 방지)
            Timber.tag(TAG).e(e, "Failed to load meta-data due to NullPointerException for key");
            // 또는 Log.e(TAG, "Failed to load meta-data due to NullPointerException for key: " + META_DATA_KEY, e);
            return null;
        }
    }


    private void sendMsgToChatGPT() {
        String apiKey = getOpenAiApiKey(this);

        ChatGPTApi api = ApiClient.getChatGPTApi(apiKey);

        ChatGPTRequest request = new ChatGPTRequest(
                "gpt-3.5-turbo",
                chatMsgList
        );

        api.getChatResponse(request).enqueue(new Callback<ChatGPTResponse>() {
            @Override
            public void onResponse(Call<ChatGPTResponse> call, Response<ChatGPTResponse> response) {
                //응답을 성공적으로 받은 경우
                if (response.isSuccessful() && response.body() != null) {
                    //응답에서 gpt 답변 가져오기
                    String chatResponse = response.body().getChoices().get(0).getMessage().content;
                    //리사이클러뷰에 답변 추가하기
                    adapter.addChatMsg(new ChatMsg(ChatMsg.ROLE_ASSISTANT, chatResponse));
                    //로딩바 숨기기
                    progressBar.setVisibility(View.GONE);
                    //화면 터치 차단 해제
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                } else {
                    //응답 오류
                    Log.e("getChatResponse", "Error: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<ChatGPTResponse> call, Throwable t) {
                //응답 오류
                Log.e("getChatResponse", "onFailure: ", t);
            }
        });
    }
}