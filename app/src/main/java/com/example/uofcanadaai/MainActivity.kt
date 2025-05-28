package com.example.uofcanadaai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uofcanadaai.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.util.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.app.AlertDialog
import android.widget.EditText
import org.json.JSONObject
import com.example.uofcanadaai.UploadedFile

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val TAG = "MainActivity"
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var currentChatId: String? = null
    private val REQUEST_CODE_PICK_FILE = 1001
    private val geminiClient = GeminiClient()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var lastUploadedFile: File? = null
    private var lastUploadedMimeType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (auth.currentUser == null) {
            Log.d(TAG, "No user logged in, redirecting to login")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        setupMessageInput()
        showChatList()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)

        // Add menu items
        val menu = binding.navigationView.menu
        menu.add(0, R.id.nav_chats, 0, "Chats")
            .setIcon(android.R.drawable.ic_dialog_email)
        menu.add(0, R.id.nav_uploads, 1, "Uploads")
            .setIcon(android.R.drawable.ic_menu_upload)
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
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

        binding.btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_chats -> {
                showChatList()
            }
            R.id.nav_uploads -> {
                showUploads()
            }
            R.id.nav_logout -> {
                logout()
            }
        }
        binding.drawerLayout.closeDrawers()
        return true
    }

    private fun showChatList() {
        supportActionBar?.title = "Chats"
        replaceFragment(ChatListFragment())
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.GONE
        binding.inputContainer.visibility = View.GONE
    }

    private fun showUploads() {
        supportActionBar?.title = "Uploads"
        replaceFragment(UploadsFragment())
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.GONE
        binding.inputContainer.visibility = View.GONE
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

 /*   private fun hideMessageInput() {
        binding.inputContainer.visibility = View.GONE
        binding.rvMessages.visibility = View.GONE
    }

    private fun showMessageInput() {
        binding.inputContainer.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.VISIBLE
    }*/

    fun loadMessages(chatId: String) {
        currentChatId = chatId
        supportActionBar?.title = "Chat"
        binding.fragmentContainer.visibility = View.GONE
        binding.rvMessages.visibility = View.VISIBLE
        binding.inputContainer.visibility = View.VISIBLE
        messages.clear()
        messageAdapter.notifyDataSetChanged()

        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading messages: ${e.message}")
                    return@addSnapshotListener
                }

                messages.clear()
                snapshot?.documents?.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    if (message != null) {
                        messages.add(message)
                    }
                }
                messageAdapter.notifyDataSetChanged()
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }
    }

    private fun sendMessage(text: String) {
        val userId = auth.currentUser?.uid ?: return
        val chatId = currentChatId ?: return

        val message = Message(
            text = text,
            timestamp = Timestamp.now(),
            isUser = true
        )

        db.collection("users").document(userId)
            .collection("chats").document(chatId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {
                binding.etMessage.text.clear()
                updateLastMessage(chatId, text)


                if (lastUploadedFile != null && lastUploadedMimeType != null) {
                    sendToGemini(lastUploadedFile!!, lastUploadedMimeType!!, text)
                    lastUploadedFile = null
                    lastUploadedMimeType = null
                } else {
                    coroutineScope.launch {
                        try {
                            val aiResponse = geminiClient.generateResponse(text)
                            val aiMessage = Message(
                                text = aiResponse,
                                timestamp = Timestamp.now(),
                                isUser = false
                            )

                            db.collection("users").document(userId)
                                .collection("chats").document(chatId)
                                .collection("messages")
                                .add(aiMessage)
                                .addOnSuccessListener {
                                    updateLastMessage(chatId, aiResponse)
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting AI response: ${e.message}")
                            Toast.makeText(this@MainActivity, "Error getting AI response", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending message: ${e.message}")
                Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastMessage(chatId: String, message: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("chats").document(chatId)
            .update(
                mapOf(
                    "lastMessage" to message,
                    "timestamp" to Timestamp.now()
                )
            )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    private fun uploadFile(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val fileName = UUID.randomUUID().toString()
        val originalFileName = uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"

        try {
            val appDir = File(getExternalFilesDir(null), "uploads")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            val file = File(appDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val message = Message(
                text = "File: $originalFileName",
                sender = userId,
                isUser = true,
                timestamp = Timestamp.now(),
                fileUrl = file.absolutePath,
                fileName = originalFileName
            )
            sendMessageWithFile(message)
            showUploadNotification(originalFileName)

            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"


            lastUploadedFile = file
            lastUploadedMimeType = mimeType

            val uploadedFile = UploadedFile(
                id = fileName,
                name = originalFileName,
                url = file.absolutePath,
                userId = userId,
                uploadTime = Timestamp.now(),
                size = file.length(),
                type = mimeType
            )
            db.collection("users")
                .document(userId)
                .collection("uploads")
                .document(fileName)
                .set(uploadedFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving file: ${e.message}")
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessageWithFile(message: Message) {
        currentChatId?.let { chatId ->
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener {
                    binding.etMessage.text.clear()

                    coroutineScope.launch {
                        try {
                            val aiResponse = geminiClient.generateResponse("I've shared a file: ${message.fileName}")
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
                            Toast.makeText(this@MainActivity, "Error getting AI response", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error sending message: ${e.message}")
                    Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            createNewChatWithFile(message)
        }
    }

    private fun createNewChatWithFile(message: Message) {
        currentChatId?.let { chatId ->
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener {
                    binding.etMessage.text.clear()
                    coroutineScope.launch {
                        try {
                            val aiResponse = geminiClient.generateResponse("I've shared a file: ${message.fileName}")
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
                            Toast.makeText(this@MainActivity, "Error getting AI response", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error sending message: ${e.message}")
                    Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            createNewChat()
        }
    }

    private fun createNewChat() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "Creating new chat for user: $userId")

        auth.currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val tokenResult = task.result
                val tokenStr = tokenResult?.token
                Log.d(TAG, "User is authenticated with token: ${tokenStr?.take(10)}...")

                val chat = hashMapOf(
                    "title" to "New Chat",
                    "userId" to userId,
                    "createdAt" to Timestamp.now(),
                    "lastMessage" to "",
                    "lastMessageTime" to Timestamp.now()
                )

                Log.d(TAG, "Attempting to create chat with data: $chat")
                db.collection("chats")
                    .add(chat)
                    .addOnSuccessListener { docRef ->
                        Log.d(TAG, "New chat created with ID: ${docRef.id}")
                        currentChatId = docRef.id
                        loadMessages(docRef.id)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error creating new chat: ${e.message}")
                        Toast.makeText(this, "Error creating new chat: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

            } else {
                Log.e(TAG, "Authentication failed: ${task.exception?.message}")
                Toast.makeText(this, "Authentication failed. Please try logging in again.", Toast.LENGTH_SHORT).show()

                // Redirect to login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun showUploadNotification(fileName: String) {
        val channelId = "upload_channel"
        val notificationId = 2

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Upload Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("File Uploaded")
            .setContentText("$fileName has been uploaded successfully")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .build()

        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun logout() {
        try {
            auth.signOut()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout: ${e.message}")
            Toast.makeText(this, "Error during logout: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(binding.navigationView)) {
            binding.drawerLayout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    private fun sendToGemini(file: File, mimeType: String, prompt: String) {
        val apiKey = "Place YOUR API KEY HERE FOR GEMINI"
        val base64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)

        val json = """
        {
          "contents": [
            {
              "role": "user",
              "parts": [
                {
                  "text": "$prompt"
                },
                {
                  "inlineData": {
                    "mimeType": "$mimeType",
                    "data": "$base64"
                  }
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GeminiAPI", "API call failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error processing file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("GeminiAPI", "Response: $responseBody")

                // Parse the response to extract only the text answer
                val aiText = try {
                    val json = JSONObject(responseBody ?: "")
                    val candidates = json.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            parts.getJSONObject(0).getString("text")
                        } else {
                            "Sorry, I couldn't process the file."
                        }
                    } else {
                        "Sorry, I couldn't process the file."
                    }
                } catch (e: Exception) {
                    Log.e("GeminiAPI", "Error parsing response: ${e.message}")
                    "Sorry, I couldn't process the file."
                }

                // Add AI response to chat
                runOnUiThread {
                    currentChatId?.let { chatId ->
                        val aiMessage = Message(
                            text = aiText,
                            timestamp = Timestamp.now(),
                            isUser = false
                        )

                        db.collection("users").document(auth.currentUser?.uid ?: return@runOnUiThread)
                            .collection("chats").document(chatId)
                            .collection("messages")
                            .add(aiMessage)
                            .addOnSuccessListener {
                                updateLastMessage(chatId, aiMessage.text)
                            }
                    }
                }
            }
        })
    }

}
