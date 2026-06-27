package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileType: String, // "PDF", "XLSX", "DOCX", "TXT"
    val content: String,
    val lastModified: Long = System.currentTimeMillis(),
    val sizeInKb: Int = 0,
    val summary: String = ""
)
