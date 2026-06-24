package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class PreviewData(val title: String, val description: String, val imageUrl: String)

@Composable
fun LinkPreview(url: String, isDarkTheme: Boolean, onHide: () -> Unit) {
    var previewData by remember { mutableStateOf<PreviewData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    
    val bgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF0F0F0)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dimColor = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF666666)

    LaunchedEffect(url) {
        try {
            withContext(Dispatchers.IO) {
                var urlToFetch = url
                if (!urlToFetch.startsWith("http://") && !urlToFetch.startsWith("https://")) {
                    urlToFetch = "https://$url"
                }
                val doc = Jsoup.connect(urlToFetch).timeout(3000).get()
                val title = doc.select("meta[property=og:title]").attr("content").ifEmpty { doc.title() }
                val desc = doc.select("meta[property=og:description]").attr("content").ifEmpty { doc.select("meta[name=description]").attr("content") }
                val image = doc.select("meta[property=og:image]").attr("content")
                
                if (title.isNotBlank()) {
                    previewData = PreviewData(title, desc, image)
                } else {
                    isFailed = true
                }
            }
        } catch (e: Exception) {
            isFailed = true
        } finally {
            isLoading = false
        }
    }

    if (!isLoading && !isFailed && previewData != null) {
        val data = previewData!!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .clickable { 
                    try { 
                        var urlToOpen = url
                        if (!urlToOpen.startsWith("http://") && !urlToOpen.startsWith("https://")) {
                            urlToOpen = "https://$url"
                        }
                        uriHandler.openUri(urlToOpen) 
                    } catch(e:Exception){} 
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (data.imageUrl.isNotBlank()) {
                Box(modifier = Modifier.width(2.dp).height(40.dp).background(Color(0xFF64B5F6)))
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = data.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Box(modifier = Modifier.width(2.dp).height(40.dp).background(Color(0xFF64B5F6)))
                Spacer(Modifier.width(8.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(data.title, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (data.description.isNotBlank()) {
                    Text(data.description, color = textColor, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(url, color = dimColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            
            IconButton(onClick = onHide, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = dimColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}
