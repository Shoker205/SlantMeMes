package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.models.UserProfileData
import com.example.models.UserChatData
import com.example.utils.SupabaseSetup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PushNotificationManager {
    private const val CHANNEL_ID = "chat_messages_channel"
    private var isInitialized = false
    var currentOpenedChatId: String? = null
    private var lastObservedTimestamps = mutableMapOf<String, Long>()

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        createNotificationChannel(context)

        val currentUser = SupabaseSetup.client.auth.currentUserOrNull() ?: return

        GlobalScope.launch {
            while (isActive) {
                try {
                    val userChats = SupabaseSetup.client.postgrest["user_chats"].select {
                        filter { eq("user_id", currentUser.id) }
                    }.decodeList<UserChatData>()
                    
                    for (chat in userChats) {
                        val peerId = chat.peer_id
                        val timestamp = chat.timestamp
                        val lastMessage = chat.last_message
                        
                        // We check timestamp vs last message
                        val previousTimestamp = lastObservedTimestamps[peerId]
                        
                        if (previousTimestamp != null && timestamp > previousTimestamp) {
                            if (lastMessage.isNotBlank() && peerId != currentOpenedChatId) {
                                // Assume it's from them if updated and it's not opened
                                val userSnap = SupabaseSetup.client.postgrest["users"].select {
                                    filter { eq("uid", peerId) }
                                }.decodeSingleOrNull<UserProfileData>()
                                
                                if (userSnap != null) {
                                    val name = userSnap.name
                                    val avatarUrl = userSnap.avatarUrl
                                    
                                    val chatId = if (currentUser.id < peerId) currentUser.id + "_" + peerId else peerId + "_" + currentUser.id
                                    val decryptedTxt = if (!lastMessage.startsWith("[")) {
                                        try { com.example.ui.screens.chat.ChatCrypto.decrypt(lastMessage, chatId) } catch (e: Exception) { lastMessage }
                                    } else lastMessage
                                    
                                    showNotification(context, peerId, name, decryptedTxt, avatarUrl)
                                }
                            }
                        }
                        
                        lastObservedTimestamps[peerId] = timestamp
                    }
                } catch (e: Exception) {
                    // Ignore errors during polling
                }
                delay(4000)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Chat Messages"
            val descriptionText = "Notifications for new messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, peerId: String, title: String, messageText: String, avatarUrl: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatId", peerId)
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, peerId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyLabel = "Ответить"
        val remoteInput = androidx.core.app.RemoteInput.Builder("key_text_reply").setLabel(replyLabel).build()
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_REPLY"
            putExtra("peerId", peerId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(context, peerId.hashCode(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val replyAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, replyLabel, replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()
            
        val readIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_MARK_READ"
            putExtra("peerId", peerId)
        }
        val readPendingIntent = PendingIntent.getBroadcast(context, peerId.hashCode() + 1, readIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val readAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, "Прочитать", readPendingIntent).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .addAction(readAction)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        notificationManager.notify(peerId.hashCode(), builder.build())
        
        if (avatarUrl.isNotBlank()) {
            val loader = coil.ImageLoader(context)
            val request = coil.request.ImageRequest.Builder(context)
                .data(avatarUrl)
                .target { result ->
                    val bmp = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        builder.setLargeIcon(bmp)
                        notificationManager.notify(peerId.hashCode(), builder.build())
                    }
                }
                .build()
            loader.enqueue(request)
        }
    }
}
