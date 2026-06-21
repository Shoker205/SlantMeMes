package com.example

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.models.UserProfileData
import com.example.utils.SupabaseSetup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object PresenceManager {
    private var isAppInForeground = false
    private var lastActivityTime = System.currentTimeMillis()
    private val handler = Handler(Looper.getMainLooper())
    private var isOnlineState = false
    
    private val idleTimeoutMs = 5 * 60 * 1000L // 5 minutes inner idle
    private val backgroundTimeoutMs = 1 * 60 * 1000L // 1 minute background

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkStatus()
            handler.postDelayed(this, 10000) // check every 10 seconds
        }
    }

    fun init() {
        handler.post(checkRunnable)
        markActive()
    }

    fun markActive() {
        lastActivityTime = System.currentTimeMillis()
        if (!isOnlineState) {
            setOnlineStatus(true)
        }
    }

    fun onAppForeground() {
        isAppInForeground = true
        markActive()
    }

    fun onAppBackground() {
        isAppInForeground = false
        // Update activity time, we'll go offline in 1 minute due to checkRunnable
        lastActivityTime = System.currentTimeMillis()
    }

    private fun checkStatus() {
        val now = System.currentTimeMillis()
        val timeSinceLastActivity = now - lastActivityTime

        if (isAppInForeground) {
            if (timeSinceLastActivity >= idleTimeoutMs && isOnlineState) {
                setOnlineStatus(false)
            }
        } else {
            if (timeSinceLastActivity >= backgroundTimeoutMs && isOnlineState) {
                setOnlineStatus(false)
            }
        }
    }

    private fun setOnlineStatus(online: Boolean) {
        val currentUser = SupabaseSetup.client.auth.currentUserOrNull() ?: return
        isOnlineState = online
        
        GlobalScope.launch {
            try {
                if (online) {
                    val userData = mapOf("online" to true)
                    SupabaseSetup.client.postgrest["users"].update(userData) {
                        filter { eq("uid", currentUser.id) }
                    }
                } else {
                    val userData = mapOf("online" to false, "lastTimestamp" to System.currentTimeMillis())
                    SupabaseSetup.client.postgrest["users"].update(userData) {
                        filter { eq("uid", currentUser.id) }
                    }
                }
            } catch (e: Exception) {
                Log.e("PresenceManager", "Failed to update online status", e)
            }
        }
    }
}
