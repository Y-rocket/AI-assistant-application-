package com.example.uofcanadaai

import com.google.firebase.Timestamp

data class Chat(
    val id: String = "",
    val title: String = "",
    val lastMessage: String = "",
    val timestamp: Timestamp = Timestamp.now()
) 