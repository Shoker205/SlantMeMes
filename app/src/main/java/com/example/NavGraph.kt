package com.example

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.chat.ChatListScreen
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.screens.profile.ProfileScreen

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val startDest = if (FirebaseAuth.getInstance().currentUser != null) "chat_list" else "auth"

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
                onChatClick = { /* TODO */ },
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
                }
            )
        }
    }
}
