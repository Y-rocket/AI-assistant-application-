package com.example.uofcanadaai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.uofcanadaai.databinding.ItemFileBinding
import java.text.SimpleDateFormat
import java.util.Locale

class FileAdapter(
    private val files: List<UploadedFile>,
    private val onFileClick: (UploadedFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size

    inner class FileViewHolder(private val binding: ItemFileBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(file: UploadedFile) {
            binding.tvFileName.text = file.name
            binding.tvFileSize.text = formatFileSize(file.size)
            binding.tvUploadTime.text = formatDate(file.uploadTime)
            
            itemView.setOnClickListener {
                onFileClick(file)
            }
        }

        private fun formatFileSize(size: Long): String {
            val kb = size / 1024
            val mb = kb / 1024
            return when {
                mb > 0 -> "$mb MB"
                kb > 0 -> "$kb KB"
                else -> "$size B"
            }
        }

        private fun formatDate(timestamp: com.google.firebase.Timestamp): String {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return dateFormat.format(timestamp.toDate())
        }
    }
} 