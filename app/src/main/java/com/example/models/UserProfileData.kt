package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileData(
    val uid: String,
    val email: String? = null,
    val name: String,
    val username: String,
    val bio: String,
    val gender: String,
    val birthday: String,
    val avatarUrl: String,
    val online: Boolean = false,
    val lastTimestamp: Long = 0L
)
