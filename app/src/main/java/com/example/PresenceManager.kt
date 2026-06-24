package com.example

import android.util.Log
import com.example.utils.SupabaseSetup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*

object PresenceManager {
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init() {
        // Initially called, wait for foreground event
    }

    fun markActive() {
        // No-op for compatibility
    }

    fun onAppForeground() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                updateStatus(true)
                delay(30_000) // Heartbeat every 30 seconds
            }
        }
    }

    fun onAppBackground() {
        heartbeatJob?.cancel()
        scope.launch {
            updateStatus(false)
        }
    }

    private suspend fun updateStatus(online: Boolean) {
        try {
            val user = SupabaseSetup.client.auth.currentUserOrNull() ?: return
            val data = mapOf(
                "online" to online,
                "lastTimestamp" to System.currentTimeMillis()
            )
            SupabaseSetup.client.postgrest["users"].update(data) {
                filter { eq("uid", user.id) }
            }
        } catch (e: Exception) {
            Log.e("PresenceManager", "Failed to update presence", e)
        }
    }
}
