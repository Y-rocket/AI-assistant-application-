package com.example.uofcanadaai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.uofcanadaai.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(
    private var chats: List<Chat>,
    private val onChatClick: (Chat) -> Unit,
    private val onDeleteClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatBinding.inflate(inflater, parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.bind(chat)
    }

    override fun getItemCount(): Int = chats.size

    fun updateChats(newChats: List<Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.tvChatTitle.text = chat.title
            binding.tvLastMessage.text = chat.lastMessage
            binding.tvLastMessageTime.text = formatTimestamp(chat.timestamp)

            binding.root.setOnClickListener {
                onChatClick(chat)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(chat)
            }
        }

        private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return formatter.format(timestamp.toDate())
        }
    }
}
