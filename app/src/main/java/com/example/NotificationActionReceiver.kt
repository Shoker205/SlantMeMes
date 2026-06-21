package com.example

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.ui.screens.chat.ChatCrypto
import com.example.models.MessageData
import com.example.models.UserChatData
import com.example.utils.SupabaseSetup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val peerId = intent.getStringExtra("peerId") ?: return
        
        GlobalScope.launch {
            val currentUser = SupabaseSetup.client.auth.currentUserOrNull() ?: return@launch

            when (intent.action) {
                "ACTION_MARK_READ" -> {
                    SupabaseSetup.client.postgrest["user_chats"].update(mapOf("unread_count" to 0)) {
                        filter {
                            eq("user_id", currentUser.id)
                            eq("peer_id", peerId)
                        }
                    }
                    // Clear notification
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(peerId.hashCode())
                }
                "ACTION_REPLY" -> {
                    val remoteInput = RemoteInput.getResultsFromIntent(intent)
                    val replyText = remoteInput?.getCharSequence("key_text_reply")?.toString()
                    
                    if (!replyText.isNullOrBlank()) {
                        val chatId = if (currentUser.id < peerId) "${currentUser.id}_$peerId" else "${peerId}_${currentUser.id}"
                        val encryptedText = ChatCrypto.encrypt(replyText, chatId)
                        
                        val msg = MessageData(
                            id = UUID.randomUUID().toString(),
                            chat_id = chatId,
                            sender_id = currentUser.id,
                            text = encryptedText,
                            timestamp = System.currentTimeMillis()
                        )
                        SupabaseSetup.client.postgrest["messages"].insert(msg)

                        val chatMetaMe = mapOf(
                            "last_message" to encryptedText,
                            "timestamp" to System.currentTimeMillis()
                        )
                        SupabaseSetup.client.postgrest["user_chats"].update(chatMetaMe) {
                            filter {
                                eq("user_id", currentUser.id)
                                eq("peer_id", peerId)
                            }
                        }
                        
                        val peerChat = SupabaseSetup.client.postgrest["user_chats"].select {
                            filter {
                                eq("user_id", peerId)
                                eq("peer_id", currentUser.id)
                            }
                        }.decodeSingleOrNull<UserChatData>()
                        
                        val currentUnread = peerChat?.unread_count ?: 0
                        val chatMetaThem = mapOf(
                            "last_message" to encryptedText,
                            "timestamp" to System.currentTimeMillis(),
                            "unread_count" to currentUnread + 1
                        )
                        
                        if (peerChat != null) {
                            SupabaseSetup.client.postgrest["user_chats"].update(chatMetaThem) {
                                filter {
                                    eq("user_id", peerId)
                                    eq("peer_id", currentUser.id)
                                }
                            }
                        } else {
                            val newPeerChat = UserChatData(
                                user_id = peerId,
                                peer_id = currentUser.id,
                                timestamp = System.currentTimeMillis(),
                                unread_count = 1,
                                last_message = encryptedText
                            )
                            SupabaseSetup.client.postgrest["user_chats"].insert(newPeerChat)
                        }

                        // Clear notification after reply
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(peerId.hashCode())
                    }
                }
            }
        }
    }
}
