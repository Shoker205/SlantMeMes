package com.example

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.ui.screens.chat.ChatCrypto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val peerId = intent.getStringExtra("peerId") ?: return
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")

        when (intent.action) {
            "ACTION_MARK_READ" -> {
                database.getReference("user_chats").child(currentUser.uid).child(peerId).child("unreadCount").setValue(0)
                // Clear notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(peerId.hashCode())
            }
            "ACTION_REPLY" -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence("key_text_reply")?.toString()
                
                if (!replyText.isNullOrBlank()) {
                    val chatId = if (currentUser.uid < peerId) "${currentUser.uid}_$peerId" else "${peerId}_${currentUser.uid}"
                    val encryptedText = ChatCrypto.encrypt(replyText, chatId)
                    
                    val messagesRef = database.getReference("chats").child(chatId).child("messages")
                    val newMsgId = messagesRef.push().key ?: return
                    
                    val msg = mapOf(
                        "id" to newMsgId,
                        "senderId" to currentUser.uid,
                        "text" to encryptedText,
                        "type" to "text",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false
                    )
                    messagesRef.child(newMsgId).setValue(msg)

                    val chatMetaMe = mapOf(
                        "lastMessage" to encryptedText,
                        "timestamp" to System.currentTimeMillis(),
                        "lastSenderId" to currentUser.uid
                    )
                    database.getReference("user_chats").child(currentUser.uid).child(peerId).updateChildren(chatMetaMe)
                    
                    database.getReference("user_chats").child(peerId).child(currentUser.uid).get().addOnSuccessListener { snap ->
                        val currentUnread = snap.child("unreadCount").getValue(Int::class.java) ?: 0
                        val chatMetaThem = mapOf(
                            "lastMessage" to encryptedText,
                            "timestamp" to System.currentTimeMillis(),
                            "lastSenderId" to currentUser.uid,
                            "unreadCount" to currentUnread + 1
                        )
                        database.getReference("user_chats").child(peerId).child(currentUser.uid).updateChildren(chatMetaThem)
                    }

                    // Clear notification after reply
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(peerId.hashCode())
                }
            }
        }
    }
}
