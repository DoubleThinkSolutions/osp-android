package com.doublethinksolutions.osp.ui

import java.util.UUID

// Enum to represent the status of an upload
enum class UploadStatus {
    QUEUED, UPLOADING, SUCCESS, FAILED, SIGNING
}

// Data class to hold the final results of a successful upload
data class UploadResult(
    val trustScore: Double,
    val uploadTimeMs: Long,
    val fileSizeBytes: Long
)

// The main data class representing a single item in our upload queue
data class UploadItem(
    val id: String = UUID.randomUUID().toString(), // Unique ID for each upload
    val fileName: String,
    val status: UploadStatus = UploadStatus.QUEUED,
    val progress: Float = 0f, // 0.0f to 1.0f
    val result: UploadResult? = null,
    val errorMessage: String? = null
)
