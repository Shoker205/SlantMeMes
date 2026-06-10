package com.example

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

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
        
        val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
        val amIOnlineRef = database.getReference(".info/connected")
        amIOnlineRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val userStatusRef = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                        database.getReference("users").child(uid)
                    }
                    userStatusRef?.onDisconnect()?.updateChildren(
                        mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
                    )
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
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
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
        val userStatusRef = database.getReference("users").child(currentUser.uid)
        
        isOnlineState = online
        val map = if (online) {
            mapOf("online" to true)
        } else {
            mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
        }
        userStatusRef.updateChildren(map).addOnFailureListener {
            Log.e("PresenceManager", "Failed to update online status", it)
        }
    }
}
