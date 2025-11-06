package com.doublethinksolutions.osp.network

import com.doublethinksolutions.osp.upload.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Defines the API endpoints for media-related operations, such as uploads.
 */
interface MediaApiService {

    @Multipart
    @POST("media")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody?,
        @Part("signature") signature: RequestBody,
        @Part("public_key") publicKey: RequestBody,
        @Part("media_hash") mediaHash: RequestBody,
        @Part("metadata_hash") metadataHash: RequestBody,
        @Part("attestation_chain") attestationChain: RequestBody? = null
    ): Response<UploadResponse>
}
