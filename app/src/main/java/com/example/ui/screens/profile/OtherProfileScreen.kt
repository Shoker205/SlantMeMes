package com.example.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onChatClick: (String) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var isContact by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")

    val selectedLanguage by com.example.AppPreferences.language.collectAsState()
    val s: (String, String) -> String = { ru, en -> if (selectedLanguage == "English") en else ru }

    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val bgColor = if (isDarkTheme) Black else Color(0xFFF5F5F7)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)

    LaunchedEffect(userId) {
        if (currentUser != null) {
            try {
                // Load User Profile details
                val snapshot = database.getReference("users").child(userId).get().await()
                name = snapshot.child("name").getValue(String::class.java) ?: s("Пользователь", "User")
                username = snapshot.child("username").getValue(String::class.java) ?: ""
                bio = snapshot.child("bio").getValue(String::class.java) ?: ""
                gender = snapshot.child("gender").getValue(String::class.java) ?: ""
                birthday = snapshot.child("birthday").getValue(String::class.java) ?: ""
                avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java) ?: ""

                // Verify contact status
                val contactSnap = database.getReference("users")
                    .child(currentUser.uid)
                    .child("contacts")
                    .child(userId)
                    .get()
                    .await()
                isContact = contactSnap.exists()
            } catch (e: Exception) {
                // Keep default empty values
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s("Профиль", "Profile"), color = textColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s("Назад", "Back"), tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = dimTextColor)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))

                // Profile Image View Container
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderColor, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        com.example.ui.components.AvatarImage(
                            avatarUrl = avatarUrl,
                            contentDescription = s("Аватар", "Avatar"),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = s("Аватар", "Avatar"),
                            tint = dimTextColor,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Name and @username
                Text(
                    text = name,
                    color = textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (username.isNotBlank()) {
                    Text(
                        text = "@$username",
                        color = dimTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Details Card Row/Grid compliant with Material 3 design
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailItem(s("Обо мне", "About me"), if (bio.isNotBlank()) bio else s("Информация отсутствует", "No information"), textColor, dimTextColor)
                        DetailItem(s("Пол", "Gender"), if (gender.isNotBlank()) gender else s("Не указан", "Not specified"), textColor, dimTextColor)
                        DetailItem(s("Дата рождения", "Birth date"), if (birthday.isNotBlank()) birthday else s("Не указана", "Not specified"), textColor, dimTextColor)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Add to contacts working button
                Button(
                    onClick = {
                        scope.launch {
                            if (currentUser != null) {
                                val contactsRef = database.getReference("users")
                                    .child(currentUser.uid)
                                    .child("contacts")
                                    .child(userId)
                                
                                if (isContact) {
                                    contactsRef.removeValue().await()
                                    isContact = false
                                    com.example.ui.screens.chat.ContactsCache.cachedContacts = null
                                } else {
                                    contactsRef.setValue(true).await()
                                    isContact = true
                                    com.example.ui.screens.chat.ContactsCache.cachedContacts = null
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isContact) borderColor else textColor,
                        contentColor = if (isContact) textColor else bgColor
                    ),
                    border = if (isContact) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null
                ) {
                    Icon(
                        imageVector = if (isContact) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isContact) s("Удалить из контактов", "Remove from contacts") else s("Добавить в контакты", "Add to contacts"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                // "Write" (Написать) button
                Button(
                    onClick = { onChatClick(userId) },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = textColor,
                        contentColor = bgColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = s("Написать", "Write"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, textColor: Color, dimTextColor: Color) {
    Column {
        Text(text = label, color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
