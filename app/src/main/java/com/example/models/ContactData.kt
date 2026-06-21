package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ContactData(
    val user_id: String,
    val contact_id: String
)
