package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class UserChatData(
    val user_id: String,
    val peer_id: String,
    val timestamp: Long,
    val unread_count: Int,
    val last_message: String
)

@Serializable
data class MessageData(
    val id: String,
    val chat_id: String,
    val sender_id: String,
    val text: String,
    val type: String = "text",
    val mediaUrl: String = "",
    val replyToMsgId: String? = null,
    val timestamp: Long,
    val is_read: Boolean = false,
    val attachments: List<com.example.ui.screens.chat.ChatAttachment> = emptyList()
)
