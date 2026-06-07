package com.example.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
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
    onBack: () -> Unit
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

    LaunchedEffect(userId) {
        if (currentUser != null) {
            try {
                // Load User Profile details
                val snapshot = database.getReference("users").child(userId).get().await()
                name = snapshot.child("name").getValue(String::class.java) ?: "Пользователь"
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
                title = { Text("Профиль", color = White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        },
        containerColor = Black
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DimText)
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
                        .background(DarkSurface)
                        .border(1.dp, LightSurface, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Аватар",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Аватар",
                            tint = DimText,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Name and @username
                Text(
                    text = name,
                    color = White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (username.isNotBlank()) {
                    Text(
                        text = "@$username",
                        color = DimText,
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
                        .background(DarkSurface)
                        .border(1.dp, LightSurface, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailItem("Обо мне", if (bio.isNotBlank()) bio else "Информация отсутствует")
                        DetailItem("Пол", if (gender.isNotBlank()) gender else "Не указан")
                        DetailItem("Дата рождения", if (birthday.isNotBlank()) birthday else "Не указана")
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
                                } else {
                                    contactsRef.setValue(true).await()
                                    isContact = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isContact) DarkSurface else White,
                        contentColor = if (isContact) White else Black
                    ),
                    border = if (isContact) borderStroke() else null
                ) {
                    Icon(
                        imageVector = if (isContact) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isContact) "Удалить из контактов" else "Добавить в контакты",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Disabled "Write" (Написать) button
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF1E1E1E),
                        disabledContentColor = Color(0xFF555555)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Написать (Временно недоступно)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(1.dp, LightSurface)

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, color = DimText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
