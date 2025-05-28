# 🎓 UofCanada Companion App

An AI-powered Kotlin Android application designed to support university students at the University of Canada. It allows students to chat with an AI about their uploaded lecture materials, manage file uploads, and track previous sessions—all with Firebase integration and local notification support.

---

## 🚀 Features

### 🔐 User Authentication
- Firebase Email/Password Authentication
- User registration and login screens
- Stores user info in Firestore

### 💬 AI Chat Assistant
- Students can ask questions or discuss uploaded lectures
- Integrated with **OpenAI GPT-3.5** or **Gemini API** (free tier)
- Each session and message stored in Firebase Firestore:

### 📁 Lecture File Uploads
- Users can upload PDF, image, or doc files
- Files are stored in **Firebase Storage**
- Metadata saved in Firestore:
### 🔔 Local Notifications
- On successful file upload, the app shows a **local notification** to the user.

### 📋 Session-Based File Management
- Each prompt session has access to previously uploaded files
- Users can view and add files during any chat session
- Uploaded file list is always accessible

### 🔍 Additional Features
- Chat list with titles (ChatListFragment)
- Message search functionality
- Beautiful RecyclerView-based chat UI

---

## 📱 Screens

- **Login/Register Screens**
- **Chat Screen** – AI conversation interface
- **Uploads Screen** – File list and upload area
- **Chat List Screen** – View history of previous conversations

---

## 🧠 Tech Stack

- **Kotlin** (Jetpack Components, MVVM)
- **Firebase Authentication** – login and signup
- **Firebase Firestore** – chat and file metadata
- **GeminiAPI** – for AI API integration
- **Local Notifications** – Android Notification Manager
- **RecyclerView** – to display chat messages and file lists
## 🔓 License
- This project is licensed under the MIT License. Feel free to fork and contribute!

