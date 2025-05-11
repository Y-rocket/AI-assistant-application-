package com.example.uofcanadaai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.uofcanadaai.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ChatListAdapter(
    private var chats: List<Chat>,
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size

    fun updateChats(newChats: List<Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.tvChatTitle.text = chat.title
            binding.tvLastMessage.text = chat.lastMessage
            binding.tvLastMessageTime.text = formatDate(chat.timestamp)

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }

        private fun formatDate(timestamp: com.google.firebase.Timestamp): String {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return dateFormat.format(timestamp.toDate())
        }
    }
}