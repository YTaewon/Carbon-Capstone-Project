package com.example.myapplication12345.chatbot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication12345.R;

import java.util.List;

public class ChatMsgAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    List<ChatMsg> dataList;

    //데이터 리스트를 세팅하기 위한 메서드입니다.
    public void setDataList(List<ChatMsg> dataList) {
        this.dataList = dataList;
        notifyDataSetChanged();
    }

    //채팅 메시지가 추가되었을 때 어댑터에 반영해주기 위한 메서드입니다.
    public void addChatMsg(ChatMsg chatMsg) {
        dataList.add(chatMsg);
        notifyItemInserted(dataList.size());
    }


    //각 아이템의 뷰타입 호출시 ChatMsg 클래스의 role에 따라 내 메시지는 0 / 챗봇의 메시지는 1을 반환하도록 아래와 같이 오버라이드 합니다.
    @Override
    public int getItemViewType(int position) {
        if (dataList.get(position).role.equals(ChatMsg.ROLE_USER)) return 0;
        return 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        // 뷰타입이 0 이면 MyChatViewHolder를 반환
        if (viewType == 0) {
            return new MyChatViewHolder(inflater.inflate(R.layout.item_my_chat, parent, false));
        }
        // 아니면 BotChatViewHolder 반환
        return new BotChatViewHolder(inflater.inflate(R.layout.item_bot_chat, parent, false));
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMsg chatMsg = dataList.get(position);
        if (chatMsg.role.equals(ChatMsg.ROLE_USER)) {
            ((MyChatViewHolder) holder).setMsg(chatMsg);
        } else {
            ((BotChatViewHolder) holder).setMsg(chatMsg);
        }
    }

    @Override
    public int getItemCount() {
        return dataList == null ? 0 : dataList.size();
    }

    // 내가 보낸 메시지를 띄우기 위한 뷰홀더입니다.
    static class MyChatViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMsg;
        public MyChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMsg = itemView.findViewById(R.id.tv_msg);
        }

        public void setMsg(ChatMsg chatMsg) {
            tvMsg.setText(chatMsg.content);
        }
    }

    //챗봇의 메시지를 띄우기 위한 뷰홀더입니다.
    static class BotChatViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMsg;
        public BotChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMsg = itemView.findViewById(R.id.tv_msg);
        }
        public void setMsg(ChatMsg chatMsg) {
            tvMsg.setText(chatMsg.content);
        }
    }
}