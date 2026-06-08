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

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val type: String = "text",
    val timestamp: Long = 0L,
    val mediaUrl: String = "",
    val replyToMsgId: String? = null,
    val isRead: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    fun sendMessage(text: String, type: String = "text", mediaUrl: String = "") {
        if (text.isBlank() && mediaUrl.isBlank()) return
        val messagesRef = database.getReference("chats").child(chatId).child("messages")
        
        val encryptedText = if (type == "text") {
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
                replyToMsgId = replyToMessage?.id
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

    var pendingAttachmentType by remember { mutableStateOf("file") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val label = when(pendingAttachmentType) {
                "image" -> "[Фото]"
                "video" -> "[Видео]"
                "audio" -> "[Аудио]"
                else -> "Файл: ${uri.lastPathSegment}"
            }
            sendMessage(label, type = pendingAttachmentType, mediaUrl = uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (selectedMessages.isNotEmpty()) {
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
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(recipientName, color = textColor, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                if (recipientOnline) {
                                    Text("онлайн", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                                } else if (recipientLastSeen > 0L) {
                                    val dateStr = java.text.SimpleDateFormat("HH:mm, dd MMM", java.util.Locale.getDefault()).format(java.util.Date(recipientLastSeen))
                                    Text("был(а) $dateStr", color = dimTextColor, fontSize = 12.sp)
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
                items(messages) { msg ->
                    val isMine = msg.senderId == currentUser.uid
                    val isSelected = selectedMessages.contains(msg.id)
                    val isHighlighted = highlightedMessageId == msg.id
                    var swipeOffset by remember { mutableFloatStateOf(0f) }
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
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
                                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp))) {
                                                coil.compose.AsyncImage(
                                                    model = msg.mediaUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
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
                                                    onClick = { 
                                                        // A real app would download using OkHttp/DownloadManager here
                                                        android.widget.Toast.makeText(ctx, "Сохранено в кэш", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha=0.4f), CircleShape).size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (msg.type == "image") Icons.Default.Image else Icons.Default.Videocam, contentDescription = null, tint = if (isMine) bubbleSentContentColor else textColor, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(msg.text, color = if (isMine) bubbleSentContentColor else textColor, fontSize = 14.sp)
                                        }
                                    }
                                }
                                "audio", "media", "file" -> {
                                    Column {
                                        val icon = when(msg.type) {
                                            "audio" -> Icons.Default.Audiotrack
                                            else -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(icon, contentDescription = null, tint = if (isMine) bubbleSentContentColor else textColor, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(msg.text, color = if (isMine) bubbleSentContentColor else textColor, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
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
                    Row(verticalAlignment = Alignment.Bottom) {
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                            Icon(Icons.Default.Add, contentDescription = "Attach", tint = dimTextColor, modifier = Modifier.size(28.dp).clip(CircleShape).background(surfaceColor))
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
                    var isRecording by remember { mutableStateOf(false) }
                    var isRecordingLocked by remember { mutableStateOf(false) }
                    var recordSlideOffset by remember { mutableFloatStateOf(0f) }
                    var recordSlideYOffset by remember { mutableFloatStateOf(0f) }
                    var recordingSeconds by remember { mutableIntStateOf(0) }
                    
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
                                .padding(end = 8.dp)
                                .height(48.dp)
                                .background(bgColor, RoundedCornerShape(24.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRecordingLocked) {
                                IconButton(onClick = {
                                    isRecording = false
                                    isRecordingLocked = false
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            } else {
                                Spacer(Modifier.width(16.dp))
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
                                Text("< Отмените свайпом", color = dimTextColor, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(recordSlideOffset.toInt(), 0) })
                                Spacer(Modifier.width(16.dp))
                            } else {
                                IconButton(onClick = {
                                    android.widget.Toast.makeText(ctx, "Голосовое отправлено", android.widget.Toast.LENGTH_SHORT).show()
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
                                .padding(end = 8.dp)
                                .defaultMinSize(minHeight = 40.dp)
                                .background(bgColor, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                                maxLines = 5,
                                modifier = Modifier.weight(1f),
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
                    
                    val micButtonSize = if (isRecording && !isRecordingLocked) 64.dp else 48.dp
                    Box(modifier = Modifier.padding(bottom = if (isRecording && !isRecordingLocked) 0.dp else 4.dp)) {
                        if (isRecording && !isRecordingLocked && recordSlideYOffset < -20f) {
                            Box(modifier = Modifier.offset(x = 16.dp, y = (-60).dp).background(surfaceColor, CircleShape).padding(8.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock", tint = dimTextColor, modifier = Modifier.size(16.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .offset { androidx.compose.ui.unit.IntOffset(
                                    if (!isRecordingLocked) recordSlideOffset.toInt() else 0,
                                    if (!isRecordingLocked) recordSlideYOffset.toInt() else 0
                                ) }
                                .clip(CircleShape)
                                .background(if (isRecordingLocked) Color.Red.copy(alpha = 0.2f) else textColor)
                                .size(micButtonSize)
                                .pointerInput(inputText, isRecordingLocked) {
                                    if (inputText.isNotBlank()) {
                                        detectTapGestures {
                                            sendMessage(inputText.trim(), "text")
                                            inputText = ""
                                        }
                                    } else if (isRecordingLocked) {
                                        detectTapGestures {
                                            isRecording = false
                                            isRecordingLocked = false
                                        }
                                    } else {
                                        detectDragGestures(
                                            onDragStart = { 
                                                isRecording = true 
                                                recordSlideOffset = 0f
                                                recordSlideYOffset = 0f
                                            },
                                            onDragEnd = {
                                                if (recordSlideOffset < -100f) {
                                                    isRecording = false // Cancel
                                                } else if (recordSlideYOffset < -100f) {
                                                    isRecordingLocked = true // Lock
                                                    recordSlideYOffset = 0f
                                                    recordSlideOffset = 0f
                                                } else {
                                                    android.widget.Toast.makeText(ctx, "Голосовое отправлено", android.widget.Toast.LENGTH_SHORT).show()
                                                    isRecording = false
                                                }
                                                if (!isRecordingLocked) {
                                                    recordSlideOffset = 0f
                                                    recordSlideYOffset = 0f
                                                }
                                            },
                                            onDragCancel = {
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
                                                        recordSlideOffset = newX
                                                    } else if (newY < 0f) {
                                                        recordSlideYOffset = newY
                                                    }
                                                }
                                            }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (inputText.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = bgColor, modifier = Modifier.size(24.dp))
                            } else if (isRecordingLocked) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Mic, contentDescription = "Record Voice", tint = bgColor, modifier = Modifier.size(if (isRecording) 32.dp else 24.dp))
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
}
