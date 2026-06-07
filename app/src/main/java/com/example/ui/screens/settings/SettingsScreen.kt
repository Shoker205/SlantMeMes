package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var isDarkTheme by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("Русский") }
    
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
        }
    }
}
