package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.io.File

object WebRtcDataChannel {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initiateTransfer(context: Context, chatId: String, senderId: String, uri: Uri, type: String, onComplete: (String) -> Unit) {
        scope.launch {
            try {
                val transferId = System.currentTimeMillis().toString()
                
                var originalFilename = "peer_file_${transferId}"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                originalFilename = cursor.getString(nameIndex)
                            }
                        }
                    }
                } else if (uri.scheme == "file") {
                    originalFilename = File(uri.path ?: "").name
                }
                if (!originalFilename.contains(".")) {
                    originalFilename += ".${if (type == "image") "jpg" else if (type == "video") "mp4" else if (type == "voice") "m4a" else "bin"}"
                }
                
                val filename = originalFilename
                
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                
                val path = "$chatId/$transferId/$filename"
                
                SupabaseSetup.client.storage.from("chat_media").upload(path, bytes)
                val publicUrl = SupabaseSetup.client.storage.from("chat_media").publicUrl(path)
                
                withContext(Dispatchers.Main) {
                    onComplete(publicUrl)
                }
            } catch (e: Exception) {
                Log.e("WebRtc", "Upload failed", e)
            }
        }
    }

    fun getLocalFileUri(context: Context, url: String): String {
        return url
    }

    fun downloadWebRtcFile(context: Context, chatId: String, transferId: String, onComplete: (File?) -> Unit) {
        // Obsolete function since we use publicUrl directly
        scope.launch(Dispatchers.Main) { onComplete(null) }
    }
}
