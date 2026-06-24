package com.example

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.chat.ChatListScreen
import com.example.utils.SupabaseSetup
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import com.example.ui.screens.profile.ProfileScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.app.Activity

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val authUser = SupabaseSetup.client.auth.currentUserOrNull()
    val startDest = if (authUser != null) "chat_list" else "auth"

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(activity?.intent) {
        val chatId = activity?.intent?.getStringExtra("chatId")
        if (chatId != null && authUser != null) {
            navController.navigate("chat/$chatId")
            activity.intent?.removeExtra("chatId")
        }
    }

    LaunchedEffect(authUser?.id) {
        if (authUser != null) {
            PresenceManager.init()
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable("auth") {
            AuthScreen(onAuthSuccess = {
                navController.navigate("chat_list") {
                    popUpTo("auth") { inclusive = true }
                }
            })
        }
        composable("chat_list") {
            ChatListScreen(
                onOpenDrawer = { /* TODO */ },
                onChatClick = { userId -> 
                    navController.navigate("chat/$userId")
                },
                onLogout = {
                    GlobalScope.launch {
                        try {
                            SupabaseSetup.client.auth.signOut()
                        } catch (e: Exception) {}
                    }
                    navController.navigate("auth") {
                        popUpTo("chat_list") { inclusive = true }
                    }
                },
                onProfileClick = {
                    navController.navigate("profile")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onUserClick = { userId ->
                    navController.navigate("other_profile/$userId")
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("settings") {
            com.example.ui.screens.settings.SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("other_profile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.example.ui.screens.profile.OtherProfileScreen(
                userId = userId,
                onBack = {
                    navController.popBackStack()
                },
                onChatClick = { uid ->
                    navController.navigate("chat/$uid") {
                        popUpTo("other_profile/{userId}") { inclusive = true }
                    }
                }
            )
        }
        composable("chat/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.example.ui.screens.chat.ChatScreen(
                recipientId = userId,
                onBack = {
                    navController.popBackStack("chat_list", inclusive = false)
                },
                onProfileClick = {
                    navController.navigate("other_profile/$userId")
                },
                onUserMentionClick = { mentionedUserId ->
                    val currentUser = com.example.utils.SupabaseSetup.client.auth.currentUserOrNull()
                    if (currentUser != null && currentUser.id == mentionedUserId) {
                        navController.navigate("profile")
                    } else {
                        navController.navigate("other_profile/$mentionedUserId")
                    }
                }
            )
        }
    }
}
