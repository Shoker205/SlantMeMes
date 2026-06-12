package com.example.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN, REGISTER, VERIFY, PROFILE_SETUP, RECOVER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    var currentMode by remember { mutableStateOf(AuthMode.LOGIN) }
    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val globalLanguage by com.example.AppPreferences.language.collectAsState()
    val isEnglish = globalLanguage == "English"

    fun s(ru: String, en: String): String = if (isEnglish) en else ru

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    
    var profileName by remember { mutableStateOf("") }
    var profileUsername by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var profileGender by remember { mutableStateOf("") }
    var profileBirthday by remember { mutableStateOf("") }
    
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isPasswordConfirmVisible by remember { mutableStateOf(false) }
    
    var currentMascotState by remember { mutableStateOf(MascotState.Idle) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIsSuccess by remember { mutableStateOf(false) }
    
    var datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }
    var profileAvatarUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? -> 
        profileAvatarUri = uri
    }
    
    val scope = rememberCoroutineScope()

    val bgColor = if (isDarkTheme) Black else Color(0xFFF5F5F7)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)

    fun showToast(msg: String, success: Boolean = false) {
        toastMessage = msg
        toastIsSuccess = success
        scope.launch {
            delay(3000)
            if (toastMessage == msg) toastMessage = null
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { 
                    showDatePicker = false 
                    datePickerState.selectedDateMillis?.let { 
                        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        profileBirthday = sdf.format(Date(it))
                    }
                }) { Text(s("ОК", "OK"), color = if(isDarkTheme) Color.White else Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(s("Отмена", "Cancel"), color = dimTextColor) }
            },
            colors = DatePickerDefaults.colors(containerColor = surfaceColor)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = surfaceColor,
                    titleContentColor = textColor,
                    headlineContentColor = textColor,
                    weekdayContentColor = dimTextColor,
                    subheadContentColor = dimTextColor,
                    yearContentColor = dimTextColor,
                    currentYearContentColor = textColor,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = if(isDarkTheme) Color.DarkGray else Color.Black,
                    dayContentColor = textColor,
                    disabledDayContentColor = dimTextColor,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = if(isDarkTheme) Color.DarkGray else Color.Black,
                    todayContentColor = if(isDarkTheme) Color.White else Color.Black,
                    todayDateBorderColor = if(isDarkTheme) Color.White else Color.Black
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { 
                com.example.AppPreferences.setLanguage(if (isEnglish) "Русский" else "English")
            }) {
                Icon(Icons.Default.Language, contentDescription = "Language", tint = dimTextColor)
            }
            IconButton(onClick = { 
                com.example.AppPreferences.setDarkTheme(!isDarkTheme) 
            }) {
                Icon(
                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, 
                    contentDescription = "Theme", 
                    tint = dimTextColor
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Return button for non-login screens
                if (currentMode != AuthMode.LOGIN) {
                    IconButton(
                        onClick = { 
                            currentMode = when(currentMode) {
                                AuthMode.REGISTER -> AuthMode.LOGIN
                                AuthMode.VERIFY -> AuthMode.REGISTER
                                AuthMode.RECOVER -> AuthMode.LOGIN
                                else -> AuthMode.LOGIN
                            }
                        },
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = dimTextColor)
                    }
                }
                
                NeuralFluidMascot(
                    modifier = Modifier.fillMaxSize(),
                    state = currentMascotState,
                    isError = toastMessage != null && !toastIsSuccess,
                    isDarkTheme = isDarkTheme
                )
            }
            
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                }, label = "AuthContent"
            ) { mode ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    when (mode) {
                        AuthMode.LOGIN -> {
                            Text("SLANT", color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(s("НЕЙРОННЫЙ УЗЕЛ", "NEURAL NODE"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 40.dp))
                            
                            AuthTextField(
                                value = email, onValueChange = { email = it }, placeholder = s("ПОЧТА", "EMAIL"),
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.User else if(currentMascotState == MascotState.User) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(16.dp))
                            AuthTextField(
                                value = password, onValueChange = { password = it }, placeholder = s("ПАРОЛЬ", "PASSWORD"),
                                isPassword = true, passwordVisible = isPasswordVisible, onTogglePassword = { isPasswordVisible = !isPasswordVisible },
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.Pass else if(currentMascotState == MascotState.Pass) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(32.dp))
                            MainButton(s("ВОЙТИ", "LOG IN"), isDarkTheme) {
                                if (email.isNotBlank() && password.length >= 8) {
                                    currentMascotState = MascotState.Loading
                                    scope.launch {
                                        try {
                                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await()
                                            showToast(s("УЗЕЛ СИНХРОНИЗИРОВАН", "NODE SYNCHRONIZED"), true)
                                            onAuthSuccess()
                                        } catch (e: Exception) {
                                            showToast(e.localizedMessage ?: s("Ошибка", "Error"))
                                            currentMascotState = MascotState.Idle
                                        }
                                    }
                                } else showToast(s("Некорректный пароль или email", "Invalid email or password"))
                            }
                            Spacer(Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { currentMode = AuthMode.RECOVER; currentMascotState = MascotState.Idle }) {
                                    Text(s("Забыли пароль?", "Forgot password?"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { currentMode = AuthMode.REGISTER; currentMascotState = MascotState.Idle }) {
                                    Text(s("Регистрация", "Register"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        
                        AuthMode.REGISTER -> {
                            Text(s("РЕГИСТРАЦИЯ", "REGISTRATION"), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(s("СОЗДАНИЕ ЛИЧНОСТИ", "CREATING IDENTITY"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 40.dp))
                            
                            AuthTextField(
                                value = email, onValueChange = { email = it }, placeholder = s("ПОЧТА", "EMAIL"),
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.Genesis else if(currentMascotState == MascotState.Genesis) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(16.dp))
                            AuthTextField(
                                value = password, onValueChange = { password = it }, placeholder = s("ПАРОЛЬ", "PASSWORD"),
                                isPassword = true, passwordVisible = isPasswordVisible, onTogglePassword = { isPasswordVisible = !isPasswordVisible },
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.Pass else if(currentMascotState == MascotState.Pass) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(8.dp))
                            PassStrengthMeter(password, isDarkTheme, isEnglish)
                            Spacer(Modifier.height(16.dp))
                            AuthTextField(
                                value = passwordConfirm, onValueChange = { passwordConfirm = it }, placeholder = s("ПОВТОРНЫЙ ПАРОЛЬ", "REPEAT PASSWORD"),
                                isPassword = true, passwordVisible = isPasswordConfirmVisible, onTogglePassword = { isPasswordConfirmVisible = !isPasswordConfirmVisible },
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.Pass else if(currentMascotState == MascotState.Pass) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(32.dp))
                            MainButton(s("ПРОДОЛЖИТЬ", "CONTINUE"), isDarkTheme) {
                                if (password.length >= 8 && password == passwordConfirm && email.isNotBlank()) {
                                    currentMascotState = MascotState.Loading
                                    scope.launch {
                                        try {
                                            val user = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password).await().user
                                            
                                            if (user != null) {
                                                val verificationCode = (1000..9999).random().toString()
                                                val db = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/").getReference("verification_codes").child(user.uid)
                                                db.setValue(verificationCode).await()
                                            }

                                            showToast(s("КОД В БД FIREBASE", "CODE IN FIREBASE DB"), true)
                                            currentMode = AuthMode.VERIFY
                                            currentMascotState = MascotState.Idle
                                        } catch (e: Exception) {
                                            val msg = e.localizedMessage ?: s("Ошибка регистрации", "Registration error")
                                            if (msg.contains("Permission denied", ignoreCase = true)) {
                                                showToast(s("Измените правила БД на: .write: auth != null", "Change DB rules to: .write: auth != null"))
                                            } else {
                                                showToast(msg)
                                            }
                                            currentMascotState = MascotState.Idle
                                        }
                                    }
                                } else showToast(s("Проверьте введенные данные", "Check entered data"))
                            }
                        }
                        
                        AuthMode.VERIFY -> {
                            Text(s("ПОДТВЕРЖДЕНИЕ", "VERIFICATION"), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(s("ВВЕДИТЕ КОД ИЗ БД FIREBASE", "ENTER CODE FROM FIREBASE DB"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 40.dp))
                            
                            AuthTextField(
                                value = code, onValueChange = { code = it }, placeholder = s("4-ЗНАЧНЫЙ КОД", "4-DIGIT CODE"),
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.Recall else if(currentMascotState == MascotState.Recall) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(32.dp))
                            MainButton(s("ПОДТВЕРДИТЬ", "VERIFY"), isDarkTheme) {
                                if (code.length >= 4) {
                                    currentMascotState = MascotState.Loading
                                    scope.launch {
                                        try {
                                            val user = FirebaseAuth.getInstance().currentUser
                                            if (user != null) {
                                                val snapshot = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
                                                    .getReference("verification_codes")
                                                    .child(user.uid)
                                                    .get()
                                                    .await()
                                                
                                                val savedCode = snapshot.getValue(String::class.java)
                                                if (savedCode == code.trim()) {
                                                    FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
                                                        .getReference("verification_codes")
                                                        .child(user.uid)
                                                        .removeValue()
                                                        .await()

                                                    showToast(s("ЭМАЙЛ ПОДТВЕРЖДЕН", "EMAIL VERIFIED"), true)
                                                    currentMode = AuthMode.PROFILE_SETUP
                                                    currentMascotState = MascotState.Idle
                                                } else {
                                                    showToast(s("Неверный код", "Invalid code"))
                                                    currentMascotState = MascotState.Idle
                                                }
                                            } else {
                                                showToast(s("Ошибка сессии", "Session error"))
                                                currentMascotState = MascotState.Idle
                                            }
                                        } catch (e: Exception) {
                                            val msg = e.localizedMessage ?: s("Ошибка", "Error")
                                            if (msg.contains("Permission denied", ignoreCase = true)) {
                                                showToast(s("Измените правила БД (read: auth != null)", "Change DB rules (read: auth != null)"))
                                            } else {
                                                showToast(msg)
                                            }
                                            currentMascotState = MascotState.Idle
                                        }
                                    }
                                } else showToast(s("Введите код", "Enter code"))
                            }
                        }
                        
                        AuthMode.PROFILE_SETUP -> {
                            Text(s("ПРОФИЛЬ", "PROFILE"), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(s("НАСТРОЙКА ЛИЧНОСТИ", "IDENTITY SETUP"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 24.dp))
                            
                            val avatarColors = listOf(Color(0xFF8E24AA), Color(0xFF3949AB), Color(0xFF00897B), Color(0xFFE53935), Color(0xFFFB8C00))
                            val avatarBg = remember(profileName) { avatarColors[Math.abs(profileName.hashCode()) % avatarColors.size] }

                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(if (profileAvatarUri == null && profileName.isNotBlank()) avatarBg else surfaceColor, androidx.compose.foundation.shape.CircleShape)
                                    .border(1.dp, borderColor, androidx.compose.foundation.shape.CircleShape)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { launcher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileAvatarUri != null) {
                                    AsyncImage(
                                        model = profileAvatarUri,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (profileName.isNotBlank()) {
                                    Text(profileName.take(1).uppercase(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "Avatar", tint = dimTextColor, modifier = Modifier.size(40.dp))
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AuthTextField(
                                    value = profileName, onValueChange = { profileName = it }, placeholder = s("ИМЯ (ОБЯЗАТЕЛЬНО)", "NAME (REQUIRED)"),
                                    isDarkTheme = isDarkTheme,
                                    onFocus = { }
                                )
                                AuthTextField(
                                    value = profileUsername, onValueChange = { profileUsername = it }, placeholder = s("USERNAME (@ID)", "USERNAME (@ID)"),
                                    isDarkTheme = isDarkTheme,
                                    onFocus = { }
                                )
                                AuthTextField(
                                    value = profileBio, onValueChange = { profileBio = it }, placeholder = s("ОПИСАНИЕ", "BIO"),
                                    isDarkTheme = isDarkTheme,
                                    onFocus = { }
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        AuthTextField(
                                            value = profileGender, onValueChange = {}, placeholder = s("ПОЛ", "GENDER"),
                                            isDarkTheme = isDarkTheme,
                                            readOnly = true,
                                            onFocus = { }
                                        )
                                        Box(modifier = Modifier.matchParentSize().clickable { genderExpanded = true }.background(Color.Transparent))
                                        DropdownMenu(
                                            expanded = genderExpanded,
                                            onDismissRequest = { genderExpanded = false },
                                            modifier = Modifier.background(surfaceColor)
                                        ) {
                                            val genders = if (isEnglish) listOf("Male", "Female", "Other", "Prefer not to say") else listOf("Мужской", "Женский", "Другое", "Не указан")
                                            genders.forEach { g ->
                                                DropdownMenuItem(
                                                    text = { Text(g, color = textColor) },
                                                    onClick = { profileGender = g; genderExpanded = false }
                                                )
                                            }
                                        }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        AuthTextField(
                                            value = profileBirthday, onValueChange = {}, placeholder = s("Д/Р", "DOB"),
                                            isDarkTheme = isDarkTheme,
                                            readOnly = true,
                                            onFocus = { }
                                        )
                                        Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true }.background(Color.Transparent))
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            MainButton(s("ЗАВЕРШИТЬ", "COMPLETE"), isDarkTheme) {
                                if (profileName.isNotBlank()) {
                                    currentMascotState = MascotState.Loading
                                    scope.launch {
                                        try {
                                            val user = FirebaseAuth.getInstance().currentUser
                                            if (user != null) {
                                                val db = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/").getReference("users").child(user.uid)
                                                var finalAvatarUrl = ""
                                                if (profileAvatarUri != null) {
                                                    try {
                                                        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().getReference("avatars/${user.uid}.jpg")
                                                        storageRef.putFile(profileAvatarUri!!).await()
                                                        finalAvatarUrl = storageRef.downloadUrl.await().toString()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                val userData = mapOf(
                                                    "uid" to user.uid,
                                                    "email" to user.email,
                                                    "name" to profileName,
                                                    "username" to profileUsername,
                                                    "bio" to profileBio,
                                                    "gender" to profileGender,
                                                    "birthday" to profileBirthday,
                                                    "avatarUrl" to finalAvatarUrl
                                                )
                                                db.setValue(userData).await()
                                                showToast(s("ЛИЧНОСТЬ СОЗДАНА", "IDENTITY CREATED"), true)
                                                onAuthSuccess()
                                            } else {
                                                showToast(s("Ошибка", "Error"))
                                                currentMascotState = MascotState.Idle
                                            }
                                        } catch (e: Exception) {
                                            val msg = e.localizedMessage ?: s("Ошибка", "Error")
                                            if (msg.contains("Permission denied", ignoreCase = true)) {
                                                showToast(s("Измените правила БД на: .write: auth != null", "Change DB rules to: .write: auth != null"))
                                            } else {
                                                showToast(msg)
                                            }
                                            currentMascotState = MascotState.Idle
                                        }
                                    }
                                } else showToast(s("Введите имя", "Enter name"))
                            }
                        }
                        
                        AuthMode.RECOVER -> {
                            Text(s("ВОССТАНОВЛЕНИЕ", "RECOVERY"), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(s("ВОССТАНОВЛЕНИЕ ЛИЧНОСТИ", "IDENTITY RECOVERY"), color = dimTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 40.dp))
                            
                            AuthTextField(
                                value = email, onValueChange = { email = it }, placeholder = s("ПОЧТА", "EMAIL"),
                                isDarkTheme = isDarkTheme,
                                onFocus = { if(it) currentMascotState = MascotState.Recall else if(currentMascotState == MascotState.Recall) currentMascotState = MascotState.Idle }
                            )
                            Spacer(Modifier.height(32.dp))
                            MainButton(s("ОТПРАВИТЬ ССЫЛКУ", "SEND LINK"), isDarkTheme) {
                                if (email.isNotBlank()) {
                                    currentMascotState = MascotState.Loading
                                    scope.launch {
                                        try {
                                            FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
                                            showToast(s("ССЫЛКА ОТПРАВЛЕНА", "LINK SENT"), true)
                                            currentMode = AuthMode.LOGIN
                                            currentMascotState = MascotState.Idle
                                        } catch (e: Exception) {
                                            showToast(e.localizedMessage ?: s("Ошибка", "Error"))
                                            currentMascotState = MascotState.Idle
                                        }
                                    }
                                } else showToast(s("Введите email", "Enter email"))
                            }
                        }
                    }
                }
            }
        }
        
        // Custom Swipe-to-dismiss Toast
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
        ) {
            val borderColor = if (toastIsSuccess) SuccessGreen else ErrorRed
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(surfaceColor, RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (Math.abs(dragAmount) > 20) {
                                toastMessage = null
                            }
                        }
                    }
                    .padding(16.dp)
            ) {
                Text(toastMessage ?: "", color = borderColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isDarkTheme: Boolean,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    readOnly: Boolean = false,
    onTogglePassword: () -> Unit = {},
    onFocus: (Boolean) -> Unit
) {
    val fieldColor = if (isDarkTheme) DarkSurface else White
    val borderCol = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textCol = if (isDarkTheme) White else Black
    val dimTextCol = if (isDarkTheme) DimText else Color(0xFF666666)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocus(it.isFocused) },
        placeholder = { Text(placeholder, color = dimTextCol.copy(alpha = 0.5f), fontSize = 13.sp) },
        visualTransformation = if (!isPassword || passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = dimTextCol,
            unfocusedBorderColor = borderCol,
            focusedContainerColor = fieldColor,
            unfocusedContainerColor = fieldColor,
            focusedTextColor = textCol,
            unfocusedTextColor = textCol
        ),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle Password",
                        tint = if (passwordVisible) textCol else dimTextCol
                    )
                }
            }
        } else null
    )
}

@Composable
fun MainButton(text: String, isDarkTheme: Boolean, onClick: () -> Unit) {
    val bgCol = if (isDarkTheme) White else Black
    val textCol = if (isDarkTheme) Black else White
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgCol, contentColor = textCol)
    ) {
        Text(text, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
fun PassStrengthMeter(password: String, isDarkTheme: Boolean, isEnglish: Boolean) {
    val len = password.length >= 8
    val cap = password.any { it.isUpperCase() }
    val num = password.any { it.isDigit() }
    val score = listOf(len, cap, num).count { it }
    
    val dimTextCol = if (isDarkTheme) DimText else Color(0xFF666666)
    val borderCol = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    
    val color = when(score) {
        0 -> Color.Transparent
        1 -> ErrorRed
        2 -> Color(0xFFFFCC00)
        else -> SuccessGreen
    }
    
    val widthFrac = when(score) {
        0 -> 0f
        1 -> 0.33f
        2 -> 0.66f
        else -> 1f
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(borderCol, RoundedCornerShape(1.5.dp))) {
            Box(modifier = Modifier.fillMaxWidth(widthFrac).height(3.dp).background(color, RoundedCornerShape(1.5.dp)))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(if(isEnglish) "8+ chars" else "8+ символов", color = if(len) dimTextCol.copy(alpha=0.4f) else dimTextCol, fontSize = 9.sp)
            Text(" • ", color = dimTextCol, fontSize = 9.sp)
            Text(if(isEnglish) "Uppercase" else "Заглавная", color = if(cap) dimTextCol.copy(alpha=0.4f) else dimTextCol, fontSize = 9.sp)
            Text(" • ", color = dimTextCol, fontSize = 9.sp)
            Text(if(isEnglish) "Number" else "Цифра", color = if(num) dimTextCol.copy(alpha=0.4f) else dimTextCol, fontSize = 9.sp)
        }
    }
}
