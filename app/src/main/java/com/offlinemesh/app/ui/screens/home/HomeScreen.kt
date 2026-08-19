package com.offlinemesh.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.ui.components.AvatarBadge
import com.offlinemesh.app.ui.components.DeliveryStatusIcon
import com.offlinemesh.app.ui.components.EmptyStateView
import com.offlinemesh.app.ui.components.OFCBadge
import com.offlinemesh.app.ui.components.PermissionHelper
import com.offlinemesh.app.ui.components.PermissionRequesterCard
import com.offlinemesh.app.ui.screens.communities.CommunitiesScreen
import com.offlinemesh.app.ui.screens.nearby.NearbyDevicesScreen
import com.offlinemesh.app.ui.screens.settings.SettingsScreen
import com.offlinemesh.app.ui.theme.MeshCyan
import com.offlinemesh.app.ui.theme.MeshEmerald
import com.offlinemesh.app.ui.viewmodel.HomeViewModel
import com.offlinemesh.app.ui.viewmodel.NearbyViewModel
import com.offlinemesh.app.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.offlinemesh.app.ui.components.GrayHeaderBrand

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    nearbyViewModel: NearbyViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToChat: (conversationId: String, peerName: String, avatarColor: String?) -> Unit,
    onNavigateToWelcome: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val identity by homeViewModel.userIdentity.collectAsState()
    val connectedPeers by homeViewModel.connectedPeers.collectAsState()
    val discoveredPeers by homeViewModel.discoveredPeers.collectAsState()

    // Auto-start discovery and advertising if permissions are granted
    LaunchedEffect(Unit) {
        if (PermissionHelper.hasAllPermissions(context)) {
            nearbyViewModel.startScanning()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshCyan,
                        indicatorColor = MeshCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Box {
                            Icon(Icons.Default.Radar, contentDescription = "Nearby")
                            val totalNearby = connectedPeers.size + discoveredPeers.size
                            if (totalNearby > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(MeshCyan)
                                )
                            }
                        }
                    },
                    label = { Text("Nearby") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshCyan,
                        indicatorColor = MeshCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Communities") },
                    label = { Text("Communities") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshCyan,
                        indicatorColor = MeshCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshCyan,
                        indicatorColor = MeshCyan.copy(alpha = 0.15f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { selectedTab = 1 },
                    containerColor = MeshCyan,
                    contentColor = Color.Black,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.WifiTethering, contentDescription = "Find Nearby")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ChatsTabContent(
                    homeViewModel = homeViewModel,
                    nearbyViewModel = nearbyViewModel,
                    onNavigateToChat = onNavigateToChat,
                    onOpenRadar = { selectedTab = 1 }
                )
                1 -> NearbyDevicesScreen(
                    nearbyViewModel = nearbyViewModel,
                    onNavigateToChat = onNavigateToChat
                )
                2 -> CommunitiesScreen(
                    homeViewModel = homeViewModel,
                    onNavigateToChat = onNavigateToChat
                )
                3 -> SettingsScreen(
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
private fun ChatsTabContent(
    homeViewModel: HomeViewModel,
    nearbyViewModel: NearbyViewModel,
    onNavigateToChat: (conversationId: String, peerName: String, avatarColor: String?) -> Unit,
    onOpenRadar: () -> Unit
) {
    val identity by homeViewModel.userIdentity.collectAsState()
    val recentConversations by homeViewModel.recentConversations.collectAsState()
    val connectedPeers by homeViewModel.connectedPeers.collectAsState()
    val discoveredPeers by homeViewModel.discoveredPeers.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App Header & User Identity Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GrayHeaderBrand(
                        logoSize = 40.dp,
                        subtitle = "Zero-Internet P2P Radio Mesh"
                    )

                    // Live Nearby Active Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (connectedPeers.isNotEmpty()) MeshEmerald.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onOpenRadar() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (connectedPeers.isNotEmpty()) MeshEmerald
                                        else Color.Gray
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${connectedPeers.size} Connected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (connectedPeers.isNotEmpty()) MeshEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Identity Pill Card
                identity?.let { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarBadge(
                                name = user.displayName,
                                avatarColorHex = user.avatarColorHex,
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                OFCBadge(userId = user.userId, enableCopy = true)
                            }
                        }
                    }
                }
            }
        }

        // Permission Card directly on home if permissions are needed
        PermissionRequesterCard(
            onPermissionsGranted = { nearbyViewModel.startScanning() }
        )

        // Conversation List
        if (recentConversations.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.ChatBubbleOutline,
                title = "No Conversations Yet",
                subtitle = "Discover and connect with nearby devices over radio to start messaging offline.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = recentConversations,
                    key = { it.conversationId }
                ) { message ->
                    val isOnline = connectedPeers.any { it.userId == message.conversationId }
                    ConversationRow(
                        message = message,
                        isPeerOnline = isOnline,
                        onClick = {
                            val peerDisplayName = if (message.isOutgoing) message.recipientId else message.senderName
                            // If discovered but not connected, trigger connect
                            val discoveredPeer = discoveredPeers.find { it.userId == message.conversationId }
                            if (discoveredPeer != null && !isOnline) {
                                nearbyViewModel.connectPeer(discoveredPeer.endpointId)
                            }
                            onNavigateToChat(message.conversationId, peerDisplayName, null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    message: MessageEntity,
    isPeerOnline: Boolean,
    onClick: () -> Unit
) {
    val peerDisplayName = if (message.isOutgoing) message.recipientId else message.senderName
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarBadge(
            name = peerDisplayName,
            avatarColorHex = null,
            size = 50.dp,
            isOnline = isPeerOnline
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = peerDisplayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.isOutgoing) {
                    DeliveryStatusIcon(status = message.status)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
