package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
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
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.compose.ui.platform.LocalContext

fun compressUriToBase64(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        inputStream.close()
        
        val maxDimension = 200
        val width = originalBitmap.width
        val height = originalBitmap.height
        val (newWidth, newHeight) = if (width > height) {
            val ratio = width.toFloat() / maxDimension
            Pair(maxDimension, (height / ratio).toInt())
        } else {
            val ratio = height.toFloat() / maxDimension
            Pair((width / ratio).toInt(), maxDimension)
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    
    val selectedLanguage by com.example.AppPreferences.language.collectAsState()
    val s: (String, String) -> String = { ru, en -> if (selectedLanguage == "English") en else ru }

    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val bgColor = if (isDarkTheme) Black else Color(0xFFF5F5F7)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)
    
    // Snackbar for errors/success
    val snackbarHostState = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                val base64 = compressUriToBase64(context, uri)
                if (base64 != null) {
                    avatarUrl = "data:image/jpeg;base64,$base64"
                } else {
                    snackbarHostState.showSnackbar(s("Не удалось обработать изображение", "Failed to process image"))
                }
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (user != null) {
            try {
                val snapshot = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
                    .getReference("users")
                    .child(user.uid)
                    .get()
                    .await()
                
                name = snapshot.child("name").getValue(String::class.java) ?: ""
                username = snapshot.child("username").getValue(String::class.java) ?: ""
                bio = snapshot.child("bio").getValue(String::class.java) ?: ""
                gender = snapshot.child("gender").getValue(String::class.java) ?: ""
                birthday = snapshot.child("birthday").getValue(String::class.java) ?: ""
                avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java) ?: ""
            } catch (e: Exception) {
                // Ignore initial load error
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) s("Редактирование", "Edit Profile") else s("Мой профиль", "My Profile"), color = textColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            isEditing = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s("Назад", "Back"), tint = textColor)
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            scope.launch {
                                if (user != null) {
                                    isLoading = true
                                    try {
                                        val updates = mapOf(
                                            "name" to name.trim(),
                                            "username" to username.trim().removePrefix("@"),
                                            "bio" to bio.trim(),
                                            "gender" to gender.trim(),
                                            "birthday" to birthday.trim(),
                                            "avatarUrl" to avatarUrl
                                        )
                                        FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
                                            .getReference("users")
                                            .child(user.uid)
                                            .updateChildren(updates)
                                            .await()
                                        isEditing = false
                                        snackbarHostState.showSnackbar(s("Профиль сохранен", "Profile saved"))
                                    } catch(e: Exception) {
                                        snackbarHostState.showSnackbar(s("Ошибка сохранения: ", "Save error: ") + e.localizedMessage)
                                    }
                                    isLoading = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = s("Сохранить", "Save"), tint = dimTextColor)
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = s("Редактировать", "Edit"), tint = dimTextColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                
                // Avatar image selection frame
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(surfaceColor)
                        .border(1.dp, if (isEditing) textColor else borderColor, RoundedCornerShape(36.dp))
                        .clickable(enabled = isEditing) {
                            galleryLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = avatarUrl,
                            contentDescription = s("Аватар", "Avatar"),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = s("Аватар", "Avatar"), tint = dimTextColor, modifier = Modifier.size(54.dp))
                    }
                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = s("Изменить", "Edit"),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                if (!isEditing) {
                    // View Mode: Display cleanly formatted content
                    Text(
                        text = if (name.isNotBlank()) name else s("Без имени", "No name"),
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
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
                    
                    // High-fidelity compact display cards
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(surfaceColor)
                            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CompactDetailItem(s("Обо мне", "About me"), if (bio.isNotBlank()) bio else s("Информация отсутствует", "No information"), textColor, dimTextColor)
                            HorizontalDivider(color = borderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                            CompactDetailItem(s("Пол", "Gender"), if (gender.isNotBlank()) gender else s("Не указан", "Not specified"), textColor, dimTextColor)
                            HorizontalDivider(color = borderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                            CompactDetailItem(s("Дата рождения", "Birth date"), if (birthday.isNotBlank()) birthday else s("Не указана", "Not specified"), textColor, dimTextColor)
                        }
                    }
                } else {
                    // Edit Mode: compact input fields with scrollability
                    Spacer(Modifier.height(16.dp))
                    
                    ProfileEditField(s("Имя", "Name"), name, textColor, dimTextColor, surfaceColor, borderColor) { name = it }
                    ProfileEditField(s("Юзернейм", "Username"), username, textColor, dimTextColor, surfaceColor, borderColor) { username = it }
                    ProfileEditField(s("Обо мне", "About me"), bio, textColor, dimTextColor, surfaceColor, borderColor) { bio = it }
                    ProfileEditField(s("Пол", "Gender"), gender, textColor, dimTextColor, surfaceColor, borderColor) { gender = it }
                    ProfileEditField(s("Дата рождения", "Birth date"), birthday, textColor, dimTextColor, surfaceColor, borderColor) { birthday = it }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CompactDetailItem(label: String, value: String, textColor: Color, dimTextColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ProfileEditField(label: String, value: String, textColor: Color, dimTextColor: Color, surfaceColor: Color, borderColor: Color, onValueChange: (String) -> Unit) {
    val isMultiline = label == "Обо мне" || label == "About me"
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(label, color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = !isMultiline,
            maxLines = if (isMultiline) 4 else 1,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = borderColor,
                focusedBorderColor = textColor,
                unfocusedTextColor = textColor,
                focusedTextColor = textColor,
                cursorColor = textColor,
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor
            )
        )
    }
}

