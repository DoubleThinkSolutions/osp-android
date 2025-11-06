package com.doublethinksolutions.osp.upload

import android.content.Context
import android.os.Build
import android.util.Log
import com.doublethinksolutions.osp.broadcast.AuthEvent
import com.doublethinksolutions.osp.broadcast.AuthEventBus
import com.doublethinksolutions.osp.data.DeviceOrientation
import com.doublethinksolutions.osp.data.PhotoMetadata
import com.doublethinksolutions.osp.data.SerializableDeviceOrientation
import com.doublethinksolutions.osp.data.SerializablePhotoMetadata
import com.doublethinksolutions.osp.network.NetworkClient
import com.doublethinksolutions.osp.signing.MediaSigner
import com.doublethinksolutions.osp.signing.SignaturePackage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

/**
 * Data class to model the JSON response from the backend upon successful upload.
 */
data class UploadResponse(
    val id: String,
    val capture_time: String,
    val lat: Double,
    val lng: Double,
    val orientation: DeviceOrientation,
    val trust_score: Double,
    val user_id: String,
    val file_path: String,
    val verification_status: String
)

object UploadManager {
    private const val TAG = "UploadManager"

    suspend fun upload(
        context: Context,
        file: File,
        metadata: SerializablePhotoMetadata?,
        signaturePackage: SignaturePackage,
        onProgress: (Float) -> Unit,
        onSuccess: (responseData: UploadResponse, durationMs: Long) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        var attempt = 0
        val maxAttempts = 2

        while (attempt < maxAttempts) {
            attempt++
            try {
                performUpload(context, file, metadata, onProgress, onSuccess, onFailure)
                // If it succeeds, we're done.
                return
            } catch (e: IOException) {
                // Check if this exception is a 401 Unauthorized
                // We need to parse the message as Retrofit wraps the HTTP status in the exception.
                val isAuthError = e.message?.contains("code: 401") == true

                if (isAuthError && attempt < maxAttempts) {
                    Log.d(TAG, "Upload failed with 401, waiting for token refresh event...")

                    // Wait for the authenticator to do its job.
                    // Use a timeout in case something goes wrong with the event bus.
                    val refreshEvent = withTimeoutOrNull(5000) { // 5-second timeout
                        AuthEventBus.events.filterIsInstance<AuthEvent.TokenRefreshed>().first()
                    }

                    if (refreshEvent != null) {
                        Log.d(TAG, "Token refreshed event received. Retrying upload...")
                        // The loop will now continue for the next attempt.
                        continue
                    } else {
                        Log.w(TAG, "Timed out waiting for token refresh. Failing upload.")
                        // Fall through to the failure case.
                    }
                }

                // If it's not an auth error or we've exhausted retries, fail.
                Log.e(TAG, "Upload failed permanently for ${file.name}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
                return
            }
        }
    }

    private suspend fun performUpload(
        context: Context,
        file: File,
        metadata: SerializablePhotoMetadata?,
        onProgress: (Float) -> Unit,
        onSuccess: (responseData: UploadResponse, durationMs: Long) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uploadStartTimestamp = System.currentTimeMillis()
        if (!file.exists()) throw IOException("File does not exist: ${file.path}")
        if (metadata == null) throw IllegalArgumentException("Metadata cannot be null.")

        // 1. Serialize the full, rich metadata object into our canonical JSON string.
        val metadataJsonString = Json.encodeToString(metadata)
        Log.d(TAG, "Canonical metadata for signing/upload: $metadataJsonString")

        // 2. Sign the data using this exact JSON string.
        val signaturePackage = MediaSigner.sign(file, metadataJsonString)
            ?: throw IOException("Failed to sign media. Signature package was null.")

        // 3. Use the same string for the request body.
        val metadataRequestBody = metadataJsonString.toRequestBody("application/json".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, metadataRequestBody)

        val signatureRequestBody = signaturePackage.signature.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val publicKeyRequestBody = signaturePackage.publicKey.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        // Hashes are sent as hex strings for easy handling on the backend
        val mediaHashRequestBody = signaturePackage.mediaHash.toHexString().toRequestBody("text/plain".toMediaTypeOrNull())
        val metadataHashRequestBody = signaturePackage.metadataHash.toHexString().toRequestBody("text/plain".toMediaTypeOrNull())
        val attestationChainRequestBody =
            signaturePackage.attestationChain?.joinToString(",") { it.toHexString() }?.toRequestBody("text/plain".toMediaTypeOrNull())

        Log.d(TAG, "Starting upload for ${file.name} with signature package...")
        val response = NetworkClient.mediaApiService.uploadMedia(
            file = filePart,
            metadata = metadataRequestBody,
            signature = signatureRequestBody,
            publicKey = publicKeyRequestBody,
            mediaHash = mediaHashRequestBody,
            metadataHash = metadataHashRequestBody,
            attestationChain = attestationChainRequestBody
        )

        if (response.isSuccessful) {
            val responseData = response.body()
            if (responseData != null) {
                val uploadDurationMs = System.currentTimeMillis() - uploadStartTimestamp
                Log.d(TAG, "Upload successful for ${file.name} in $uploadDurationMs ms.")
                withContext(Dispatchers.Main) {
                    onSuccess(responseData, uploadDurationMs)
                }
            } else {
                // Handle cases like a 204 No Content or an empty body
                val e = IOException("Upload succeeded but response body was null.")
                Log.e(TAG, e.message, e)
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        } else {
            val errorBody = response.errorBody()?.string() ?: "Unknown error"
            // This is how we bubble up the error to the calling function's catch block
            throw IOException("Upload failed with code: ${response.code()}. Body: $errorBody")
        }
    }
}

private fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream" // A generic binary type
    }
}

// Extension function to wrap a File in our ProgressRequestBody
private fun File.asProgressRequestBody(
    contentType: MediaType?,
    onProgress: (Float) -> Unit
): RequestBody {
    return object : RequestBody() {
        override fun contentType() = contentType

        override fun contentLength() = length()

        override fun writeTo(sink: BufferedSink) {
            val source = source().buffer()
            var totalBytesWritten = 0L
            val fileLength = contentLength()
            var bytesRead: Long

            while (source.read(sink.buffer, 8192L).also { bytesRead = it } != -1L) {
                totalBytesWritten += bytesRead
                sink.flush()
                onProgress(totalBytesWritten.toFloat() / fileLength)
            }
        }
    }
}
