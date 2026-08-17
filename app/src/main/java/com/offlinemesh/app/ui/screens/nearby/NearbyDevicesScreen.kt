package com.offlinemesh.app.ui.screens.nearby

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemesh.app.core.model.PeerConnectionState
import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.ui.components.AvatarBadge
import com.offlinemesh.app.ui.components.EmptyStateView
import com.offlinemesh.app.ui.components.OFCBadge
import com.offlinemesh.app.ui.components.PermissionRequesterCard
import com.offlinemesh.app.ui.theme.MeshCyan
import com.offlinemesh.app.ui.theme.MeshEmerald
import com.offlinemesh.app.ui.theme.MeshIndigo
import com.offlinemesh.app.ui.theme.MeshRose
import com.offlinemesh.app.ui.viewmodel.NearbyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NearbyDevicesScreen(
    nearbyViewModel: NearbyViewModel,
    onNavigateToChat: (conversationId: String, peerName: String, avatarColor: String?) -> Unit
) {
    val discoveredPeers by nearbyViewModel.discoveredPeers.collectAsState()
    val connectedPeers by nearbyViewModel.connectedPeers.collectAsState()
    val knownPeers by nearbyViewModel.knownPeers.collectAsState()
    val isScanning by nearbyViewModel.isScanning.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Permission Request Banner (if not yet granted)
        item {
            PermissionRequesterCard(
                onPermissionsGranted = { nearbyViewModel.startScanning() }
            )
        }

        // Radar Visual Header
        item {
            RadarHeader(
                isScanning = isScanning,
                totalDiscovered = discoveredPeers.size + connectedPeers.size,
                onToggleScan = { nearbyViewModel.toggleScanning() }
            )
        }

        // Connected Peers Section
        if (connectedPeers.isNotEmpty()) {
            item {
                SectionHeader(title = "Connected Active Radio Links (${connectedPeers.size})")
            }
            items(
                items = connectedPeers,
                key = { it.endpointId }
            ) { peer ->
                ConnectedPeerCard(
                    peer = peer,
                    onChat = {
                        val conversationId = peer.userId ?: peer.endpointId
                        onNavigateToChat(conversationId, peer.displayName, peer.avatarColorHex)
                    },
                    onDisconnect = { nearbyViewModel.disconnectPeer(peer.endpointId) }
                )
            }
        }

        // Discovered Nearby Peers
        val unconnectedDiscovered = discoveredPeers.filter { discovered ->
            connectedPeers.none { it.endpointId == discovered.endpointId }
        }

        if (unconnectedDiscovered.isNotEmpty()) {
            item {
                SectionHeader(title = "Discovered Nearby Devices (${unconnectedDiscovered.size})")
            }
            items(
                items = unconnectedDiscovered,
                key = { it.endpointId }
            ) { peer ->
                DiscoveredPeerCard(
                    peer = peer,
                    onConnect = { nearbyViewModel.connectPeer(peer.endpointId) }
                )
            }
        } else if (connectedPeers.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.Radar,
                    title = if (isScanning) "Searching for Nearby Peers..." else "Radar Idle",
                    subtitle = if (isScanning) "Keep Bluetooth & Wi-Fi enabled on both devices to establish offline radio connection." else "Tap 'Start Radar Scan' above to search for nearby compatible devices.",
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        // Known / Historical Contacts
        if (knownPeers.isNotEmpty()) {
            item {
                SectionHeader(title = "Known Contacts (${knownPeers.size})")
            }
            items(
                items = knownPeers,
                key = { it.userId }
            ) { contact ->
                val isCurrentlyConnected = connectedPeers.any { it.userId == contact.userId }
                KnownContactCard(
                    contact = contact,
                    isConnected = isCurrentlyConnected,
                    onClick = {
                        onNavigateToChat(contact.userId, contact.displayName, contact.avatarColorHex)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RadarHeader(
    isScanning: Boolean,
    totalDiscovered: Int,
    onToggleScan: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MeshCyan.copy(alpha = 0.15f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (isScanning) MeshCyan.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = 2.dp,
                            color = if (isScanning) MeshCyan else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar",
                        tint = if (isScanning) MeshCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isScanning) "Nearby Radar Active" else "Nearby Radar Inactive",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isScanning) "$totalDiscovered device(s) found in radio range" else "Start radar to discover and connect without internet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleScan,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MeshRose else MeshCyan,
                    contentColor = if (isScanning) Color.White else Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.LinkOff else Icons.Default.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "Stop Radar Scan" else "Start Radar Scan",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun ConnectedPeerCard(
    peer: PeerDevice,
    onChat: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarBadge(
                name = peer.displayName,
                avatarColorHex = peer.avatarColorHex,
                size = 46.dp,
                isOnline = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (peer.userId != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    OFCBadge(userId = peer.userId, enableCopy = true)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row {
                Button(
                    onClick = onChat,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeshEmerald,
                        contentColor = Color.Black
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect", tint = MeshRose, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredPeerCard(
    peer: PeerDevice,
    onConnect: () -> Unit
) {
    val isConnecting = peer.connectionState == PeerConnectionState.CONNECTING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarBadge(
                name = peer.displayName,
                avatarColorHex = null,
                size = 44.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Endpoint: ${peer.endpointId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onConnect,
                enabled = !isConnecting,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshCyan,
                    contentColor = Color.Black
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Link, contentDescription = "Connect", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun KnownContactCard(
    contact: PeerEntity,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedLastSeen = remember(contact.lastSeen) { timeFormat.format(Date(contact.lastSeen)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarBadge(
                name = contact.displayName,
                avatarColorHex = contact.avatarColorHex,
                size = 40.dp,
                isOnline = isConnected
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OFCBadge(userId = contact.userId, enableCopy = false)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isConnected) "Connected" else "Last seen",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) MeshEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isConnected) {
                    Text(
                        text = formattedLastSeen,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
