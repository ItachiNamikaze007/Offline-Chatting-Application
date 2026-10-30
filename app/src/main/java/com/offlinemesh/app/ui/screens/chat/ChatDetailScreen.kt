package com.offlinemesh.app.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.ui.components.AvatarBadge
import com.offlinemesh.app.ui.components.DeliveryStatusIcon
import com.offlinemesh.app.ui.components.EmptyStateView
import com.offlinemesh.app.ui.theme.MeshCyan
import com.offlinemesh.app.ui.theme.MeshEmerald
import com.offlinemesh.app.ui.theme.MeshIndigo
import com.offlinemesh.app.ui.theme.MeshRose
import com.offlinemesh.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.DisposableEffect
import com.offlinemesh.app.core.notification.ActiveConversationTracker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val peerEntity by viewModel.peerEntity.collectAsState()
    val isConnected by viewModel.isPeerConnected.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(viewModel.conversationId) {
        ActiveConversationTracker.setActiveConversation(viewModel.conversationId)
        onDispose {
            ActiveConversationTracker.setActiveConversation(null)
        }
    }

    val listState = rememberLazyListState()
    val isAtBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItemIndex >= totalItems - 2
            }
        }
    }

    var selectedMessageForMenu by remember { mutableStateOf<MessageEntity?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showDeleteConversationDialog by remember { mutableStateOf(false) }

    val hopCount = remember(isConnected) { viewModel.getMeshHopDistance() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            viewModel.markAsRead()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarBadge(
                            name = viewModel.peerName,
                            avatarColorHex = viewModel.peerAvatarColor,
                            imagePath = peerEntity?.avatarImagePath,
                            size = 38.dp,
                            isOnline = isConnected
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = viewModel.peerName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when {
                                    isConnected -> MeshEmerald
                                    hopCount != null && hopCount > 1 -> MeshCyan
                                    else -> Color.Gray
                                }
                                val statusText = when {
                                    isConnected -> "Direct Radio Connected"
                                    hopCount != null && hopCount > 1 -> "Mesh Reachable ($hopCount hops)"
                                    else -> "Offline / Queued"
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTopMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy OFC ID") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OFC ID", viewModel.conversationId))
                                scope.launch { snackbarHostState.showSnackbar("OFC ID copied to clipboard") }
                                showTopMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Contact ID") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "OfflineMesh Contact")
                                    putExtra(Intent.EXTRA_TEXT, "OfflineMesh Contact: ${viewModel.peerName} (${viewModel.conversationId})")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Contact ID"))
                                showTopMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Conversation", color = MeshRose) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MeshRose) },
                            onClick = {
                                showTopMenu = false
                                showDeleteConversationDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            // Messages List Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = "Start Offline Conversation",
                        subtitle = "Send an end-to-end encrypted message to ${viewModel.peerName}. Messages traverse nearby mesh radios without internet.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.messageId }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                isHighlighted = message.messageId == highlightedMessageId,
                                peerAvatarImagePath = peerEntity?.avatarImagePath,
                                onLongClick = { selectedMessageForMenu = message },
                                onRetry = { viewModel.retryMessage(message.messageId) },
                                onQuotedClick = { targetMsgId ->
                                    val index = messages.indexOfFirst { it.messageId == targetMsgId }
                                    if (index >= 0) {
                                        scope.launch {
                                            listState.animateScrollToItem(index)
                                            highlightedMessageId = targetMsgId
                                            delay(1500)
                                            if (highlightedMessageId == targetMsgId) {
                                                highlightedMessageId = null
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Original message unavailable.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Jump to Bottom Floating Button
                if (!isAtBottom && messages.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(40.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Compact Quoted Reply Preview Bar
            replyingTo?.let { replyTarget ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MeshCyan)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MeshCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyTarget.senderName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MeshCyan
                            )
                            Text(
                                text = replyTarget.content,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setReplyingTo(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Message Composer Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = {
                            Text(
                                text = "Message via Offline Radio...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeshCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )

                    val isSendEnabled = inputText.trim().isNotEmpty()

                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = isSendEnabled,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSendEnabled) MeshCyan
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (isSendEnabled) Color.Black else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Message Context Bottom Sheet on Long Press
    selectedMessageForMenu?.let { message ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForMenu = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Reply
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setReplyingTo(message)
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MeshCyan)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Reply", style = MaterialTheme.typography.bodyLarge)
                }

                // Copy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Message", message.content))
                            scope.launch { snackbarHostState.showSnackbar("Message copied") }
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MeshCyan)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Copy Text", style = MaterialTheme.typography.bodyLarge)
                }

                // Star / Unstar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleStar(message)
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = MeshCyan
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (message.isStarred) "Unstar Message" else "Star Message", style = MaterialTheme.typography.bodyLarge)
                }

                // Retry (if failed)
                if (message.isOutgoing && message.status == DeliveryStatus.FAILED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.retryMessage(message.messageId)
                                selectedMessageForMenu = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = MeshEmerald)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Retry Send", style = MaterialTheme.typography.bodyLarge, color = MeshEmerald)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Delete for me
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.deleteMessage(message.messageId)
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MeshRose)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Delete for me", style = MaterialTheme.typography.bodyLarge, color = MeshRose)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delete Conversation Confirmation Dialog
    if (showDeleteConversationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationDialog = false },
            title = { Text("Delete entire conversation?") },
            text = { Text("All messages with ${viewModel.peerName} will be deleted permanently from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversation()
                        showDeleteConversationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshRose)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    isHighlighted: Boolean = false,
    peerAvatarImagePath: String? = null,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onQuotedClick: (messageId: String) -> Unit = {}
) {
    val isOutgoing = message.isOutgoing
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val bubbleBackground = if (isOutgoing) {
        MeshIndigo
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
        ) {
            if (!isOutgoing) {
                AvatarBadge(
                    name = message.senderName,
                    avatarColorHex = null,
                    imagePath = peerAvatarImagePath,
                    size = 28.dp,
                    modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(bubbleShape)
                    .background(bubbleBackground)
                    .then(
                        if (isHighlighted) {
                            Modifier.border(2.dp, MeshCyan, bubbleShape)
                        } else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            if (message.status == DeliveryStatus.FAILED) onRetry()
                        },
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
            // Replying Quote Header Card
            if (message.replyToMessageId != null || message.replyToPreview != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clickable {
                            message.replyToMessageId?.let { onQuotedClick(it) }
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isOutgoing) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MeshCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.replyToSenderName ?: "Quoted Message",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = MeshCyan
                            )
                            Text(
                                text = message.replyToPreview ?: "Original message unavailable.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isOutgoing) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Multi-Hop Relay Header
            if (!isOutgoing && message.hopCount > 0) {
                Text(
                    text = "Via Relay (${message.hopCount} hops)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshCyan,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Message Plaintext
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Footer (Time, Star, Delivery Status Icon)
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.isStarred) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Starred",
                        tint = if (isOutgoing) Color.Yellow else MeshCyan,
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 2.dp)
                    )
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isOutgoing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    DeliveryStatusIcon(status = message.status, size = 12.dp)
                    if (message.status == DeliveryStatus.FAILED) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tap to retry",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MeshRose
                        )
                    }
                }
            }
        }
    }
}
}


