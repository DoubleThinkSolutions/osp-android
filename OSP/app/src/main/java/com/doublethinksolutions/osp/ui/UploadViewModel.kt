package com.doublethinksolutions.osp.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doublethinksolutions.osp.data.PhotoMetadata
import com.doublethinksolutions.osp.data.SerializablePhotoMetadata
import com.doublethinksolutions.osp.signing.MediaSigner
import com.doublethinksolutions.osp.upload.UploadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class UploadViewModel : ViewModel() {

    // Linger duration for a completed item in the main queue before being archived.
    private val LINGER_DURATION_MS = 4000L
    // Maximum number of items to keep in the history.
    private val MAX_HISTORY_SIZE = 5

    // This queue holds active uploads and recently completed ones that are "lingering".
    private val _uploadQueue = MutableStateFlow<List<UploadItem>>(emptyList())
    val uploadQueue: StateFlow<List<UploadItem>> = _uploadQueue.asStateFlow()

    // This holds the archived history of completed uploads.
    private val _uploadHistory = MutableStateFlow<List<UploadItem>>(emptyList())
    val uploadHistory: StateFlow<List<UploadItem>> = _uploadHistory.asStateFlow()

    fun startUpload(context: Context, photoFile: File, metadata: SerializablePhotoMetadata?) {
        val newItem = UploadItem(fileName = photoFile.name)
        // Add to the front of the queue
        _uploadQueue.update { listOf(newItem) + it }

        viewModelScope.launch {
            // --- SIGNING STEP ---
            updateUploadItem(newItem.id) { it.copy(status = UploadStatus.SIGNING) }
            val signaturePackage = metadata?.let { MediaSigner.sign(photoFile, it) }

            if (signaturePackage == null) {
                Log.e("ViewModel", "Signing failed for ${newItem.fileName}")
                updateUploadItem(newItem.id) {
                    it.copy(status = UploadStatus.FAILED, errorMessage = "Signing failed")
                }
                scheduleForArchival(newItem.id)
                return@launch
            }
            Log.d("ViewModel", "Signing successful for ${newItem.fileName}")

            UploadManager.upload(
                context = context,
                file = photoFile,
                metadata = metadata,
                signaturePackage = signaturePackage,
                onProgress = { progress ->
                    updateUploadItem(newItem.id) { it.copy(status = UploadStatus.UPLOADING, progress = progress) }
                },
                onSuccess = { responseData, uploadDurationMs ->
                    Log.d("ViewModel", "Upload success for ${newItem.fileName}. Trust score from server: ${responseData.trust_score}")
                    // Create the result object using data from the server response
                    val result = UploadResult(
                        trustScore = responseData.trust_score,
                        uploadTimeMs = uploadDurationMs,
                        fileSizeBytes = photoFile.length()
                    )
                    updateUploadItem(newItem.id) {
                        it.copy(status = UploadStatus.SUCCESS, progress = 1f, result = result)
                    }
                    // Schedule item to be moved to history after a delay
                    scheduleForArchival(newItem.id)
                },
                onFailure = { exception ->
                    Log.e("ViewModel", "Upload failed for ${newItem.fileName}", exception)
                    updateUploadItem(newItem.id) {
                        it.copy(status = UploadStatus.FAILED, progress = 1f, errorMessage = exception.message ?: "Unknown error")
                    }
                    // Schedule item to be moved to history after a delay
                    scheduleForArchival(newItem.id)
                }
            )
        }
    }

    // The entire trust score calculation method is removed.
    // private fun calculateTrustScore(...) { ... }

    private fun scheduleForArchival(itemId: String) {
        viewModelScope.launch {
            delay(LINGER_DURATION_MS)
            archiveItem(itemId)
        }
    }

    private fun archiveItem(itemId: String) {
        val itemToArchive = _uploadQueue.value.find { it.id == itemId }

        if (itemToArchive != null) {
            // Add item to the beginning of the history list, maintaining max size
            _uploadHistory.update { currentHistory ->
                (listOf(itemToArchive) + currentHistory).take(MAX_HISTORY_SIZE)
            }
            // Remove item from the active/lingering queue
            _uploadQueue.update { currentQueue ->
                currentQueue.filter { it.id != itemId }
            }
        }
    }

    private fun updateUploadItem(id: String, updateAction: (UploadItem) -> UploadItem) {
        _uploadQueue.update { currentList ->
            currentList.map { if (it.id == id) updateAction(it) else it }
        }
    }
}
