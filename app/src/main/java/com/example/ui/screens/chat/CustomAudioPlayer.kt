package com.example.ui.screens.chat

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CustomAudioPlayer(
    audioMessages: List<ChatMessage>,
    initialIndex: Int,
    getSenderName: (String) -> String,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(1) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var loopMode by remember { mutableIntStateOf(0) } // 0=none, 1=all, 2=one
    var shuffle by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val isDarkTheme by com.example.AppPreferences.isDarkTheme.collectAsState(initial = true)
    val bgColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF5F5F5)
    val surfaceColor = if (isDarkTheme) Color(0xFF2C2C2C) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dimTextColor = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF666666)

    val mediaPlayer = remember { MediaPlayer() }
    
    val currentMsg = audioMessages.getOrNull(currentIndex)
    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var songCover by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(currentIndex, currentMsg) {
        if (currentMsg == null) return@LaunchedEffect
        val resolvedUrl = com.example.utils.WebRtcDataChannel.getLocalFileUri(ctx, currentMsg.mediaUrl)
        
        songTitle = currentMsg.text.ifBlank { "Аудио" }
        songArtist = getSenderName(currentMsg.senderId)
        songCover = null
        
        if (!resolvedUrl.startsWith("webrtc://")) {
            try {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(ctx, android.net.Uri.parse(resolvedUrl))
                val extTitle = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val extArtist = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val extCoverBytes = mmr.embeddedPicture
                if (extTitle != null) songTitle = extTitle
                if (extArtist != null) songArtist = extArtist
                if (extCoverBytes != null) {
                    songCover = android.graphics.BitmapFactory.decodeByteArray(extCoverBytes, 0, extCoverBytes.size)
                }
                mmr.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(ctx, android.net.Uri.parse(resolvedUrl))
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                duration = it.duration
                if (isPlaying) it.start()
            }
            mediaPlayer.setOnCompletionListener {
                if (loopMode == 2) {
                    it.seekTo(0)
                    it.start()
                } else {
                    if (shuffle) {
                        currentIndex = audioMessages.indices.random()
                    } else if (currentIndex < audioMessages.size - 1) {
                        currentIndex++
                    } else if (loopMode == 1) {
                        currentIndex = 0
                    } else {
                        isPlaying = false
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) mediaPlayer.start() else mediaPlayer.pause()
        while (isActive && isPlaying) {
            currentPosition = mediaPlayer.currentPosition
            progress = currentPosition.toFloat() / duration.toFloat()
            delay(500)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Click outside to dismiss
            Box(modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null
            ) { onDismiss() })
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isExpanded) 1f else 0.33f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(bgColor)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null
                    ) { } // prevent clicks passing through
            ) {
                // Drag handle / Expand toggle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(dimTextColor.copy(alpha = 0.5f)))
                }

                if (!isExpanded) {
                    // COMPACT LAYOUT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // cover
                        Box(
                            modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(surfaceColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (songCover != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = songCover!!.asImageBitmap(),
                                    contentDescription = "Cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = dimTextColor)
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // title & progress
                        Column(modifier = Modifier.weight(1f)) {
                            Text(songTitle, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(songArtist, color = dimTextColor, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(dimTextColor.copy(alpha=0.3f))) {
                                val prog = if(progress.isNaN()) 0f else progress
                                drawRect(color = textColor, size = androidx.compose.ui.geometry.Size(size.width * prog, size.height))
                            }
                        }

                        Spacer(Modifier.width(12.dp))
                        
                        // Play/Pause button
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(textColor).clickable { isPlaying = !isPlaying },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = bgColor)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    // FULLSCREEN LAYOUT
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp).height(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isExpanded = false }) { // Minimize instead of dismiss entirely
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Minimize", tint = textColor)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { 
                                if (currentMsg != null) MediaTools.downloadMedia(ctx, currentMsg.mediaUrl, "audio")
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = textColor)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Album Art
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .aspectRatio(1f)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                                .background(surfaceColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (songCover != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = songCover!!.asImageBitmap(),
                                    contentDescription = "Cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = dimTextColor, modifier = Modifier.size(120.dp))
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        // Title and Author
                        Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                            Text(songTitle, color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(songArtist, color = dimTextColor, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }

                        Spacer(Modifier.weight(1f))

                        // Seek bar
                        Slider(
                            value = if(progress.isNaN()) 0f else progress,
                            onValueChange = { 
                                progress = it
                                mediaPlayer.seekTo((it * duration).toInt())
                            },
                            modifier = Modifier.padding(horizontal = 24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = surfaceColor
                            )
                        )

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            val mm = currentPosition / 1000 / 60
                            val ss = (currentPosition / 1000) % 60
                            val dm = duration / 1000 / 60
                            val ds = (duration / 1000) % 60
                            Text(String.format(java.util.Locale.US, "%02d:%02d", mm, ss), color = dimTextColor, fontSize = 12.sp)
                            Text(String.format(java.util.Locale.US, "%02d:%02d", dm, ds), color = dimTextColor, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(16.dp))

                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { shuffle = !shuffle }) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) textColor else dimTextColor)
                            }
                            IconButton(onClick = { 
                                if (currentIndex > 0) currentIndex-- 
                            }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = textColor, modifier = Modifier.size(36.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(textColor)
                                    .clickable { isPlaying = !isPlaying },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = bgColor, modifier = Modifier.size(36.dp))
                            }
                            IconButton(onClick = { 
                                if (currentIndex < audioMessages.size - 1) currentIndex++ 
                            }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = textColor, modifier = Modifier.size(36.dp))
                            }
                            IconButton(onClick = { loopMode = (loopMode + 1) % 3 }) {
                                val icon = if (loopMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
                                val tint = if (loopMode > 0) textColor else dimTextColor
                                Icon(icon, contentDescription = "Repeat", tint = tint)
                            }
                        }
                    }
                }
            }
        }
    }
}
