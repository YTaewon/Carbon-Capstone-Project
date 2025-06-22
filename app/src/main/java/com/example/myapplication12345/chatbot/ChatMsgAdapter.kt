package com.example.myapplication12345.chatbot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication12345.R

class ChatMsgAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Nullable 대신 초기 빈 리스트를 할당하여 Null-safety를 강화합니다.
    private var dataList: MutableList<ChatMsg> = mutableListOf()

    fun setDataList(list: MutableList<ChatMsg>) {
        this.dataList = list
        notifyDataSetChanged()
    }

    fun addChatMsg(chatMsg: ChatMsg) {
        dataList.add(chatMsg)
        notifyItemInserted(dataList.size - 1)
    }

    // if-else 대신 when 표현식을 사용하여 더 간결하게 표현합니다.
    override fun getItemViewType(position: Int): Int {
        return when (dataList[position].role) {
            ChatMsg.ROLE_USER -> VIEW_TYPE_MY_CHAT
            else -> VIEW_TYPE_BOT_CHAT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MY_CHAT -> {
                val view = inflater.inflate(R.layout.item_my_chat, parent, false)
                MyChatViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_bot_chat, parent, false)
                BotChatViewHolder(view)
            }
        }
    }

    // is 키워드를 사용하여 타입 체크와 동시에 스마트 캐스팅을 활용합니다.
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMsg = dataList[position]
        when (holder) {
            is MyChatViewHolder -> holder.bind(chatMsg)
            is BotChatViewHolder -> holder.bind(chatMsg)
        }
    }

    override fun getItemCount(): Int = dataList.size

    // 내가 보낸 메시지를 띄우기 위한 뷰홀더
    inner class MyChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMsg: TextView = itemView.findViewById(R.id.tv_msg)

        fun bind(chatMsg: ChatMsg) {
            // content가 String일 경우에만 텍스트를 설정 (스마트 캐스팅)
            if (chatMsg.content is String) {
                tvMsg.text = chatMsg.content
            }
        }
    }

    // 챗봇의 메시지를 띄우기 위한 뷰홀더
    inner class BotChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMsg: TextView = itemView.findViewById(R.id.tv_msg)

        fun bind(chatMsg: ChatMsg) {
            if (chatMsg.content is String) {
                tvMsg.text = chatMsg.content
            }
        }
    }

    // 뷰 타입을 상수로 정의하여 가독성을 높입니다.
    companion object {
        private const val VIEW_TYPE_MY_CHAT = 0
        private const val VIEW_TYPE_BOT_CHAT = 1
    }
}