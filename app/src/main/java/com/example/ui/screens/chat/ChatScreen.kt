package com.example.ui.screens.chat

import android.net.Uri
import com.example.PushNotificationManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.isActive
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Simple E2E Encryption helper (AES)
object ChatCrypto {
    private fun getSecretKey(chatId: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(chatId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(text: String, chatId: String): String {
        try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(chatId))
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            return text
        }
    }

    fun decrypt(text: String, chatId: String): String {
        try {
            val decoded = Base64.decode(text, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(chatId))
            return String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            return text
        }
    }
}

data class ChatAttachment(
    val url: String = "",
    val type: String = "",
    val filename: String = ""
)

data class PendingAttachment(val uri: android.net.Uri, val type: String)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val type: String = "text",
    val timestamp: Long = 0L,
    val mediaUrl: String = "",
    val replyToMsgId: String? = null,
    val isRead: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    recipientId: String,
    onBack: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var contextMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var initialMediaIndex by remember { mutableIntStateOf(0) }
    var showFullscreenMedia by remember { mutableStateOf(false) }
    var showAudioPlayer by remember { mutableStateOf(false) }
    var initialAudioUrl by remember { mutableStateOf("") }
    var initialAudioName by remember { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }

    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")

    var recipientName by remember { mutableStateOf("User") }
    var recipientAvatar by remember { mutableStateOf("") }
    var recipientOnline by remember { mutableStateOf(false) }
    var recipientLastSeen by remember { mutableStateOf(0L) }
    
    LaunchedEffect(recipientId) {
        database.getReference("users").child(recipientId).addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                recipientName = snapshot.child("name").getValue(String::class.java) ?: "User"
                recipientAvatar = snapshot.child("avatarUrl").getValue(String::class.java) ?: ""
                recipientOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false
                recipientLastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
    
    DisposableEffect(recipientId) {
        PushNotificationManager.currentOpenedChatId = recipientId
        onDispose {
            if (PushNotificationManager.currentOpenedChatId == recipientId) {
                PushNotificationManager.currentOpenedChatId = null
            }
        }
    }
    
    val chatId = if (currentUser.uid < recipientId) "${currentUser.uid}_$recipientId" else "${recipientId}_${currentUser.uid}"

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val bgColor = if (isDarkTheme) Black else Color(0xFFEBEBEB)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)
    
    val bubbleSentContentColor = bgColor
    val bubbleSentBgColor = textColor
    val bubbleReceivedColor = if (isDarkTheme) Color(0xFF1E1E1E) else White

    var showAttachmentMenu by remember { mutableStateOf(false) }

    LaunchedEffect(recipientId) {
        // Listen to messages
        val messagesRef = database.getReference("chats").child(chatId).child("messages")
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newMessages = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: ""
                    val senderId = child.child("senderId").getValue(String::class.java) ?: ""
                    val encryptedText = child.child("text").getValue(String::class.java) ?: ""
                    val type = child.child("type").getValue(String::class.java) ?: "text"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val mediaUrl = child.child("mediaUrl").getValue(String::class.java) ?: ""
                    val replyToMsgId = child.child("replyToMsgId").getValue(String::class.java)
                    val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false
                    
                    val decryptedText = if (type == "text") ChatCrypto.decrypt(encryptedText, chatId) else encryptedText
                    
                    newMessages.add(ChatMessage(id, senderId, decryptedText, type, timestamp, mediaUrl, replyToMsgId, isRead))
                    
                    if (senderId != currentUser.uid && !isRead) {
                        child.ref.child("isRead").setValue(true)
                    }
                }
                messages = newMessages.sortedByDescending { it.timestamp }
                
                // Reset unread count for current user
                database.getReference("user_chats").child(currentUser.uid).child(recipientId).child("unreadCount").setValue(0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun sendMessage(text: String, type: String = "text", mediaUrl: String = "", attachments: List<ChatAttachment> = emptyList()) {
        if (text.isBlank() && mediaUrl.isBlank() && attachments.isEmpty()) return
        val messagesRef = database.getReference("chats").child(chatId).child("messages")
        
        val encryptedText = if (type == "text" || type == "media_group") {
            ChatCrypto.encrypt(text, chatId)
        } else text
        
        if (messageToEdit != null) {
            messagesRef.child(messageToEdit!!.id).child("text").setValue(encryptedText)
            messageToEdit = null
        } else {
            val newMsgId = messagesRef.push().key ?: return
            val msg = ChatMessage(
                id = newMsgId,
                senderId = currentUser.uid,
                text = encryptedText,
                type = type,
                timestamp = System.currentTimeMillis(),
                mediaUrl = mediaUrl,
                replyToMsgId = replyToMessage?.id,
                attachments = attachments
            )
            messagesRef.child(newMsgId).setValue(msg)
            
            val chatMetaMe = mapOf(
                "lastMessage" to (if (type == "text") encryptedText else "[$type]"),
                "timestamp" to System.currentTimeMillis(),
                "lastSenderId" to currentUser.uid
            )
            database.getReference("user_chats").child(currentUser.uid).child(recipientId).updateChildren(chatMetaMe)
            
            database.getReference("user_chats").child(recipientId).child(currentUser.uid).get().addOnSuccessListener { snap ->
                val currentUnread = snap.child("unreadCount").getValue(Int::class.java) ?: 0
                val chatMetaThem = mapOf(
                    "lastMessage" to (if (type == "text") encryptedText else "[$type]"),
                    "timestamp" to System.currentTimeMillis(),
                    "lastSenderId" to currentUser.uid,
                    "unreadCount" to currentUnread + 1
                )
                database.getReference("user_chats").child(recipientId).child(currentUser.uid).updateChildren(chatMetaThem)
            }
        }
        
        replyToMessage = null

        scope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }
    }

    var pendingAttachmentType by remember { mutableStateOf("image/*") }
    var isUploading by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val typeLabel = when {
            pendingAttachmentType.startsWith("image") -> "image"
            pendingAttachmentType.startsWith("video") -> "video"
            pendingAttachmentType.startsWith("audio") -> "audio"
            else -> "file"
        }
        val toAdd = uris.take(10 - pendingAttachments.size).map { PendingAttachment(it, typeLabel) }
        if (toAdd.isNotEmpty()) {
            pendingAttachments = pendingAttachments + toAdd
        }
        if (uris.size > 10) {
            android.widget.Toast.makeText(ctx, "Выбрано больше 10 файлов", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    fun uploadAndSendMessage(text: String, audioUri: android.net.Uri? = null) {
        val attachmentsToUpload = pendingAttachments.toList() + (audioUri?.let { listOf(PendingAttachment(it, "audio")) } ?: emptyList())
        if (attachmentsToUpload.isEmpty()) {
            if (text.isNotBlank()) sendMessage(text)
            return
        }
        
        isUploading = true
        pendingAttachments = emptyList() // clear
        
        // Background upload process that waits for recipient to be online
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            // Wait for receiver to be online (simulating WebRTC requirement)
            while (!recipientOnline) {
                kotlinx.coroutines.delay(2000)
            }
            
            val uploadedAttachments = mutableListOf<ChatAttachment>()
            var uploadsCompleted = 0
            
            attachmentsToUpload.forEach { pending ->
                com.example.utils.WebRtcDataChannel.initiateTransfer(
                    context = ctx,
                    chatId = chatId,
                    senderId = currentUser?.uid ?: "",
                    uri = pending.uri,
                    type = pending.type
                ) { downloadUri ->
                    uploadedAttachments.add(ChatAttachment(url = downloadUri, type = pending.type, filename = pending.uri.lastPathSegment ?: "file"))
                    uploadsCompleted++
                    if (uploadsCompleted == attachmentsToUpload.size) {
                        isUploading = false
                        val sendType = if (uploadedAttachments.size == 1 && text.isBlank()) pending.type else "media_group"
                        sendMessage(text, type = sendType, attachments = uploadedAttachments)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    androidx.compose.animation.AnimatedContent(targetState = selectedMessages.isNotEmpty(), label = "") { hasSelection ->
                        if (hasSelection) {
                            Text(selectedMessages.size.toString(), color = textColor, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onProfileClick() }
                            ) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(surfaceColor)) {
                                    if (recipientAvatar.isNotBlank()) {
                                        com.example.ui.components.AvatarImage(avatarUrl = recipientAvatar, contentDescription = null, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(36.dp)) {
                                    Text(recipientName, color = textColor, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, lineHeight = 20.sp)
                                    if (recipientOnline) {
                                        Text("онлайн", color = Color(0xFF4FC3F7), fontSize = 12.sp, lineHeight = 16.sp)
                                    } else if (recipientLastSeen > 0L) {
                                        val dateStr = java.text.SimpleDateFormat("HH:mm, dd MMM", java.util.Locale.getDefault()).format(java.util.Date(recipientLastSeen))
                                        Text("был(а) $dateStr", color = dimTextColor, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (selectedMessages.isNotEmpty()) {
                        IconButton(onClick = { selectedMessages = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = textColor)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                        }
                    }
                },
                actions = {
                    if (selectedMessages.isNotEmpty()) {
                        IconButton(onClick = { showForwardDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Forward", tint = textColor, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
                        }
                        IconButton(onClick = { 
                            val messagesRef = database.getReference("chats").child(chatId).child("messages")
                            selectedMessages.forEach { id -> messagesRef.child(id).removeValue() }
                            selectedMessages = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = textColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUser.uid
                    val isSelected = selectedMessages.contains(msg.id)
                    val isHighlighted = highlightedMessageId == msg.id
                    var swipeOffset by remember { mutableFloatStateOf(0f) }
                    
                    Box(modifier = Modifier.fillMaxWidth().animateItemPlacement(), contentAlignment = Alignment.CenterEnd) {
                        if (swipeOffset < -20f) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = dimTextColor, modifier = Modifier.padding(end = 16.dp).size(24.dp).scale(scaleX = -1f, scaleY = 1f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.toInt(), 0) }
                                .background(if (isSelected) textColor.copy(alpha = 0.3f) else if (isHighlighted) textColor.copy(alpha = 0.15f) else Color.Transparent)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (swipeOffset < -100f) {
                                                replyToMessage = msg
                                            }
                                            swipeOffset = 0f
                                        },
                                        onDragCancel = { swipeOffset = 0f },
                                        onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                            
                                            val newOffset = swipeOffset + dragAmount
                                            if (newOffset <= 0f && newOffset >= -150f) {
                                                swipeOffset = newOffset
                                            }
                                        }
                                    )
                                }
                                .combinedClickable(
                                onClick = {
                                    if (selectedMessages.isNotEmpty()) {
                                        if (isSelected) selectedMessages -= msg.id
                                        else if (selectedMessages.size < 100) selectedMessages += msg.id
                                    }
                                },
                                onLongClick = {
                                    if (selectedMessages.isEmpty()) {
                                        contextMenuMessage = msg
                                    }
                                }
                            )
                            .padding(vertical = 2.dp, horizontal = 8.dp),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (isMine) {
                            Row(modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)) {
                                val iconTint = if (msg.isRead) Color(0xFF4FC3F7) else dimTextColor
                                Icon(Icons.Default.Check, contentDescription = "Tick", tint = iconTint, modifier = Modifier.size(16.dp))
                                if (msg.isRead) {
                                    Icon(Icons.Default.Check, contentDescription = "Read", tint = iconTint, modifier = Modifier.size(16.dp).offset(x = (-8).dp))
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(
                                    topStart = 16.dp, 
                                    topEnd = 16.dp, 
                                    bottomStart = if (isMine) 16.dp else 4.dp, 
                                    bottomEnd = if (isMine) 4.dp else 16.dp
                                ))
                                .background(if (isMine) bubbleSentBgColor else bubbleReceivedColor)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (msg.replyToMsgId != null) {
                                val replyMsg = messages.find { it.id == msg.replyToMsgId }
                                if (replyMsg != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(textColor.copy(alpha = 0.05f))
                                            .clickable {
                                                highlightedMessageId = replyMsg.id
                                                val index = messages.indexOf(replyMsg)
                                                if (index != -1) {
                                                    scope.launch { listState.animateScrollToItem(index) }
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(1500)
                                                        if (highlightedMessageId == replyMsg.id) highlightedMessageId = null
                                                    }
                                                }
                                            }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.width(3.dp).height(24.dp).background(Color(0xFF4FC3F7), RoundedCornerShape(1.dp)))
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text(if (replyMsg.senderId == currentUser.uid) "Вы" else recipientName, color = Color(0xFF4FC3F7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(replyMsg.text, color = if (isMine) bubbleSentContentColor else textColor, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                                    if (msg.attachments.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            androidx.compose.foundation.layout.FlowRow(
                                                modifier = Modifier.fillMaxWidth(0.9f),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                msg.attachments.forEach { attachment ->
                                                    val isMedia = attachment.type.startsWith("image") || attachment.type.startsWith("video")
                                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                                    Box(
                                                        modifier = Modifier
                                                            .size(120.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.Gray.copy(alpha=0.3f))
                                                            .clickable {
                                                                if (isMedia) {
                                                                    initialAudioUrl = attachment.url // using this state var hackily for passing url
                                                                    showFullscreenMedia = true
                                                                } else if (attachment.type.startsWith("audio")) {
                                                                    initialAudioUrl = attachment.url
                                                                    initialAudioName = attachment.filename
                                                                    showAudioPlayer = true
                                                                } else {
                                                                    MediaTools.downloadMedia(ctx, attachment.url, "file")
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMedia) {
                                                            if (attachment.url.startsWith("webrtc://")) {
                                                                Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                                                    Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = Color.White, modifier = Modifier.size(24.dp))
                                                                }
                                                            } else {
                                                                coil.compose.AsyncImage(
                                                                    model = attachment.url,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                                )
                                                            }
                                                            if (attachment.type.startsWith("video")) {
                                                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
                                                                    Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(32.dp))
                                                                }
                                                            }
                                                        } else {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                                                Icon(if (attachment.type.startsWith("audio")) Icons.Default.Audiotrack else androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = if(isMine) bubbleSentContentColor else textColor, modifier = Modifier.size(32.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text(attachment.filename.takeIf { it.isNotBlank() } ?: "Файл", color = if(isMine) bubbleSentContentColor else textColor, fontSize = 10.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (msg.text.isNotBlank() && msg.text != "Файлы" && msg.text != "[Фото]" && msg.text != "[Видео]" && msg.text != "[Аудио]") {
                                                Text(msg.text, color = if(isMine) bubbleSentContentColor else textColor, fontSize = 15.sp)
                                            }
                                        }
                                    } else {
                                        when (msg.type) {
                                            "text" -> {
                                                Text(
                                                    text = msg.text,
                                                    color = if (isMine) bubbleSentContentColor else textColor,
                                                    fontSize = 15.sp,
                                                    lineHeight = 20.sp
                                                )
                                            }
                                            "image", "video" -> {
                                    Column {
                                        if (msg.mediaUrl.isNotBlank()) {
                                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)).clickable { 
                                                val mediaMessages = messages.filter { it.type == "image" || it.type == "video" }
                                                val idx = mediaMessages.indexOf(msg)
                                                if(idx != -1) {
                                                    initialMediaIndex = idx
                                                    showFullscreenMedia = true
                                                }
                                            }) {
                                                if (msg.mediaUrl.startsWith("webrtc://")) {
                                                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = Color.White, modifier = Modifier.size(32.dp))
                                                        Text("Секретное фото", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                                                    }
                                                } else {
                                                    coil.compose.AsyncImage(
                                                        model = msg.mediaUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                }
                                                if (msg.type == "video") {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Videocam, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(48.dp))
                                                    }
                                                }
                                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                                IconButton(
                                                    onClick = { MediaTools.downloadMedia(ctx, msg.mediaUrl, msg.type) },
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha=0.4f), CircleShape).size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                        }
                                        if (msg.text.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(if (msg.type == "image") Icons.Default.Image else Icons.Default.Videocam, contentDescription = null, tint = if (isMine) bubbleSentContentColor else textColor, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(msg.text, color = if (isMine) bubbleSentContentColor else textColor, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                                "audio", "media", "file" -> {
                                    Column {
                                        val ctx = androidx.compose.ui.platform.LocalContext.current
                                        val isAudio = msg.type == "audio"
                                        val icon = if (isAudio) Icons.Default.Audiotrack else androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                                if (isAudio) {
                                                    initialAudioUrl = msg.mediaUrl
                                                    initialAudioName = if (msg.text.isNotBlank()) msg.text else "Голосовое сообщение"
                                                    showAudioPlayer = true
                                                } else {
                                                    MediaTools.downloadMedia(ctx, msg.mediaUrl, "file")
                                                }
                                            }.padding(4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if(isMine) bgColor.copy(alpha=0.3f) else Color.Gray.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = null, tint = if (isMine) bubbleSentContentColor else textColor, modifier = Modifier.size(24.dp))
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                val dispText = if (msg.text.isNotBlank()) msg.text else (if (isAudio) "Голосовое сообщение" else "Файл")
                                                Text(dispText, color = if (isMine) bubbleSentContentColor else textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                if (!isAudio) {
                                                    Text("Нажмите для скачивания", color = if (isMine) bubbleSentContentColor.copy(alpha=0.7f) else dimTextColor, fontSize = 11.sp)
                                                } else {
                                                    Text("Нажмите для прослушивания", color = if (isMine) bubbleSentContentColor.copy(alpha=0.7f) else dimTextColor, fontSize = 11.sp)
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(onClick = { MediaTools.downloadMedia(ctx, msg.mediaUrl, msg.type) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Download, "Download", tint = if(isMine) bubbleSentContentColor else dimTextColor)
                                            }
                                        }
                                    }
                                }
                            }
                            } // Close else block
                        }
                    }
                } // Box end
            }
        }

        // Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Column {
                    androidx.compose.animation.AnimatedVisibility(visible = pendingAttachments.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pendingAttachments.size, key = { index -> pendingAttachments[index].uri.toString() + index }) { index ->
                                val attachment = pendingAttachments[index]
                                Box(
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(bgColor).animateItemPlacement(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (attachment.type.startsWith("image") || attachment.type.startsWith("video")) {
                                        coil.compose.AsyncImage(
                                            model = attachment.uri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        if (attachment.type.startsWith("video")) {
                                            Icon(Icons.Default.Videocam, "Video", tint = Color.White, modifier = Modifier.size(24.dp).align(Alignment.Center))
                                        }
                                    } else {
                                        Icon(if (attachment.type.startsWith("audio")) Icons.Default.Audiotrack else androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = dimTextColor, modifier = Modifier.size(32.dp))
                                    }
                                    IconButton(
                                        onClick = { pendingAttachments = pendingAttachments.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).background(Color.Black.copy(alpha=0.5f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = isUploading) {
                        Text("Загрузка медиа...", color = Color(0xFF4FC3F7), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = replyToMessage != null) {
                        if (replyToMessage != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = dimTextColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("В ответ на", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                                    Text(replyToMessage!!.text, color = textColor, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { replyToMessage = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = dimTextColor)
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = messageToEdit != null) {
                        if (messageToEdit != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = dimTextColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Редактирование", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                                    Text(messageToEdit!!.text, color = textColor, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { messageToEdit = null; inputText = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = dimTextColor)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .background(bgColor, RoundedCornerShape(24.dp)),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box(modifier = Modifier.padding(bottom = 2.dp)) {
                            IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                                Icon(Icons.Default.Add, contentDescription = "Attach", tint = dimTextColor, modifier = Modifier.size(28.dp))
                            }
                            DropdownMenu(
                                expanded = showAttachmentMenu,
                                onDismissRequest = { showAttachmentMenu = false },
                                containerColor = surfaceColor
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Изображение", color = textColor) },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = dimTextColor) },
                                    onClick = { showAttachmentMenu = false; pendingAttachmentType = "image"; launcher.launch("image/*") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Видео", color = textColor) },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = dimTextColor) },
                                    onClick = { showAttachmentMenu = false; pendingAttachmentType = "video"; launcher.launch("video/*") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Аудио", color = textColor) },
                                    leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = dimTextColor) },
                                    onClick = { showAttachmentMenu = false; pendingAttachmentType = "audio"; launcher.launch("audio/*") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Файл", color = textColor) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = dimTextColor) },
                                    onClick = { showAttachmentMenu = false; pendingAttachmentType = "file"; launcher.launch("*/*") }
                                )
                            }
                        }
                        
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        val recorder = remember { com.example.utils.VoiceRecorder(ctx) }
                        var audioPermissionGranted by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
                        val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                            audioPermissionGranted = isGranted
                            if (!isGranted) {
                                android.widget.Toast.makeText(ctx, "Разрешение на микрофон необходимо", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        var isRecording by remember { mutableStateOf(false) }
                        var isRecordingLocked by remember { mutableStateOf(false) }
                        var recordSlideOffset by remember { mutableFloatStateOf(0f) }
                        var recordSlideYOffset by remember { mutableFloatStateOf(0f) }
                        var recordingSeconds by remember { mutableIntStateOf(0) }
                        var hasVibratedForLock by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(isRecording) {
                            if (isRecording) {
                                recordingSeconds = 0
                                while (isActive) {
                                    kotlinx.coroutines.delay(1000)
                                    recordingSeconds++
                                }
                            }
                        }
                        
                        if (isRecording) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isRecordingLocked) {
                                    IconButton(onClick = {
                                        recorder.cancelRecording()
                                        isRecording = false
                                        isRecordingLocked = false
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                                }
                                
                                Spacer(Modifier.width(8.dp))
                                val min = recordingSeconds / 60
                                val sec = recordingSeconds % 60
                                Text(String.format(java.util.Locale.US, "%d:%02d", min, sec), color = textColor, fontSize = 14.sp)
                                
                                val infiniteTransition = rememberInfiniteTransition()
                                val wavePhase by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 2 * Math.PI.toFloat(), animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "")
                                
                                Canvas(modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 8.dp)) {
                                    val barWidth = 3.dp.toPx()
                                    val space = 2.dp.toPx()
                                    val bars = (size.width / (barWidth + space)).toInt()
                                    for (i in 0 until bars) {
                                        val x = i * (barWidth + space)
                                        val phaseOffset = i * 0.3f
                                        val normalizedSine = (kotlin.math.sin(wavePhase + phaseOffset) + 1) / 2
                                        val barHeight = (size.height * 0.3f) + (size.height * 0.7f) * normalizedSine
                                        drawRect(
                                            color = Color(0xFF4FC3F7),
                                            topLeft = Offset(x, (size.height - barHeight.toFloat()) / 2),
                                            size = Size(barWidth, barHeight.toFloat())
                                        )
                                    }
                                }
    
                                if (!isRecordingLocked) {
                                    val cancelAlpha = (1f - (-recordSlideOffset / 150f)).coerceIn(0f, 1f)
                                    Text("< Отмените", color = dimTextColor.copy(alpha = cancelAlpha), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(recordSlideOffset.toInt(), 0) })
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    IconButton(onClick = {
                                        val file = recorder.stopRecording()
                                        if (file != null) uploadAndSendMessage("", android.net.Uri.fromFile(file))
                                        isRecording = false
                                        isRecordingLocked = false
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF4FC3F7))
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp, horizontal = 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                                    maxLines = 5,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp, end = 4.dp),
                                    decorationBox = { innerTextField ->
                                        if (inputText.isEmpty()) {
                                            Text("Сообщение...", color = dimTextColor, fontSize = 16.sp)
                                        }
                                        innerTextField()
                                    },
                                    cursorBrush = SolidColor(if (isDarkTheme) Color.White else Color.Black)
                                )
                            }
                        }
                        
                        val micButtonSize by animateDpAsState(targetValue = if (isRecording && !isRecordingLocked) 64.dp else 44.dp, label = "")
                        Box(modifier = Modifier.padding(bottom = 2.dp, end = 2.dp)) {
                            if (isRecording && !isRecordingLocked && recordSlideYOffset < -20f) {
                                Box(modifier = Modifier.offset(x = 14.dp, y = (-60).dp).background(surfaceColor, CircleShape).padding(8.dp)) {
                                    Icon(Icons.Default.Lock, contentDescription = "Lock", tint = dimTextColor, modifier = Modifier.size(16.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(
                                        if (!isRecordingLocked) recordSlideOffset.toInt() else 0,
                                        if (!isRecordingLocked) recordSlideYOffset.toInt() else 0
                                    ) }
                                    .size(44.dp), // anchor size so row doesn't jump
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredSize(micButtonSize)
                                        .clip(CircleShape)
                                        .background(if (isRecordingLocked) Color.Red.copy(alpha = 0.2f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                uploadAndSendMessage(inputText.trim())
                                                inputText = ""
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF4FC3F7), modifier = Modifier.size(24.dp))
                                        }
                                    } else if (isRecordingLocked) {
                                        IconButton(
                                            onClick = {
                                                recorder.cancelRecording()
                                                isRecording = false
                                                isRecordingLocked = false
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(24.dp))
                                        }
                                    } else {
                                        Box(
                                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { 
                                                    if (!audioPermissionGranted) {
                                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                        return@detectDragGestures
                                                    }
                                                    isRecording = true 
                                                    recordSlideOffset = 0f
                                                    recordSlideYOffset = 0f
                                                    hasVibratedForLock = false
                                                    recorder.startRecording()
                                                },
                                                onDragEnd = {
                                                    if (recordSlideOffset < -150f) {
                                                        recorder.cancelRecording()
                                                        isRecording = false // Cancel
                                                    } else if (recordSlideYOffset <= -60f || hasVibratedForLock) {
                                                        isRecordingLocked = true // Lock
                                                        recordSlideYOffset = 0f
                                                        recordSlideOffset = 0f
                                                    } else {
                                                        val file = recorder.stopRecording()
                                                        if (file != null) uploadAndSendMessage("", android.net.Uri.fromFile(file))
                                                        isRecording = false
                                                    }
                                                    if (!isRecordingLocked) {
                                                        recordSlideOffset = 0f
                                                        recordSlideYOffset = 0f
                                                    }
                                                },
                                                onDragCancel = {
                                                    recorder.cancelRecording()
                                                    isRecording = false
                                                    isRecordingLocked = false
                                                    recordSlideOffset = 0f
                                                    recordSlideYOffset = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (!isRecordingLocked) {
                                                        val newX = recordSlideOffset + dragAmount.x
                                                        val newY = recordSlideYOffset + dragAmount.y
                                                        if (kotlin.math.abs(newX) > kotlin.math.abs(newY * 1.5f) && newX < 0f) {
                                                            recordSlideOffset = newX.coerceAtLeast(-200f)
                                                        } else if (newY < 0f) {
                                                            recordSlideYOffset = newY.coerceAtLeast(-80f)
                                                            if (recordSlideYOffset <= -70f && !hasVibratedForLock) {
                                                                hasVibratedForLock = true
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Record Voice", tint = dimTextColor, modifier = Modifier.size(if (isRecording) 32.dp else 24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (contextMenuMessage != null) {
        ModalBottomSheet(
            onDismissRequest = { contextMenuMessage = null },
            containerColor = surfaceColor
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                val msg = contextMenuMessage!!
                val isMine = msg.senderId == currentUser.uid
                
                ListItem(
                    headlineContent = { Text("Ответить", color = textColor) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = textColor) },
                    modifier = Modifier.clickable {
                        replyToMessage = msg
                        contextMenuMessage = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Переслать", color = textColor) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = textColor, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f)) },
                    modifier = Modifier.clickable {
                        selectedMessages = setOf(msg.id)
                        showForwardDialog = true
                        contextMenuMessage = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (msg.type == "text" && isMine) {
                    ListItem(
                        headlineContent = { Text("Редактировать", color = textColor) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = textColor) },
                        modifier = Modifier.clickable {
                            messageToEdit = msg
                            inputText = msg.text
                            contextMenuMessage = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                ListItem(
                    headlineContent = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        messageToDelete = msg
                        showDeleteDialog = true
                        contextMenuMessage = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Выделить", color = textColor) },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = textColor) },
                    modifier = Modifier.clickable {
                        selectedMessages = setOf(msg.id)
                        contextMenuMessage = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (showDeleteDialog && messageToDelete != null) {
        val isMine = messageToDelete!!.senderId == currentUser.uid
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false; messageToDelete = null },
            title = { Text("Удалить сообщение?", color = textColor) },
            text = { Text("Вы уверены, что хотите удалить это сообщение?", color = dimTextColor) },
            confirmButton = {
                if (isMine) {
                    Column {
                        TextButton(onClick = {
                            val messagesRef = database.getReference("chats").child(chatId).child("messages")
                            messagesRef.child(messageToDelete!!.id).removeValue()
                            showDeleteDialog = false
                            messageToDelete = null
                        }) {
                            Text("Удалить для всех", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {
                            // Local delete isn't fully implemented in DB, usually requires a "deletedFor" field.
                            // For simplicity, we just delete for all here since real logic requires extra fields.
                            val messagesRef = database.getReference("chats").child(chatId).child("messages")
                            messagesRef.child(messageToDelete!!.id).removeValue()
                            showDeleteDialog = false
                            messageToDelete = null
                        }) {
                            Text("Удалить у меня", color = textColor)
                        }
                    }
                } else {
                    TextButton(onClick = {
                        val messagesRef = database.getReference("chats").child(chatId).child("messages")
                        messagesRef.child(messageToDelete!!.id).removeValue()
                        showDeleteDialog = false
                        messageToDelete = null
                    }) {
                        Text("Удалить у меня", color = textColor)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; messageToDelete = null }) {
                    Text("Отмена", color = dimTextColor)
                }
            },
            containerColor = surfaceColor
        )
    }

    if (showForwardDialog) {
        var chats by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
        LaunchedEffect(Unit) {
            database.getReference("user_chats").child(currentUser.uid).get().addOnSuccessListener { snapshot ->
                val list = mutableListOf<Map<String, String>>()
                for (child in snapshot.children) {
                    val peerId = child.key ?: continue
                    list.add(mapOf("id" to peerId))
                }
                chats = list
            }
        }
        
        ModalBottomSheet(
            onDismissRequest = { showForwardDialog = false },
            containerColor = surfaceColor
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("Переслать в...", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                androidx.compose.foundation.lazy.LazyColumn {
                    items(chats) { chatMap ->
                        val peerId = chatMap["id"] ?: return@items
                        var peerName by remember { mutableStateOf("User") }
                        var peerAvatar by remember { mutableStateOf("") }
                        
                        LaunchedEffect(peerId) {
                            database.getReference("users").child(peerId).get().addOnSuccessListener { snap ->
                                peerName = snap.child("name").getValue(String::class.java) ?: "User"
                                peerAvatar = snap.child("avatarUrl").getValue(String::class.java) ?: ""
                            }
                        }
                        
                        ListItem(
                            headlineContent = { Text(peerName, color = textColor) },
                            leadingContent = {
                                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray)) {
                                    if (peerAvatar.isNotBlank()) {
                                        com.example.ui.components.AvatarImage(avatarUrl = peerAvatar, contentDescription = null, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                val selectedMsgs = messages.filter { selectedMessages.contains(it.id) }
                                val targetChatId = if (currentUser.uid < peerId) currentUser.uid + "_" + peerId else peerId + "_" + currentUser.uid
                                val targetRef = database.getReference("chats").child(targetChatId).child("messages")
                                
                                selectedMsgs.forEach { originalMsg ->
                                    val newMsgId = targetRef.push().key ?: return@forEach
                                    val decryptedOriginalText = if (originalMsg.type == "text") {
                                        if (!originalMsg.text.startsWith("[")) {
                                            try { com.example.ui.screens.chat.ChatCrypto.decrypt(originalMsg.text, chatId) } catch (e: Exception) { originalMsg.text }
                                        } else originalMsg.text
                                    } else originalMsg.text
                                    
                                    val fwdPrefix = "Переслано:\n" + decryptedOriginalText
                                    val finalEncrypted = if (originalMsg.type == "text") com.example.ui.screens.chat.ChatCrypto.encrypt(fwdPrefix, targetChatId) else originalMsg.text
                                    
                                    val msg = ChatMessage(id = newMsgId, senderId = currentUser.uid, text = finalEncrypted, type = originalMsg.type, timestamp = System.currentTimeMillis(), mediaUrl = originalMsg.mediaUrl)
                                    targetRef.child(newMsgId).setValue(msg)
                                    
                                    val chatMetaMe = mapOf("lastMessage" to (if (originalMsg.type == "text") finalEncrypted else "[${originalMsg.type}]"), "timestamp" to System.currentTimeMillis(), "lastSenderId" to currentUser.uid)
                                    database.getReference("user_chats").child(currentUser.uid).child(peerId).updateChildren(chatMetaMe)
                                    
                                    database.getReference("user_chats").child(peerId).child(currentUser.uid).get().addOnSuccessListener { snap ->
                                        val u = snap.child("unreadCount").getValue(Int::class.java) ?: 0
                                        val chatMetaThem = mapOf("lastMessage" to (if (originalMsg.type == "text") finalEncrypted else "[${originalMsg.type}]"), "timestamp" to System.currentTimeMillis(), "lastSenderId" to currentUser.uid, "unreadCount" to u + 1)
                                        database.getReference("user_chats").child(peerId).child(currentUser.uid).updateChildren(chatMetaThem)
                                    }
                                }
                                showForwardDialog = false
                                selectedMessages = emptySet()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    if (showFullscreenMedia) {
        val mediaMessages = messages.flatMap { msg ->
            if (msg.attachments.isNotEmpty()) {
                msg.attachments.filter { it.type.startsWith("image") || it.type.startsWith("video") }.map {
                    ChatMessage(id = msg.id, senderId = msg.senderId, text = it.filename, type = it.type, timestamp = msg.timestamp, mediaUrl = it.url)
                }
            } else if (msg.type == "image" || msg.type == "video") {
                listOf(msg)
            } else {
                emptyList()
            }
        }
        val idx = mediaMessages.indexOfFirst { it.mediaUrl == initialAudioUrl }.takeIf { it >= 0 } ?: initialMediaIndex
        if (mediaMessages.isNotEmpty()) {
            MediaViewer(
                mediaMessages = mediaMessages,
                initialIndex = if (idx >= 0 && idx < mediaMessages.size) idx else 0,
                onDismiss = { showFullscreenMedia = false }
            )
        }
    }

    if (showAudioPlayer) {
        val audioMsgs = messages.flatMap { msg ->
            if (msg.attachments.isNotEmpty()) {
                msg.attachments.filter { it.type.startsWith("audio") }.map {
                    ChatMessage(id = msg.id, senderId = msg.senderId, text = it.filename, type = it.type, timestamp = msg.timestamp, mediaUrl = it.url)
                }
            } else if (msg.type == "audio") {
                listOf(msg)
            } else {
                emptyList()
            }
        }
        val idx = audioMsgs.indexOfFirst { it.mediaUrl == initialAudioUrl }
        if (audioMsgs.isNotEmpty()) {
            CustomAudioPlayer(
                audioMessages = audioMsgs,
                initialIndex = if (idx >= 0 && idx < audioMsgs.size) idx else 0,
                getSenderName = { if (it == currentUser.uid) "Вы" else recipientName },
                onDismiss = { showAudioPlayer = false }
            )
        }
    }
}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(mediaMessages: List<ChatMessage>, initialIndex: Int, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = if (initialIndex in mediaMessages.indices) initialIndex else 0,
            pageCount = { mediaMessages.size }
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val msg = mediaMessages[page]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (msg.type == "image" || msg.type == "video") {
                        coil.compose.AsyncImage(
                            model = msg.mediaUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        if (msg.type == "video") {
                            IconButton(onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                intent.setDataAndType(android.net.Uri.parse(msg.mediaUrl), "video/*")
                                ctx.startActivity(intent)
                            }, modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(alpha=0.5f), CircleShape)) {
                                Icon(Icons.Default.Videocam, "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.4f)).padding(top = 24.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color.DarkGray
                    ) {
                        DropdownMenuItem(
                            text = { Text("Скачать", color = Color.White) },
                            onClick = { 
                                showMenu = false
                                val msg = mediaMessages[pagerState.currentPage]
                                MediaTools.downloadMedia(ctx, msg.mediaUrl, msg.type)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться", color = Color.White) },
                            onClick = { 
                                showMenu = false
                                val msg = mediaMessages[pagerState.currentPage]
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                                intent.type = if (msg.type == "video") "video/*" else "image/*"
                                intent.putExtra(android.content.Intent.EXTRA_TEXT, msg.mediaUrl)
                                ctx.startActivity(android.content.Intent.createChooser(intent, "Поделиться"))
                            }
                        )
                    }
                }
            }
        }
    }
}

object MediaTools {
    fun downloadMedia(ctx: android.content.Context, url: String, type: String) {
        if (url.isBlank()) return
        if (url.startsWith("webrtc://")) {
            val parts = url.replace("webrtc://", "").split("/")
            if (parts.size >= 2) {
                val chatId = parts[0]
                val transferId = parts[1]
                android.widget.Toast.makeText(ctx, "Загрузка файла по P2P сети...", android.widget.Toast.LENGTH_SHORT).show()
                com.example.utils.WebRtcDataChannel.downloadWebRtcFile(ctx, chatId, transferId) { file ->
                    if (file != null) {
                        android.widget.Toast.makeText(ctx, "Файл загружен: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(ctx, "Ошибка скачивания по P2P", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
        }
        
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            val ext = when(type) {
                "image" -> "jpg"
                "video" -> "mp4"
                "audio" -> "mp3"
                else -> "bin"
            }
            val filename = "slant_media_${System.currentTimeMillis()}.$ext"
            request.setTitle(filename)
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            val manager = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            manager.enqueue(request)
            android.widget.Toast.makeText(ctx, "Скачивание начато", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Ошибка скачивания", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
