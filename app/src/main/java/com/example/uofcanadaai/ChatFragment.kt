package com.example.uofcanadaai

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uofcanadaai.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ChatFragment"
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var currentChatId: String? = null
    private val geminiClient = GeminiClient()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupMessageInput()
        loadMessages()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = messageAdapter
        }
    }

    private fun setupMessageInput() {
        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessage.text.toString()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }
    }

    private fun loadMessages() {
        currentChatId = arguments?.getString("chatId")
        currentChatId?.let { chatId ->
            messages.clear()
            messageAdapter.notifyDataSetChanged()

            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Error loading messages: ${e.message}")
                        return@addSnapshotListener
                    }

                    messages.clear()
                    snapshot?.documents?.forEach { doc ->
                        val message = doc.toObject(Message::class.java)
                        message?.let { messages.add(it) }
                    }
                    messageAdapter.notifyDataSetChanged()
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
        }
    }

    private fun sendMessage(text: String) {
        val userId = auth.currentUser?.uid ?: return
        val userMessage = Message(
            text = text,
            sender = userId,
            isUser = true,
            timestamp = Timestamp.now()
        )

        currentChatId?.let { chatId ->
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(userMessage)
                .addOnSuccessListener {
                    binding.etMessage.text.clear()

                    coroutineScope.launch {
                        try {
                            val aiResponse = geminiClient.generateResponse(text)
                            val aiMessage = Message(
                                text = aiResponse,
                                sender = "AI",
                                isUser = false,
                                timestamp = Timestamp.now()
                            )
                            
                            db.collection("chats")
                                .document(chatId)
                                .collection("messages")
                                .add(aiMessage)
                                .addOnSuccessListener {
                                    // Update last message in chat
                                    db.collection("chats")
                                        .document(chatId)
                                        .update(
                                            mapOf(
                                                "lastMessage" to aiResponse,
                                                "lastMessageTime" to Timestamp.now()
                                            )
                                        )
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting AI response: ${e.message}")
                            Toast.makeText(context, "Error getting AI response", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error sending message: ${e.message}")
                    Toast.makeText(context, "Error sending message", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(chatId: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString("chatId", chatId)
                }
            }
        }
    }
} 