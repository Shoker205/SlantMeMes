package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.local.CryptoManager
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private suspend fun DatabaseReference.setValueSuspend(value: Any?) {
    suspendCancellableCoroutine<Unit> { continuation ->
        this.setValue(value).addOnSuccessListener {
            if (continuation.isActive) continuation.resume(Unit)
        }.addOnFailureListener {
            if (continuation.isActive) continuation.resumeWithException(it)
        }
    }
}

private suspend fun DatabaseReference.getSuspend(): DataSnapshot {
    return suspendCancellableCoroutine { continuation ->
        this.get().addOnSuccessListener { snapshot ->
            if (continuation.isActive) continuation.resume(snapshot)
        }.addOnFailureListener {
            if (continuation.isActive) continuation.resumeWithException(it)
        }
    }
}

data class P2PTransfer(
    val transferId: String,
    val senderId: String,
    val filename: String,
    val fileType: String,
    val totalChunks: Int,
    var chunksSent: Int = 0,
    var chunksReceived: Int = 0,
    val status: MutableStateFlow<String> = MutableStateFlow("queued")
)

object WebRtcDataChannel {
    private val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transfersRef = database.getReference("webrtc_signaling")

    private fun getAESKey(chatId: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(chatId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun initiateTransfer(context: Context, chatId: String, senderId: String, uri: Uri, type: String, onComplete: (String) -> Unit) {
        scope.launch {
            try {
                val transferId = System.currentTimeMillis().toString()
                val filename = "peer_file_${transferId}.${if (type == "image") "jpg" else if (type == "video") "mp4" else if (type == "audio") "m4a" else "bin"}"
                
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                
                // Cache locally for the sender to view
                val cachedFile = File(context.cacheDir, filename)
                cachedFile.writeBytes(bytes)
                
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, getAESKey(chatId))
                val encryptedBytes = cipher.doFinal(bytes)

                val chunkSize = 512 * 1024 // 512 KB per chunk
                val chunks = encryptedBytes.toList().chunked(chunkSize)
                
                val ref = transfersRef.child(chatId).child(transferId)
                ref.child("metadata").setValueSuspend(mapOf(
                    "senderId" to senderId,
                    "filename" to filename,
                    "fileType" to type,
                    "totalChunks" to chunks.size,
                    "ready" to false
                ))

                for ((index, chunk) in chunks.withIndex()) {
                    val encoded = Base64.encodeToString(chunk.toByteArray(), Base64.NO_WRAP)
                    ref.child("chunks").child(index.toString()).setValueSuspend(encoded)
                    delay(50)
                }

                ref.child("metadata").child("ready").setValueSuspend(true)
                
                val downloadedUri = "webrtc://$chatId/$transferId"
                withContext(Dispatchers.Main) {
                    onComplete(downloadedUri)
                }
            } catch (e: Exception) {
                Log.e("WebRtc", "Upload failed", e)
            }
        }
    }

    fun getLocalFileUri(context: Context, url: String): String {
        if (!url.startsWith("webrtc://")) return url
        val parts = url.replace("webrtc://", "").split("/")
        if (parts.size >= 2) {
            val transferId = parts[1]
            val file = context.cacheDir.listFiles()?.firstOrNull { it.name.contains(transferId) }
            if (file != null && file.exists()) {
                return android.net.Uri.fromFile(file).toString()
            }
        }
        return url
    }

    fun downloadWebRtcFile(context: Context, chatId: String, transferId: String, onComplete: (File?) -> Unit) {
        scope.launch {
            try {
                val ref = transfersRef.child(chatId).child(transferId)
                val snapshot = ref.child("metadata").getSuspend()
                if (!snapshot.exists() || snapshot.child("ready").getValue(Boolean::class.java) != true) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }
                
                val totalChunks = snapshot.child("totalChunks").getValue(Long::class.java)?.toInt() ?: 0
                val filename = snapshot.child("filename").getValue(String::class.java) ?: "received_file"
                
                val chunksSnap = ref.child("chunks").getSuspend()
                val buffer = java.io.ByteArrayOutputStream()
                for (i in 0 until totalChunks) {
                    val encoded = chunksSnap.child(i.toString()).getValue(String::class.java) ?: ""
                    if (encoded.isNotEmpty()) {
                        buffer.write(Base64.decode(encoded, Base64.NO_WRAP))
                    }
                }
                val encryptedBytes = buffer.toByteArray()
                
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, getAESKey(chatId))
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                
                val file = File(context.cacheDir, filename)
                file.writeBytes(decryptedBytes)
                
                withContext(Dispatchers.Main) {
                    onComplete(file)
                }
            } catch (e: Exception) {
                Log.e("WebRtc", "Download failed", e)
                withContext(Dispatchers.Main) { onComplete(null) }
            }
        }
    }
}
