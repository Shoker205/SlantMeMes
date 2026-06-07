package com.example.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var isDarkTheme by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("Русский") }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = White, fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            
            // Theme toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Темная тема", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Использовать темную тему оформления", color = DimText, fontSize = 12.sp)
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { isDarkTheme = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Black,
                        checkedTrackColor = White,
                        uncheckedThumbColor = DimText,
                        uncheckedTrackColor = DarkSurface
                    )
                )
            }
            
            HorizontalDivider(color = LightSurface, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            
            // Language selector
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Язык", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DimText,
                            unfocusedBorderColor = LightSurface,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = DarkSurface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Русский", color = White) },
                            onClick = { selectedLanguage = "Русский"; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("English", color = White) },
                            onClick = { selectedLanguage = "English"; expanded = false }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Изменение языка приложения", color = DimText, fontSize = 12.sp)
            }
            
            HorizontalDivider(color = LightSurface, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            
            // About App
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("О приложении", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Версия, разработчик и другая информация", color = DimText, fontSize = 12.sp)
                }
                Icon(Icons.Default.Info, contentDescription = "Инфо", tint = DimText)
            }
        }
    }

    if (showAboutDialog) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkSurface,
            titleContentColor = White,
            textContentColor = White,
            title = {
                Text(
                    text = "О приложении",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "Иконка приложения",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Slant 2",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Версия: 1.0.0",
                        fontSize = 14.sp,
                        color = DimText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://t.me/slant_tech")
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Разработчик: ",
                            fontSize = 14.sp,
                            color = DimText
                        )
                        Text(
                            text = "SlantTech",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "© 2026",
                        fontSize = 14.sp,
                        color = DimText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Закрыть", color = White)
                }
            }
        )
    }
}
