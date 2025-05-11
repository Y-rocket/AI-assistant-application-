package com.example.uofcanadaai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiClient {
    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = "AIzaSyDUO-x2xal3KGDDDaiXRhbQnaKR1P3Iklw"
    )

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val response: GenerateContentResponse = model.generateContent(prompt)
            response.text ?: "Sorry, I couldn't generate a response."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }


} 