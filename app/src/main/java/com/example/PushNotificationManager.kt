package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object PushNotificationManager {
    private const val CHANNEL_ID = "chat_messages_channel"
    private var isInitialized = false
    private var lastObservedTimestamps = mutableMapOf<String, Long>()

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        createNotificationChannel(context)

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
        val userChatsRef = database.getReference("user_chats").child(currentUser.uid)

        userChatsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val peerId = child.key ?: continue
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val lastMessage = child.child("lastMessage").getValue(String::class.java) ?: ""
                    val lastSenderId = child.child("lastSenderId").getValue(String::class.java) ?: ""

                    val previousTimestamp = lastObservedTimestamps[peerId]

                    // If we already know about this chat and the timestamp is newer, it's a new message
                    if (previousTimestamp != null && timestamp > previousTimestamp) {
                        if (lastMessage.isNotBlank() && lastSenderId != currentUser.uid) {
                            // Let's resolve peer name
                            database.getReference("users").child(peerId).child("name").get().addOnSuccessListener { nameSnap ->
                                val name = nameSnap.getValue(String::class.java) ?: "User"
                                showNotification(context, peerId, name, "Новое сообщение")
                            }
                        }
                    }
                    
                    lastObservedTimestamps[peerId] = timestamp
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
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

    private fun showNotification(context: Context, peerId: String, title: String, messageText: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // We could pass an extra to navigate directly to the chat
            putExtra("chatId", peerId)
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, peerId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(peerId.hashCode(), builder.build())
    }
}
