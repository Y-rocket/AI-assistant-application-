package com.example.uofcanadaai

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uofcanadaai.databinding.FragmentChatListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class ChatListFragment : Fragment() {
    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatAdapter: ChatAdapter
    private val chats = mutableListOf<Chat>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val TAG = "ChatListFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadChats()
        setupSearchView()
        setupNewChatButton()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            chats,
            onChatClick = { chat ->
                (activity as? MainActivity)?.loadMessages(chat.id)
            },
            onDeleteClick = { chat ->
                deleteChat(chat)
            }
        )
        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener,
            android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterChats(newText)
                return true
            }
        })
    }

    private fun filterChats(query: String?) {
        val filteredChats = if (query.isNullOrEmpty()) {
            chats
        } else {
            chats.filter { chat ->
                chat.title.contains(query, ignoreCase = true) ||
                        chat.lastMessage.contains(query, ignoreCase = true)
            }
        }
        chatAdapter.updateChats(filteredChats)
    }

    private fun setupNewChatButton() {
        binding.fabNewChat.setOnClickListener {
            showTitleInputDialog()
        }
    }

    private fun showTitleInputDialog() {
        val titleInput = EditText(context).apply {
            hint = "Enter chat title"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(context)
            .setTitle("New Chat")
            .setView(titleInput)
            .setPositiveButton("Create") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title!=null) {
                    createNewChat(title)
                } else {
                    Toast.makeText(context, "please write your title ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewChat(title: String) {
        val userId = auth.currentUser?.uid ?: return
        val chatId = UUID.randomUUID().toString()

        coroutineScope.launch {
            try {
                val timestamp = Timestamp.now()
                val chat = Chat(
                    id = chatId,
                    title = title,
                    lastMessage = "",
                    timestamp = timestamp
                )

                val chatReference = chatRef(userId, chatId)
                chatReference.set(chat).await()

                (activity as? MainActivity)?.loadMessages(chatId)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to create chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteChat(chat: Chat) {
        val userId = auth.currentUser?.uid ?: return
        coroutineScope.launch {
            try {
                val chatRef = chatRef(userId, chat.id)

                // Delete chat document
                chatRef.delete().await()

                // Delete all messages
                val messagesSnapshot = chatRef.collection("messages").get().await()
                for (doc in messagesSnapshot.documents) {
                    doc.reference.delete().await()
                }

                chats.remove(chat)
                chatAdapter.updateChats(chats)
                Toast.makeText(context, "Chat deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to delete chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun chatRef(userId: String, chatId: String) =
        db.collection("users").document(userId).collection("chats").document(chatId)

    private fun loadChats() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "Loading chats for user: $userId")

        db.collection("users").document(userId)
            .collection("chats")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading chats: ${e.message}")
                    Toast.makeText(context, "Error loading chats: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                chats.clear()
                snapshot?.documents?.forEach { doc ->
                    val chat = doc.toObject(Chat::class.java)
                    if (chat != null) {
                        chats.add(chat)
                    }
                }
                chatAdapter.notifyDataSetChanged()
                Log.d(TAG, "Loaded ${chats.size} chats")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
