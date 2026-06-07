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
import androidx.compose.ui.graphics.Color
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
    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState()
    val selectedLanguage by com.example.AppPreferences.language.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) Black else Color(0xFFF5F5F7)
    val surfaceColor = if (isDarkTheme) DarkSurface else White
    val borderColor = if (isDarkTheme) LightSurface else Color(0xFFE0E0E0)
    val textColor = if (isDarkTheme) White else Black
    val dimTextColor = if (isDarkTheme) DimText else Color(0xFF666666)

    val s: (String, String) -> String = { ru, en -> if (selectedLanguage == "English") en else ru }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s("Настройки", "Settings"), color = textColor, fontWeight = FontWeight.Bold) },
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
                    Text(s("Темная тема", "Dark Theme"), color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(s("Использовать темную тему оформления", "Use dark visual theme"), color = dimTextColor, fontSize = 12.sp)
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { com.example.AppPreferences.setDarkTheme(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = bgColor,
                        checkedTrackColor = textColor,
                        uncheckedThumbColor = dimTextColor,
                        uncheckedTrackColor = surfaceColor
                    )
                )
            }
            
            HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            
            // Language selector
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(s("Язык", "Language"), color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                            focusedBorderColor = dimTextColor,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedContainerColor = bgColor,
                            unfocusedContainerColor = bgColor
                        ),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = surfaceColor
                    ) {
                        DropdownMenuItem(
                            text = { Text("Русский", color = textColor) },
                            onClick = { com.example.AppPreferences.setLanguage("Русский"); expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("English", color = textColor) },
                            onClick = { com.example.AppPreferences.setLanguage("English"); expanded = false }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(s("Изменение языка приложения", "Change application language"), color = dimTextColor, fontSize = 12.sp)
            }
            
            HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            
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
                    Text(s("О приложении", "About Application"), color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(s("Версия, разработчик и другая информация", "Version, developer and other info"), color = dimTextColor, fontSize = 12.sp)
                }
                Icon(Icons.Default.Info, contentDescription = "Инфо", tint = dimTextColor)
            }
        }
    }

    if (showAboutDialog) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = surfaceColor,
            titleContentColor = textColor,
            textContentColor = textColor,
            title = {
                Text(
                    text = s("О приложении", "About Application"),
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
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Иконка приложения",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Slant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = s("Версия: 1.0.0", "Version: 1.0.0"),
                        fontSize = 14.sp,
                        color = dimTextColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://t.me/slant_tech")
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s("Разработчик: ", "Developer: "),
                            fontSize = 14.sp,
                            color = dimTextColor
                        )
                        Text(
                            text = "SlantTech",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "© 2026",
                        fontSize = 14.sp,
                        color = dimTextColor
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(s("Закрыть", "Close"), color = textColor)
                }
            }
        )
    }
}
