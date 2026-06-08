package com.example

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

object PresenceManager {
    fun init() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance("https://slantmes-64dbf-default-rtdb.europe-west1.firebasedatabase.app/")
        val amIOnlineRef = database.getReference(".info/connected")
        val userStatusRef = database.getReference("users").child(currentUser.uid)

        amIOnlineRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val statusMap = mapOf(
                        "online" to false,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    userStatusRef.onDisconnect().updateChildren(statusMap)

                    val onlineMap = mapOf(
                        "online" to true,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    userStatusRef.updateChildren(onlineMap)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
}
