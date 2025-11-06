package com.doublethinksolutions.osp.signing

import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.ceil

/**
 * A utility for calculating the Merkle Tree root hash of a large file.
 * This approach is highly performant for large files like videos because it
 * processes file chunks in parallel.
 */
object MerkleTree {

    private const val CHUNK_SIZE_BYTES = 1024 * 1024 // 1 MB chunks
    private const val HASH_ALGORITHM = "SHA-256"

    /**
     * Calculates the Merkle root hash for a given file.
     *
     * @param file The file to hash.
     * @param dispatcher The coroutine dispatcher to use for parallel processing, defaults to Dispatchers.IO.
     * @return The 32-byte SHA-256 Merkle root hash.
     */
    suspend fun calculateRootHash(file: File, dispatcher: CoroutineDispatcher = Dispatchers.IO): ByteArray {
        return withContext(dispatcher) {
            val leafHashes = calculateLeafHashes(file)
            calculateRootFromLeaves(leafHashes)
        }
    }

    private suspend fun calculateLeafHashes(file: File): List<ByteArray> = coroutineScope {
        val fileSize = file.length()
        if (fileSize == 0L) return@coroutineScope emptyList()

        val numChunks = ceil(fileSize.toDouble() / CHUNK_SIZE_BYTES).toInt()
        val deferredHashes = List(numChunks) { chunkIndex ->
            async {
                hashChunk(file, chunkIndex)
            }
        }
        deferredHashes.awaitAll()
    }

    private fun hashChunk(file: File, chunkIndex: Int): ByteArray {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val buffer = ByteArray(8192)
        RandomAccessFile(file, "r").use { raf ->
            val offset = chunkIndex.toLong() * CHUNK_SIZE_BYTES
            raf.seek(offset)

            var bytesToRead = CHUNK_SIZE_BYTES
            var bytesRead: Int

            while (bytesToRead > 0) {
                bytesRead = raf.read(buffer)
                if (bytesRead == -1) break
                val readCount = minOf(bytesRead, bytesToRead)
                digest.update(buffer, 0, readCount)
                bytesToRead -= readCount
            }
        }
        return digest.digest()
    }

    private fun calculateRootFromLeaves(hashes: List<ByteArray>): ByteArray {
        if (hashes.isEmpty()) {
            // Hash of an empty input is a known value for SHA-256
            return MessageDigest.getInstance(HASH_ALGORITHM).digest()
        }
        if (hashes.size == 1) {
            return hashes.first()
        }

        var currentLevelHashes = hashes
        while (currentLevelHashes.size > 1) {
            currentLevelHashes = currentLevelHashes
                .chunked(2)
                .map { pair ->
                    if (pair.size == 2) {
                        // Standard case: hash the concatenation of two child hashes
                        hashPair(pair[0], pair[1])
                    } else {
                        // Odd number of nodes: hash the last node with itself
                        hashPair(pair[0], pair[0])
                    }
                }
        }
        return currentLevelHashes.first()
    }

    private fun hashPair(left: ByteArray, right: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        digest.update(left)
        digest.update(right)
        return digest.digest()
    }
}