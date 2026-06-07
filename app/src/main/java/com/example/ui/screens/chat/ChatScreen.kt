package com.example.ui.screens.chat

import android.net.Uri
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val mediaUrl: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    recipientId: String,
    onBack: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("User") }
    var recipientAvatar by remember { mutableStateOf("") }
    
    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
    
    val chatId = if (currentUser.uid < recipientId) "${currentUser.uid}_$recipientId" else "${recipientId}_${currentUser.uid}"

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val bgColor = if (isDarkTheme) Black else Color(0xFFEBEBEB)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)
    
    val bubbleSentContentColor = White
    val bubbleReceivedColor = if (isDarkTheme) Color(0xFF1E1E1E) else White

    var showAttachmentMenu by remember { mutableStateOf(false) }

    LaunchedEffect(recipientId) {
        val userSnap = database.getReference("users").child(recipientId).get().await()
        recipientName = userSnap.child("name").getValue(String::class.java) ?: "User"
        recipientAvatar = userSnap.child("avatarUrl").getValue(String::class.java) ?: ""
        
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
                    
                    val decryptedText = if (type == "text") ChatCrypto.decrypt(encryptedText, chatId) else encryptedText
                    
                    newMessages.add(ChatMessage(id, senderId, decryptedText, type, timestamp, mediaUrl))
                }
                messages = newMessages.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun sendMessage(text: String, type: String = "text", mediaUrl: String = "") {
        if (text.isBlank() && mediaUrl.isBlank()) return
        val messagesRef = database.getReference("chats").child(chatId).child("messages")
        val newMsgId = messagesRef.push().key ?: return
        
        val encryptedText = if (type == "text") ChatCrypto.encrypt(text, chatId) else text
        
        val msg = ChatMessage(newMsgId, currentUser.uid, encryptedText, type, System.currentTimeMillis(), mediaUrl)
        messagesRef.child(newMsgId).setValue(msg)
        
        val chatMeta = mapOf(
            "lastMessage" to (if (type == "text") encryptedText else "[$type]"),
            "timestamp" to System.currentTimeMillis()
        )
        database.getReference("user_chats").child(currentUser.uid).child(recipientId).setValue(chatMeta)
        database.getReference("user_chats").child(recipientId).child(currentUser.uid).setValue(chatMeta)
        
        scope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val type = "media"
            sendMessage("Файл: ${uri.lastPathSegment}", type = "file", mediaUrl = uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
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
                        Text(recipientName, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(
                                    topStart = 16.dp, 
                                    topEnd = 16.dp, 
                                    bottomStart = if (isMine) 16.dp else 4.dp, 
                                    bottomEnd = if (isMine) 4.dp else 16.dp
                                ))
                                .background(if (isMine) Color(0xFF007AFF) else bubbleReceivedColor)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            when (msg.type) {
                                "text" -> {
                                    Text(
                                        text = msg.text,
                                        color = if (isMine) bubbleSentContentColor else textColor,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                                "image", "video", "audio", "media", "file" -> {
                                    Column {
                                        val icon = when(msg.type) {
                                            "image" -> Icons.Default.Image
                                            "video" -> Icons.Default.Videocam
                                            "audio" -> Icons.Default.Audiotrack
                                            else -> Icons.AutoMirrored.Filled.InsertDriveFile
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
                Row(verticalAlignment = Alignment.Bottom) {
                    Box {
                        IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = dimTextColor)
                        }
                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false },
                            containerColor = surfaceColor
                        ) {
                            DropdownMenuItem(
                                text = { Text("Изображение", color = textColor) },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = dimTextColor) },
                                onClick = { showAttachmentMenu = false; launcher.launch("image/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("Видео", color = textColor) },
                                leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = dimTextColor) },
                                onClick = { showAttachmentMenu = false; launcher.launch("video/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("Аудио", color = textColor) },
                                leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = dimTextColor) },
                                onClick = { showAttachmentMenu = false; launcher.launch("audio/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("Файл", color = textColor) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = dimTextColor) },
                                onClick = { showAttachmentMenu = false; launcher.launch("*/*") }
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text("Сообщение...", color = dimTextColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = bgColor,
                            unfocusedContainerColor = bgColor,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 5
                    )
                    
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                sendMessage(inputText.trim(), "text")
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF007AFF))
                            .size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}
