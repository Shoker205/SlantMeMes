package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppPreferences {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_IS_DARK_THEME = "is_dark_theme"
    private const val KEY_LANGUAGE = "app_language" // "Русский" or "English"
    private const val KEY_CHATS_CACHE = "chats_cache"
    private const val KEY_PROFILE_CACHE = "profile_cache"

    private lateinit var prefs: SharedPreferences

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    private val _language = MutableStateFlow("Русский")
    val language: StateFlow<String> = _language

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDarkTheme.value = prefs.getBoolean(KEY_IS_DARK_THEME, true)
        _language.value = prefs.getString(KEY_LANGUAGE, "Русский") ?: "Русский"
    }

    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        prefs.edit().putBoolean(KEY_IS_DARK_THEME, isDark).apply()
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }
    
    fun saveChatsCache(json: String) {
        prefs.edit().putString(KEY_CHATS_CACHE, json).apply()
    }
    
    fun getChatsCache(): String? {
        return prefs.getString(KEY_CHATS_CACHE, null)
    }
    
    fun saveProfileCache(json: String) {
        prefs.edit().putString(KEY_PROFILE_CACHE, json).apply()
    }
    
    fun getProfileCache(): String? {
        return prefs.getString(KEY_PROFILE_CACHE, null)
    }
}
