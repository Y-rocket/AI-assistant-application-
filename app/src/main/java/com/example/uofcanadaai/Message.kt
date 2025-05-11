package com.example.uofcanadaai

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val text: String = "",
    val sender: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isUser: Boolean = true,
    val fileUrl: String? = null,
    val fileName: String? = null
) 