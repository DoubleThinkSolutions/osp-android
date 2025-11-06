package com.doublethinksolutions.osp.signing

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyInfo
import android.util.Log
import androidx.annotation.RequiresApi
import com.doublethinksolutions.osp.data.SerializablePhotoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

data class SignaturePackage(
    val signature: ByteArray,
    val publicKey: ByteArray,
    val mediaHash: ByteArray,
    val metadataHash: ByteArray,
    val algorithm: String = "SHA256withECDSA",
    val attestationChain: List<ByteArray>? = null
)

object MediaSigner {
    private const val KEY_ALIAS = "com.doublethinksolutions.osp.media_signing_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val TAG = "MediaSigner"

    private var isInitialized = false

    @RequiresApi(Build.VERSION_CODES.P)
    fun initialize(context: Context, metadata: SerializablePhotoMetadata? = null) {
        if (isInitialized) return
        try {
            generateSigningKeyIfMissing(context, metadata)
            isInitialized = true
            Log.i(TAG, "MediaSigner initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during MediaSigner initialization", e)
        }
    }

    suspend fun sign(mediaFile: File, metadataJson: String): SignaturePackage? = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.e(TAG, "Signer not initialized. Call MediaSigner.initialize() first.")
            return@withContext null
        }

        try {
            // Hash the provided JSON string directly.
            val metadataHash = calculateSha256(metadataJson.toByteArray(Charsets.UTF_8))

            val mediaHash = when (mediaFile.extension.lowercase()) {
                "mp4" -> MerkleTree.calculateRootHash(mediaFile)
                else -> calculateFileSha256(mediaFile)
            }

            val dataToSign = mediaHash + metadataHash

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: throw GeneralSecurityException("Failed to retrieve private key from Keystore.")

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initSign(privateKeyEntry.privateKey)
                update(dataToSign)
            }.sign()

            val publicKeyBytes = privateKeyEntry.certificate.publicKey.encoded

            // Only attach attestation if hardware-backed
            val attestationChain = try {
                val keyFactory = KeyFactory.getInstance(privateKeyEntry.privateKey.algorithm, "AndroidKeyStore")
                val keyInfo = keyFactory.getKeySpec(privateKeyEntry.privateKey, KeyInfo::class.java)
                if (keyInfo.isInsideSecureHardware) {
                    keyStore.getCertificateChain(KEY_ALIAS)?.map { (it as X509Certificate).encoded }
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Attestation unavailable: ${e.message}")
                null
            }

            return@withContext SignaturePackage(
                signature = signature,
                publicKey = publicKeyBytes,
                mediaHash = mediaHash,
                metadataHash = metadataHash,
                attestationChain = attestationChain
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign media and metadata", e)
            null
        }
    }

    private fun generateSigningKeyIfMissing(context: Context, metadata: SerializablePhotoMetadata?) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            Log.d(TAG, "Signing key already exists.")
            return
        }

        Log.i(TAG, "No signing key found. Generating a new one...")

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))

        // Optional: link attestation to metadata content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && metadata != null) {
            builder.setAttestationChallenge(Json.encodeToString(metadata).toByteArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasStrongBox = context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            if (hasStrongBox) {
                try {
                    builder.setIsStrongBoxBacked(true)
                    Log.i(TAG, "Using StrongBox-backed key.")
                } catch (e: Exception) {
                    Log.w(TAG, "StrongBox unavailable, falling back.", e)
                }
            } else {
                Log.i(TAG, "StrongBox not supported, using standard Keystore.")
            }
        }

        kpg.initialize(builder.build())
        kpg.generateKeyPair()
        Log.i(TAG, "New ECDSA signing key generated and stored in Android Keystore.")
    }

    private fun calculateSha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance(HASH_ALGORITHM).digest(data)

    private fun calculateFileSha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest()
    }
}
