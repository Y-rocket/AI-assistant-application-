package com.example.uofcanadaai
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uofcanadaai.databinding.FragmentUploadsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONObject

class UploadsFragment : Fragment() {
    private var _binding: FragmentUploadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var fileAdapter: FileAdapter
    private val files = mutableListOf<UploadedFile>()
    private lateinit var Database: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Database = FirebaseFirestore.getInstance()
        setupRecyclerView()
        loadFiles()
        
        binding.btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(files) { file ->
            // Open the file
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(File(file.url)), file.type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = fileAdapter
        }
    }

    private fun loadFiles() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        Database.collection("users")
            .document(userId)
            .collection("uploads")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                files.clear()
                snapshot?.documents?.forEach { doc ->
                    val file = doc.toObject(UploadedFile::class.java)
                    file?.let { files.add(it) }
                }
                fileAdapter.notifyDataSetChanged()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_PICK_FILE && data?.data != null) {
            val fileUri = data.data!!
            uploadFile(fileUri)
        }
    }

    private fun uploadFile(fileUri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = UUID.randomUUID().toString()
        val originalFileName = fileUri.lastPathSegment ?: "Unknown"
        
        try {
            // Create app-specific directory if it doesn't exist
            val appDir = File(requireContext().getExternalFilesDir(null), "uploads")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            // Create the file
            val file = File(appDir, fileName)
            
            // Copy the file
            requireContext().contentResolver.openInputStream(fileUri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val uploadedFile = UploadedFile(
                id = fileName,
                name = originalFileName,
                url = file.absolutePath,
                userId = userId,
                size = file.length(),
                type = context?.contentResolver?.getType(fileUri) ?: ""
            )

            Database.collection("users")
                .document(userId)
                .collection("uploads")
                .document(fileName)
                .set(uploadedFile)
                .addOnSuccessListener {
                    showUploadNotification(originalFileName)
                    showPromptDialog(file, uploadedFile.type)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error saving file metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("UploadsFragment", "Error saving file: ${e.message}")
            Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUploadNotification(fileName: String) {
        val channelId = "upload_channel"
        val notificationId = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Upload Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setContentTitle("File Uploaded")
            .setContentText("$fileName has been uploaded successfully")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .build()

        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1001
    }

    private fun sendToGemini(file: File, mimeType: String, prompt: String) {
        val apiKey = "AIzaSyDUO-x2xal3KGDDDaiXRhbQnaKR1P3Iklw"
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
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error processing file: ${e.message}", Toast.LENGTH_SHORT).show()
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
                activity?.runOnUiThread {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@runOnUiThread
                    val db = FirebaseFirestore.getInstance()
                    
                    // Create a new chat for the file response
                    val chat = hashMapOf(
                        "title" to "File Analysis",
                        "userId" to userId,
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "lastMessage" to aiText,
                        "lastMessageTime" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("chats")
                        .add(chat)
                        .addOnSuccessListener { docRef ->
                            val aiMessage = Message(
                                text = aiText,
                                timestamp = com.google.firebase.Timestamp.now(),
                                isUser = false
                            )

                            db.collection("chats")
                                .document(docRef.id)
                                .collection("messages")
                                .add(aiMessage)
                        }
                }
            }
        })
    }

    private fun showPromptDialog(file: File, mimeType: String) {
        val builder = AlertDialog.Builder(requireContext())
        val input = EditText(requireContext())
        input.hint = "What would you like to know about this file?"
        builder.setTitle("Process File")
            .setView(input)
            .setPositiveButton("Process") { dialog, _ ->
                val prompt = input.text.toString()
                if (prompt.isNotEmpty()) {
                    sendToGemini(file, mimeType, prompt)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.show()
    }
} 