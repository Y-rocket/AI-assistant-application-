package com.example.uofcanadaai

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.uofcanadaai.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MessageAdapter(private val messages: List<Message>) : 
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    inner class MessageViewHolder(private val binding: ItemMessageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: Message) {
            if (message.fileUrl != null) {
                binding.tvMessage.text = "📎 ${message.fileName}"
                binding.tvMessage.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.fileUrl))
                    binding.root.context.startActivity(intent)
                }
            } else {
                binding.tvMessage.text = message.text
                binding.tvMessage.setOnClickListener(null)
            }
            
            binding.tvTime.text = formatDate(message.timestamp)
            
            if (message.isUser) {
                binding.messageContainer.setBackgroundResource(R.drawable.user_message_background)
                binding.tvMessage.setTextColor(binding.root.context.getColor(android.R.color.white))
            } else {
                binding.messageContainer.setBackgroundResource(R.drawable.ai_message_background)
                binding.tvMessage.setTextColor(binding.root.context.getColor(android.R.color.black))
            }
        }

        private fun formatDate(timestamp: com.google.firebase.Timestamp): String {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return dateFormat.format(timestamp.toDate())
        }
    }
} 