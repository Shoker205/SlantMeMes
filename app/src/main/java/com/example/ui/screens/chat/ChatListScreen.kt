package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.LaunchedEffect

data class ChatItem(val id: String, val name: String, val lastMessage: String, val isOnline: Boolean, val timestamp: Long = 0L, val avatarUrl: String = "", val unreadCount: Int = 0)
data class UserProfile(val uid: String, val name: String, val username: String, val avatarUrl: String)

@Composable
fun ChatListScreen(
    onOpenDrawer: () -> Unit = {},
    onChatClick: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedDockTab by remember { mutableIntStateOf(0) }
    
    var profileName by remember { mutableStateOf("Name") }
    var profileUsername by remember { mutableStateOf("@username") }
    var profileAvatarUrl by remember { mutableStateOf("") }
    
    val selectedLanguage by com.example.AppPreferences.language.collectAsState()
    val s: (String, String) -> String = { ru, en -> if (selectedLanguage == "English") en else ru }

    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val bgColor = if (isDarkTheme) Black else Color(0xFFF5F5F7)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Search and contacts state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var contactsList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(false) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")

    // Load contacts implementation
    LaunchedEffect(selectedDockTab, searchQuery) {
        if (selectedDockTab == 1 && currentUser != null && searchQuery.isBlank()) {
            isLoadingContacts = true
            try {
                val contactsSnap = database.getReference("users")
                    .child(currentUser.uid)
                    .child("contacts")
                    .get()
                    .await()
                
                val list = mutableListOf<UserProfile>()
                for (child in contactsSnap.children) {
                    val uid = child.key ?: continue
                    val userSnap = database.getReference("users").child(uid).get().await()
                    if (userSnap.exists()) {
                        val name = userSnap.child("name").getValue(String::class.java) ?: "User"
                        val username = userSnap.child("username").getValue(String::class.java) ?: ""
                        val avatarUrl = userSnap.child("avatarUrl").getValue(String::class.java) ?: ""
                        list.add(UserProfile(uid, name, username, avatarUrl))
                    }
                }
                contactsList = list
            } catch (e: Exception) {
                // error
            }
            isLoadingContacts = false
        }
    }

    // Load search query implementation
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && currentUser != null) {
            isSearching = true
            try {
                val usersSnap = database.getReference("users").get().await()
                val list = mutableListOf<UserProfile>()
                val queryClean = searchQuery.trim().lowercase().removePrefix("@")
                
                for (child in usersSnap.children) {
                    val uid = child.key ?: continue
                    if (uid == currentUser.uid) continue // skip myself
                    
                    val uName = child.child("name").getValue(String::class.java) ?: ""
                    val uUsername = child.child("username").getValue(String::class.java) ?: ""
                    val uAvatarUrl = child.child("avatarUrl").getValue(String::class.java) ?: ""
                    
                    if (uUsername.lowercase().contains(queryClean)) {
                        list.add(UserProfile(uid, uName, uUsername, uAvatarUrl))
                    }
                }
                searchResults = list
            } catch (e: Exception) {
                // error
            }
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val snapshot = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
                    .getReference("users")
                    .child(user.uid)
                    .get()
                    .await()
                
                profileName = snapshot.child("name").getValue(String::class.java) ?: "Name"
                val un = snapshot.child("username").getValue(String::class.java) ?: ""
                profileUsername = if (un.isNotBlank()) "@$un" else user.email ?: "@username"
                profileAvatarUrl = snapshot.child("avatarUrl").getValue(String::class.java) ?: ""
            }
        } catch (e: Exception) {
            // keep defaults
        }
    }

    // Load real chats
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val userChatsRef = database.getReference("user_chats").child(currentUser.uid)
            userChatsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    scope.launch {
                        val chatsList = mutableListOf<ChatItem>()
                        for (child in snapshot.children) {
                            val peerId = child.key ?: continue
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val unreadCount = child.child("unreadCount").getValue(Int::class.java) ?: 0
                            val encryptedLastMessage = child.child("lastMessage").getValue(String::class.java) ?: ""
                            
                            val chatId = if (currentUser.uid < peerId) currentUser.uid + "_" + peerId else peerId + "_" + currentUser.uid
                            val lastMessage = if (encryptedLastMessage.isNotBlank() && !encryptedLastMessage.startsWith("[")) {
                                try { ChatCrypto.decrypt(encryptedLastMessage, chatId) } catch (e: Exception) { encryptedLastMessage }
                            } else {
                                encryptedLastMessage
                            }
                            
                            try {
                                val userSnap = database.getReference("users").child(peerId).get().await()
                                val name = userSnap.child("name").getValue(String::class.java) ?: "User"
                                val avatarUrl = userSnap.child("avatarUrl").getValue(String::class.java) ?: ""
                                // Normally you'd monitor online status from RTDB presence
                                chatsList.add(ChatItem(peerId, name, lastMessage, true, timestamp, avatarUrl, unreadCount))
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                        chatsList.sortByDescending { it.timestamp }
                        chats = chatsList
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
    }

        ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = bgColor,
                drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier.width(310.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 20.dp)) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(surfaceColor, RoundedCornerShape(20.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileAvatarUrl.isNotBlank()) {
                                    com.example.ui.components.AvatarImage(
                                        avatarUrl = profileAvatarUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = dimTextColor, modifier = Modifier.size(32.dp))
                                }
                            }
                            Spacer(Modifier.height(15.dp))
                            Text(profileName, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(profileUsername, color = dimTextColor, fontSize = 12.sp)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(bottom = 10.dp))
                    
                    DrawerMenuItem(icon = Icons.Default.Person, text = s("Мой профиль", "My profile"), textColor = textColor) { 
                        scope.launch { drawerState.close() }
                        onProfileClick() 
                    }
                    androidx.compose.material3.HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    DrawerMenuItem(icon = Icons.Default.Settings, text = s("Настройки", "Settings"), textColor = textColor) { 
                        scope.launch { drawerState.close() }
                        onSettingsClick() 
                    }
                    DrawerMenuItem(icon = Icons.AutoMirrored.Filled.Logout, text = s("Выйти", "Logout"), textColor = textColor) { showLogoutDialog = true }
                }
            }
        }
    ) {
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(s("Выход", "Logout"), color = textColor) },
                text = { Text(s("Вы уверены, что хотите выйти из профиля?", "Are you sure you want to logout?"), color = dimTextColor) },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }) {
                        Text(s("Выйти", "Logout"), color = Color(0xFFE53935))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(s("Отмена", "Cancel"), color = dimTextColor)
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = textColor,
                textContentColor = dimTextColor
            )
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                    }
                    Text("SLANT", color = textColor, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    IconButton(onClick = { 
                        selectedDockTab = 1
                    }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = textColor)
                    }
                }
            },
            containerColor = bgColor
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                
                when (selectedDockTab) {
                    0 -> {
                        // Chats Tab
                        if (chats.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = dimTextColor,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    s("Вы ещё никому не писали.", "You haven't messaged anyone yet."),
                                    color = textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s("Перейдите во вкладку Контакты (вторая иконка) или воспользуйтесь поиском, чтобы найти собеседника.", "Go to the Contacts tab (second icon) or use search to find someone."),
                                    color = dimTextColor,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(chats) { chat ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onChatClick(chat.id) }
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(surfaceColor)
                                                .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (chat.avatarUrl.isNotBlank()) {
                                                com.example.ui.components.AvatarImage(
                                                    avatarUrl = chat.avatarUrl,
                                                    contentDescription = "Аватар",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(chat.name.take(1), color = textColor, fontWeight = FontWeight.Bold)
                                            }
                                            if (chat.isOnline) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(SuccessGreen)
                                                        .border(2.dp, bgColor, CircleShape)
                                                        .align(Alignment.BottomEnd)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(chat.name, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                            Text(chat.lastMessage, color = dimTextColor, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                        if (chat.unreadCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(textColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = chat.unreadCount.toString(),
                                                    color = bgColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Contacts and Search Tab
                        Column(modifier = Modifier.fillMaxSize()) {
                            // High fidelity stylized search text field compliant with M3 guidelines
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(s("Поиск по @username...", "Search by @username..."), color = dimTextColor) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = textColor,
                                    unfocusedBorderColor = borderColor,
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    cursorColor = textColor,
                                    focusedContainerColor = surfaceColor,
                                    unfocusedContainerColor = surfaceColor
                                ),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = dimTextColor) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textColor)
                                        }
                                    }
                                },
                                singleLine = true
                            )

                            Spacer(Modifier.height(12.dp))

                            if (searchQuery.isNotBlank()) {
                                // Search list mode
                                Text(
                                    text = s("РЕЗУЛЬТАТЫ ПОИСКА", "SEARCH RESULTS"),
                                    color = dimTextColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )

                                if (isSearching) {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = textColor, modifier = Modifier.size(24.dp))
                                    }
                                } else if (searchResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(s("Никого не найдено", "No one found"), color = dimTextColor, fontSize = 14.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 100.dp)
                                    ) {
                                        items(searchResults) { user ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onUserClick(user.uid) }
                                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(surfaceColor)
                                                        .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (user.avatarUrl.isNotBlank()) {
                                                        com.example.ui.components.AvatarImage(
                                                            avatarUrl = user.avatarUrl,
                                                            contentDescription = "Аватар",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.Person, contentDescription = null, tint = dimTextColor)
                                                    }
                                                }
                                                Spacer(Modifier.width(16.dp))
                                                Column {
                                                    Text(user.name, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                                    Text("@${user.username}", color = dimTextColor, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Contacts mode
                                Text(
                                    text = s("МОИ КОНТАКТЫ", "MY CONTACTS") + " (${contactsList.size})",
                                    color = dimTextColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )

                                if (isLoadingContacts) {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = textColor, modifier = Modifier.size(24.dp))
                                    }
                                } else if (contactsList.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 80.dp)
                                            .padding(horizontal = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            s("Ваш список контактов пуст", "Your contact list is empty"),
                                            color = textColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            s("Используйте поле ввода выше, чтобы найти пользователей по их юзернейму и добавить в контакты.", "Use the input field above to find users by their username and add them to contacts."),
                                            color = dimTextColor,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 100.dp)
                                    ) {
                                        items(contactsList) { contact ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onUserClick(contact.uid) }
                                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(surfaceColor)
                                                        .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (contact.avatarUrl.isNotBlank()) {
                                                        com.example.ui.components.AvatarImage(
                                                            avatarUrl = contact.avatarUrl,
                                                            contentDescription = "Аватар",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.Person, contentDescription = null, tint = dimTextColor)
                                                    }
                                                }
                                                Spacer(Modifier.width(16.dp))
                                                Column {
                                                    Text(contact.name, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                                    Text("@${contact.username}", color = dimTextColor, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Information/Feature Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = dimTextColor,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                s("Группы и каналы", "Groups and Channels"),
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                s("Данный раздел находится во временной разработке и будет доступен в следующих обновлениях.", "This section is currently under development and will be available in future updates."),
                                color = dimTextColor,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Dock Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isDarkTheme) Color(0xFF0F0F0F) else Color(0xFFEBEBEB))
                        .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val activeIcons = listOf(
                        Icons.AutoMirrored.Filled.Chat,
                        Icons.Default.Person,
                        Icons.Default.Group
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeIcons.forEachIndexed { index, icon ->
                            val isSelected = selectedDockTab == index
                            IconButton(onClick = { selectedDockTab = index }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Tab $index",
                                    tint = if (isSelected) textColor else dimTextColor,
                                    modifier = Modifier.size(if (isSelected) 26.dp else 22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, textColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = text, tint = textColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 14.sp)
    }
}
