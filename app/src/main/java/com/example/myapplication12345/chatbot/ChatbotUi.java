package com.example.myapplication12345.chatbot;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
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

public class ChatbotUi extends AppCompatActivity {
    RecyclerView recyclerView;
    ChatMsgAdapter adapter;
    ImageButton btnSend;
    EditText etMsg;
    ProgressBar progressBar;
    List<ChatMsg> chatMsgList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatui);

        //뷰 객체 연결
        recyclerView = findViewById(R.id.recyclerView);
        btnSend = findViewById(R.id.btn_send);
        etMsg = findViewById(R.id.et_msg);
        progressBar = findViewById(R.id.progressBar);

        //채팅 메시지 데이터를 담을 list 생성
        chatMsgList = new ArrayList<>();
        //리사이클러뷰 초기화
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new ChatMsgAdapter();
        adapter.setDataList(chatMsgList);
        recyclerView.setAdapter(adapter);


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

    private void sendMsgToChatGPT() {
        ChatGPTApi api = ApiClient.getChatGPTApi();

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