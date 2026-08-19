package com.offlinemesh.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offlinemesh.app.di.AppContainer
import com.offlinemesh.app.ui.screens.chat.ChatDetailScreen
import com.offlinemesh.app.ui.screens.home.HomeScreen
import com.offlinemesh.app.ui.viewmodel.ChatViewModel
import com.offlinemesh.app.ui.viewmodel.HomeViewModel
import com.offlinemesh.app.ui.viewmodel.NearbyViewModel
import com.offlinemesh.app.ui.viewmodel.SettingsViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import com.offlinemesh.app.ui.screens.splash.SplashScreen
import com.offlinemesh.app.ui.screens.welcome.WelcomeScreen

@Composable
fun AppNavigation(
    container: AppContainer
) {
    val navController = rememberNavController()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            getOrCreateIdentityUseCase = container.getOrCreateIdentityUseCase,
            observeRecentConversationsUseCase = container.observeRecentConversationsUseCase,
            observeDiscoveredPeersUseCase = container.observeDiscoveredPeersUseCase,
            observeConnectedPeersUseCase = container.observeConnectedPeersUseCase,
            communityRepository = container.communityRepository
        )
    )

    val nearbyViewModel: NearbyViewModel = viewModel(
        factory = NearbyViewModel.Factory(
            getOrCreateIdentityUseCase = container.getOrCreateIdentityUseCase,
            observeDiscoveredPeersUseCase = container.observeDiscoveredPeersUseCase,
            observeConnectedPeersUseCase = container.observeConnectedPeersUseCase,
            observeKnownPeersUseCase = container.observeKnownPeersUseCase,
            connectPeerUseCase = container.connectPeerUseCase,
            disconnectPeerUseCase = container.disconnectPeerUseCase,
            startDiscoveryUseCase = container.startDiscoveryUseCase,
            stopDiscoveryUseCase = container.stopDiscoveryUseCase
        )
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            getOrCreateIdentityUseCase = container.getOrCreateIdentityUseCase,
            updateProfileUseCase = container.updateProfileUseCase
        )
    )

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                settingsViewModel = settingsViewModel,
                onEnterApp = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                homeViewModel = homeViewModel,
                nearbyViewModel = nearbyViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToChat = { conversationId, peerName, avatarColor ->
                    navController.navigate(
                        Screen.ChatDetail.createRoute(conversationId, peerName, avatarColor)
                    )
                },
                onNavigateToWelcome = {
                    navController.navigate(Screen.Welcome.route)
                }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType },
                navArgument("avatarColor") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawConversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val rawPeerName = backStackEntry.arguments?.getString("peerName") ?: "Peer"
            val rawAvatarColor = backStackEntry.arguments?.getString("avatarColor") ?: "#3B82F6"

            val conversationId = URLDecoder.decode(rawConversationId, StandardCharsets.UTF_8.toString())
            val peerName = URLDecoder.decode(rawPeerName, StandardCharsets.UTF_8.toString())
            val avatarColor = URLDecoder.decode(rawAvatarColor, StandardCharsets.UTF_8.toString())

            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    conversationId = conversationId,
                    peerName = peerName,
                    peerAvatarColor = avatarColor,
                    getOrCreateIdentityUseCase = container.getOrCreateIdentityUseCase,
                    observeMessagesUseCase = container.observeMessagesUseCase,
                    sendMessageUseCase = container.sendMessageUseCase,
                    retrySendMessageUseCase = container.retrySendMessageUseCase,
                    observeConnectedPeersUseCase = container.observeConnectedPeersUseCase,
                    observeDiscoveredPeersUseCase = container.observeDiscoveredPeersUseCase,
                    connectPeerUseCase = container.connectPeerUseCase
                )
            )

            ChatDetailScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
