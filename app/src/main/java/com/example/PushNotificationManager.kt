package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.models.UserProfileData
import com.example.models.UserChatData
import com.example.models.MessageData
import com.example.utils.SupabaseSetup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PushNotificationManager {
    private const val CHANNEL_ID = "chat_messages_channel"
    private var isInitialized = false
    var currentOpenedChatId: String? = null
    private var lastObservedTimestamps = mutableMapOf<String, Long>()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        createNotificationChannel(context)

        scope.launch {
            // Wait for user to be logged in
            var currentUser = SupabaseSetup.client.auth.currentUserOrNull()
            while (currentUser == null) {
                delay(2000)
                currentUser = SupabaseSetup.client.auth.currentUserOrNull()
            }

            while (isActive) {
                try {
                    val userChats = SupabaseSetup.client.postgrest["user_chats"].select {
                        filter { eq("user_id", currentUser.id) }
                    }.decodeList<UserChatData>()
                    
                    for (chat in userChats) {
                        val peerId = chat.peer_id
                        val timestamp = chat.timestamp
                        val unreadCount = chat.unread_count
                        
                        val previousTimestamp = lastObservedTimestamps[peerId]
                        
                        if (unreadCount > 0 && (previousTimestamp == null || timestamp > previousTimestamp)) {
                            if (peerId != currentOpenedChatId) {
                                showTelegramStyleNotification(context, currentUser.id, peerId, unreadCount)
                            }
                        } else if (unreadCount == 0 && previousTimestamp != null && previousTimestamp > 0L) {
                            // Clear notification if read elsewhere
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.cancel(peerId.hashCode())
                        }
                        
                        lastObservedTimestamps[peerId] = timestamp
                    }
                } catch (e: Exception) {
                    // Ignore errors during polling
                }
                delay(3000)
            }
        }
    }

    private suspend fun showTelegramStyleNotification(context: Context, currentUserId: String, peerId: String, unreadCount: Int) {
        try {
            val userSnap = SupabaseSetup.client.postgrest["users"].select {
                filter { eq("uid", peerId) }
            }.decodeSingleOrNull<UserProfileData>() ?: return
            
            val chatId = if (currentUserId < peerId) "${currentUserId}_${peerId}" else "${peerId}_${currentUserId}"
            
            // Fetch unread messages
            val messages = SupabaseSetup.client.postgrest["messages"].select {
                filter { 
                    eq("chat_id", chatId)
                    eq("sender_id", peerId)
                }
                order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(unreadCount.toLong())
            }.decodeList<MessageData>().reversed()
            
            if (messages.isEmpty()) return

            val senderPerson = Person.Builder()
                .setName(userSnap.name)
                .setKey(peerId)
                .build()
                
            val mePerson = Person.Builder()
                .setName("Я")
                .setKey(currentUserId)
                .build()

            val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            messagingStyle.setConversationTitle(userSnap.name)
            messagingStyle.isGroupConversation = false
            
            for (msg in messages) {
                val decryptedTxt = if (!msg.text.startsWith("[")) {
                    try { com.example.ui.screens.chat.ChatCrypto.decrypt(msg.text, chatId) } catch (e: Exception) { msg.text }
                } else msg.text
                
                // If it's empty but has media
                val displayTxt = if (decryptedTxt.isBlank() && msg.mediaUrl.isNotBlank()) "Медиафайл" else decryptedTxt
                
                messagingStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        displayTxt,
                        msg.timestamp,
                        senderPerson
                    )
                )
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("chatId", peerId)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, peerId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val remoteInput = androidx.core.app.RemoteInput.Builder("key_text_reply").setLabel("Ответить").build()
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "ACTION_REPLY"
                putExtra("peerId", peerId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(context, peerId.hashCode(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val replyAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Ответить", replyPendingIntent)
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
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .addAction(replyAction)
                .addAction(readAction)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(peerId.hashCode(), builder.build())
            
            if (userSnap.avatarUrl.isNotBlank()) {
                val loader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(userSnap.avatarUrl)
                    .target { result ->
                        val bmp = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bmp != null) {
                            val personWithIcon = Person.Builder()
                                .setName(userSnap.name)
                                .setKey(peerId)
                                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bmp))
                                .build()
                                
                            val updatedStyle = NotificationCompat.MessagingStyle(mePerson)
                            updatedStyle.setConversationTitle(userSnap.name)
                            updatedStyle.isGroupConversation = false
                            
                            for (msg in messages) {
                                val decryptedTxt = if (!msg.text.startsWith("[")) {
                                    try { com.example.ui.screens.chat.ChatCrypto.decrypt(msg.text, chatId) } catch (e: Exception) { msg.text }
                                } else msg.text
                                
                                val displayTxt = if (decryptedTxt.isBlank() && msg.mediaUrl.isNotBlank()) "Медиафайл" else decryptedTxt
                                
                                updatedStyle.addMessage(
                                    NotificationCompat.MessagingStyle.Message(
                                        displayTxt,
                                        msg.timestamp,
                                        personWithIcon
                                    )
                                )
                            }
                            builder.setStyle(updatedStyle)
                            notificationManager.notify(peerId.hashCode(), builder.build())
                        }
                    }
                    .build()
                loader.enqueue(request)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
