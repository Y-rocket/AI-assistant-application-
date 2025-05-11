package com.example.uofcanadaai

import com.google.firebase.Timestamp

data class UploadedFile(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val userId: String = "",
    val uploadTime: Timestamp = Timestamp.now(),
    val size: Long = 0,
    val type: String = ""
) 