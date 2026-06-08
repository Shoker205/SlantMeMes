package com.example

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.chat.ChatListScreen
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.screens.profile.ProfileScreen
import androidx.compose.runtime.LaunchedEffect

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val authUser = FirebaseAuth.getInstance().currentUser
    val startDest = if (authUser != null) "chat_list" else "auth"

    LaunchedEffect(authUser?.uid) {
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
                    FirebaseAuth.getInstance().signOut()
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
                }
            )
        }
    }
}
