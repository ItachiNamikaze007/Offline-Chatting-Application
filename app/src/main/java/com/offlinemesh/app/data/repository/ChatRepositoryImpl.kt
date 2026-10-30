package com.offlinemesh.app.data.repository

import android.util.Log
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.core.crypto.PayloadCrypto
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.dao.MessageDao
import com.offlinemesh.app.data.local.dao.PeerDao
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.data.transport.TransportManager
import com.offlinemesh.app.data.transport.TransportPacket
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import com.offlinemesh.app.domain.mesh.DefaultMessageRelay
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import com.offlinemesh.app.domain.mesh.MessageRelay
import com.offlinemesh.app.domain.mesh.RelayDecision
import com.offlinemesh.app.domain.mesh.RoutingManager
import com.offlinemesh.app.domain.mesh.StoreAndForwardManager
import com.offlinemesh.app.domain.repository.ChatRepository
import com.offlinemesh.app.domain.repository.IdentityRepository
import com.offlinemesh.app.domain.repository.MessagePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.Context
import com.offlinemesh.app.core.notification.NotificationHelper
import com.offlinemesh.app.core.storage.AvatarStorageHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.UUID

@Serializable
data class RouteAdvertisementEntry(
    val destinationUserId: String,
    val hopCount: Int,
    val displayName: String? = null,
    val publicKey: String? = null,
    val avatarColorHex: String? = null,
    val avatarVersion: Long = 0L
)

@Serializable
data class RouteAdvertisementPayload(
    val routes: List<RouteAdvertisementEntry>
)

class ChatRepositoryImpl(
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val transportManager: TransportManager,
    private val identityRepository: IdentityRepository,
    private val keystoreManager: AndroidKeystoreManager,
    private val deduplicator: MessageDeduplicator,
    private val routingManager: RoutingManager,
    private val storeAndForwardManager: StoreAndForwardManager,
    private val context: Context? = null
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relay: MessageRelay = DefaultMessageRelay(deduplicator, routingManager)

    init {
        listenToIncomingPackets()
        monitorConnectedPeersForRouteChanges()
    }

    private fun listenToIncomingPackets() {
        scope.launch {
            transportManager.incomingPackets.collectLatest { transportPacket ->
                handleIncomingTransportPacket(transportPacket)
            }
        }
    }

    private fun monitorConnectedPeersForRouteChanges() {
        scope.launch {
            var previousConnectedIds = emptySet<String>()
            transportManager.connectedPeers.collectLatest { currentPeers ->
                val currentEndpointIds = currentPeers.map { it.endpointId }.toSet()

                // Detect disconnected endpoints and remove associated routes
                val disconnected = previousConnectedIds - currentEndpointIds
                for (endpointId in disconnected) {
                    Log.d(TAG, "ENDPOINT_DISCONNECTED_PURGE_ROUTES endpointId=$endpointId")
                    routingManager.removeRoutesForEndpoint(endpointId)
                }

                // For newly connected peers, register 1-hop route and exchange route advertisements
                val newlyConnected = currentPeers.filter { it.endpointId !in previousConnectedIds && it.userId != null }
                for (peer in newlyConnected) {
                    val peerUserId = peer.userId ?: continue
                    routingManager.addOrUpdateRoute(
                        destinationUserId = peerUserId,
                        nextHopEndpointId = peer.endpointId,
                        nextHopUserId = peerUserId,
                        hopCount = 1,
                        displayName = peer.displayName,
                        publicKey = peer.publicKey,
                        avatarColorHex = peer.avatarColorHex
                    )
                    sendRouteAdvertisement(peer.endpointId)
                    flushPendingPacketsForPeer(peerUserId)
                }

                previousConnectedIds = currentEndpointIds
            }
        }
    }

    private suspend fun handleIncomingTransportPacket(transportPacket: TransportPacket) {
        val (incomingEndpointId, packet) = transportPacket
        val currentIdentity = identityRepository.getOrCreateIdentity()

        // 1. Opportunistic reverse route learning: Record that sender is reachable via incomingEndpointId
        if (incomingEndpointId != null && packet.senderId.isNotEmpty() && packet.senderId != currentIdentity.userId) {
            val routeLearned = routingManager.addOrUpdateRoute(
                destinationUserId = packet.senderId,
                nextHopEndpointId = incomingEndpointId,
                nextHopUserId = packet.senderId,
                hopCount = packet.hopCount + 1,
                displayName = packet.senderDisplayName,
                publicKey = packet.senderPublicKey
            )
            if (routeLearned) {
                Log.d(TAG, "ROUTE_LEARNED destination=${packet.senderId} viaEndpoint=$incomingEndpointId hops=${packet.hopCount + 1}")
                flushPendingPacketsForPeer(packet.senderId)
            }
        }

        // 2. Evaluate packet against mesh relay rules
        val decision = relay.evaluate(packet, incomingEndpointId, currentIdentity.userId)

        when (decision) {
            is RelayDecision.Drop -> {
                Log.d(TAG, "PACKET_DROPPED packetId=${packet.packetId} type=${packet.packetType} reason=${decision.reason}")
            }

            is RelayDecision.Forward -> {
                val fwd = decision.updatedPacket
                Log.d(TAG, "PACKET_FORWARDING packetId=${fwd.packetId} type=${fwd.packetType} target=${decision.targetEndpointId} ttl=${fwd.ttl} hop=${fwd.hopCount}")
                if (decision.targetEndpointId != null) {
                    transportManager.sendPacket(fwd, destinationEndpointId = decision.targetEndpointId)
                } else {
                    transportManager.broadcastPacket(fwd, excludedEndpointId = decision.excludeEndpointId)
                }
            }

            is RelayDecision.DeliverLocally -> {
                processLocalDelivery(decision.packet, currentIdentity.userId, incomingEndpointId)
            }
        }
    }

    private suspend fun processLocalDelivery(
        packet: ProtocolPacket,
        localUserId: String,
        incomingEndpointId: String?
    ) {
        val currentIdentity = identityRepository.getOrCreateIdentity()

        when (packet.packetType) {
            PacketType.DATA_DIRECT -> {
                Log.d(TAG, "MESSAGE_RECEIVED messageId=${packet.packetId} sender=${packet.senderId} hops=${packet.hopCount}")

                // Cryptographic signature verification
                if (packet.signature != null && packet.senderPublicKey.isNotEmpty()) {
                    val signPayload = "${packet.packetId}:${packet.senderId}:${packet.recipientId}:${packet.timestamp}:${packet.payload}"
                    val isValid = keystoreManager.verify(
                        signPayload.toByteArray(StandardCharsets.UTF_8),
                        packet.signature,
                        packet.senderPublicKey
                    )
                    if (!isValid) {
                        Log.w(TAG, "SIGNATURE_VERIFICATION_FAILED messageId=${packet.packetId}")
                    } else {
                        Log.d(TAG, "SIGNATURE_VERIFIED messageId=${packet.packetId} sender=${packet.senderId}")
                    }
                }

                // Decrypt payload if encrypted
                val decryptedRaw = try {
                    if (PayloadCrypto.isEncrypted(packet.payload) && packet.senderPublicKey.isNotEmpty()) {
                        keystoreManager.decryptPayload(packet.payload, packet.senderPublicKey)
                    } else {
                        packet.payload
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "PAYLOAD_DECRYPTION_FAILED messageId=${packet.packetId} error=${e.message}")
                    packet.payload
                }

                // Parse structured message payload (supports reply metadata and backward compatibility)
                val parsedPayload = try {
                    json.decodeFromString<MessagePayload>(decryptedRaw)
                } catch (e: Exception) {
                    MessagePayload(text = decryptedRaw)
                }

                // Persist / update peer in Room DB
                val existingPeer = peerDao.getPeerById(packet.senderId)
                if (existingPeer == null) {
                    peerDao.insertOrUpdatePeer(
                        PeerEntity(
                            userId = packet.senderId,
                            displayName = packet.senderDisplayName,
                            publicKey = packet.senderPublicKey,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                } else {
                    peerDao.updateLastSeen(packet.senderId, System.currentTimeMillis())
                }

                // Persist delivered message locally in Room (marked unread until user opens conversation)
                val entity = MessageEntity(
                    messageId = packet.packetId,
                    conversationId = packet.senderId,
                    senderId = packet.senderId,
                    senderName = packet.senderDisplayName,
                    recipientId = localUserId,
                    content = parsedPayload.text,
                    timestamp = packet.timestamp,
                    status = DeliveryStatus.DELIVERED,
                    isOutgoing = false,
                    hopCount = packet.hopCount,
                    signature = packet.signature,
                    isRead = false,
                    isStarred = false,
                    replyToMessageId = parsedPayload.replyToMessageId,
                    replyToSenderId = parsedPayload.replyToSenderId,
                    replyToSenderName = parsedPayload.replyToSenderName,
                    replyToPreview = parsedPayload.replyToPreview,
                    replyToMessageType = parsedPayload.replyToMessageType
                )
                messageDao.insertMessage(entity)

                // Trigger real-time background notification
                val ctx = context
                if (ctx != null) {
                    val senderPeer = peerDao.getPeerById(packet.senderId)
                    val privacy = identityRepository.notificationPrivacy.value
                    NotificationHelper.showMessageNotification(
                        context = ctx,
                        conversationId = packet.senderId,
                        senderName = packet.senderDisplayName,
                        content = entity.content,
                        avatarImagePath = senderPeer?.avatarImagePath,
                        avatarColorHex = senderPeer?.avatarColorHex,
                        privacy = privacy
                    )
                }

                // Transmit multi-hop DELIVERY_ACK back to original sender
                val ackPacket = ProtocolPacket(
                    packetId = UUID.randomUUID().toString(),
                    packetType = PacketType.DELIVERY_ACK,
                    senderId = currentIdentity.userId,
                    senderDisplayName = currentIdentity.displayName,
                    senderPublicKey = currentIdentity.publicKey,
                    recipientId = packet.senderId,
                    timestamp = System.currentTimeMillis(),
                    ttl = ProtocolPacket.DEFAULT_TTL,
                    hopCount = 0,
                    payload = packet.packetId // Original messageId acknowledged
                )
                sendPacketMultiHop(ackPacket)
                Log.d(TAG, "DELIVERY_ACK_DISPATCHED acknowledgedMessageId=${packet.packetId} destination=${packet.senderId}")
            }

            PacketType.DELIVERY_ACK -> {
                val acknowledgedMessageId = packet.payload
                Log.d(TAG, "ACK_RECEIVED acknowledgedMessageId=$acknowledgedMessageId from=${packet.senderId}")
                messageDao.updateMessageStatus(acknowledgedMessageId, DeliveryStatus.DELIVERED)
                storeAndForwardManager.markPacketDelivered(acknowledgedMessageId)
                Log.d(TAG, "MESSAGE_DELIVERY_CONFIRMED messageId=$acknowledgedMessageId")
            }

            PacketType.ROUTE_REQUEST -> {
                Log.d(TAG, "ROUTE_REQUEST_REACHED_DESTINATION sender=${packet.senderId} target=${packet.recipientId}")
                if (packet.recipientId == localUserId) {
                    // Send ROUTE_REPLY back to requester
                    val replyPacket = ProtocolPacket(
                        packetId = UUID.randomUUID().toString(),
                        packetType = PacketType.ROUTE_REPLY,
                        senderId = currentIdentity.userId,
                        senderDisplayName = currentIdentity.displayName,
                        senderPublicKey = currentIdentity.publicKey,
                        recipientId = packet.senderId,
                        timestamp = System.currentTimeMillis(),
                        ttl = ProtocolPacket.DEFAULT_TTL,
                        hopCount = 0,
                        payload = currentIdentity.avatarColorHex
                    )
                    sendPacketMultiHop(replyPacket)
                    Log.d(TAG, "ROUTE_REPLY_SENT to=${packet.senderId}")
                }
            }

            PacketType.ROUTE_REPLY -> {
                Log.d(TAG, "ROUTE_REPLY_RECEIVED destination=${packet.senderId} hops=${packet.hopCount}")
                if (incomingEndpointId != null) {
                    routingManager.addOrUpdateRoute(
                        destinationUserId = packet.senderId,
                        nextHopEndpointId = incomingEndpointId,
                        nextHopUserId = packet.senderId,
                        hopCount = packet.hopCount + 1,
                        displayName = packet.senderDisplayName,
                        publicKey = packet.senderPublicKey,
                        avatarColorHex = packet.payload
                    )
                }

                // Update peer repository and flush any queued messages
                val peer = PeerEntity(
                    userId = packet.senderId,
                    displayName = packet.senderDisplayName,
                    publicKey = packet.senderPublicKey,
                    avatarColorHex = packet.payload.ifEmpty { "#3B82F6" },
                    lastSeen = System.currentTimeMillis()
                )
                peerDao.insertOrUpdatePeer(peer)
                flushPendingPacketsForPeer(packet.senderId)
            }

            PacketType.ROUTE_ADVERTISEMENT -> {
                if (incomingEndpointId != null) {
                    ingestRouteAdvertisement(packet.payload, incomingEndpointId, packet.senderId)
                }
            }

            PacketType.AVATAR_REQUEST -> {
                Log.d(TAG, "AVATAR_REQUEST_RECEIVED from=${packet.senderId}")
                val ctx = context
                if (ctx != null) {
                    val avatarBase64 = AvatarStorageHelper.getSelfAvatarBase64(ctx)
                    if (avatarBase64 != null) {
                        val responsePacket = ProtocolPacket(
                            packetId = UUID.randomUUID().toString(),
                            packetType = PacketType.AVATAR_RESPONSE,
                            senderId = currentIdentity.userId,
                            senderDisplayName = currentIdentity.displayName,
                            senderPublicKey = currentIdentity.publicKey,
                            recipientId = packet.senderId,
                            timestamp = currentIdentity.avatarVersion,
                            ttl = ProtocolPacket.DEFAULT_TTL,
                            hopCount = 0,
                            payload = avatarBase64
                        )
                        val nextHop = routingManager.getNextHopEndpoint(packet.senderId)
                        if (nextHop != null) {
                            transportManager.sendPacket(responsePacket, destinationEndpointId = nextHop)
                        } else {
                            transportManager.broadcastPacket(responsePacket)
                        }
                    }
                }
            }

            PacketType.AVATAR_RESPONSE -> {
                Log.d(TAG, "AVATAR_RESPONSE_RECEIVED from=${packet.senderId}")
                val ctx = context
                if (ctx != null && packet.payload.isNotEmpty()) {
                    val savedPath = AvatarStorageHelper.savePeerAvatarFromBase64(ctx, packet.senderId, packet.payload)
                    if (savedPath != null) {
                        peerDao.updatePeerAvatar(packet.senderId, savedPath, packet.timestamp)
                    }
                }
            }

            PacketType.HANDSHAKE, PacketType.HANDSHAKE_ACK -> {
                if (incomingEndpointId != null) {
                    routingManager.addOrUpdateRoute(
                        destinationUserId = packet.senderId,
                        nextHopEndpointId = incomingEndpointId,
                        nextHopUserId = packet.senderId,
                        hopCount = 1,
                        displayName = packet.senderDisplayName,
                        publicKey = packet.senderPublicKey,
                        avatarColorHex = packet.payload
                    )
                    sendRouteAdvertisement(incomingEndpointId)
                    flushPendingPacketsForPeer(packet.senderId)
                    requestAvatarIfMissing(packet.senderId)
                }
            }

            else -> {
                // Other control packets
            }
        }
    }

    private suspend fun sendRouteAdvertisement(targetEndpointId: String) {
        val identity = identityRepository.getOrCreateIdentity()
        val routesToAdvertise = routingManager.getRoutesForAdvertisement(excludeEndpointId = targetEndpointId)
        if (routesToAdvertise.isEmpty()) return

        val entries = routesToAdvertise.map {
            RouteAdvertisementEntry(
                destinationUserId = it.destinationUserId,
                hopCount = it.hopCount,
                displayName = it.displayName,
                publicKey = it.publicKey,
                avatarColorHex = it.avatarColorHex
            )
        }
        val payloadJson = json.encodeToString(RouteAdvertisementPayload(entries))

        val adPacket = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.ROUTE_ADVERTISEMENT,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = ProtocolPacket.BROADCAST_ID,
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            hopCount = 0,
            payload = payloadJson
        )
        transportManager.sendPacket(adPacket, destinationEndpointId = targetEndpointId)
    }

    private suspend fun ingestRouteAdvertisement(
        payloadJson: String,
        incomingEndpointId: String,
        senderUserId: String
    ) {
        val currentIdentity = identityRepository.getOrCreateIdentity()
        try {
            val adPayload = json.decodeFromString<RouteAdvertisementPayload>(payloadJson)
            for (entry in adPayload.routes) {
                if (entry.destinationUserId != currentIdentity.userId) {
                    val updated = routingManager.addOrUpdateRoute(
                        destinationUserId = entry.destinationUserId,
                        nextHopEndpointId = incomingEndpointId,
                        nextHopUserId = senderUserId,
                        hopCount = entry.hopCount + 1,
                        displayName = entry.displayName,
                        publicKey = entry.publicKey,
                        avatarColorHex = entry.avatarColorHex
                    )
                    if (updated) {
                        flushPendingPacketsForPeer(entry.destinationUserId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse route advertisement: ${e.message}")
        }
    }

    private suspend fun sendPacketMultiHop(packet: ProtocolPacket): Boolean {
        val nextHop = routingManager.getNextHopEndpoint(packet.recipientId)
        return if (nextHop != null) {
            transportManager.sendPacket(packet, destinationEndpointId = nextHop)
        } else {
            // Broadcast packet if unrouted or discovery
            transportManager.broadcastPacket(packet)
        }
    }

    private suspend fun flushPendingPacketsForPeer(peerUserId: String) {
        val nextHopEndpointId = routingManager.getNextHopEndpoint(peerUserId) ?: return
        val pendingPackets = storeAndForwardManager.getPendingPacketsForPeer(peerUserId)

        for (packet in pendingPackets) {
            Log.d(TAG, "FLUSHING_STORE_AND_FORWARD_PACKET packetId=${packet.packetId} destination=$peerUserId via=$nextHopEndpointId")
            val success = transportManager.sendPacket(packet, destinationEndpointId = nextHopEndpointId)
            if (success) {
                storeAndForwardManager.markPacketDelivered(packet.packetId)
                messageDao.updateMessageStatus(packet.packetId, DeliveryStatus.SENT)
            } else {
                storeAndForwardManager.incrementRetryAndCheckExpired(packet.packetId)
            }
        }
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    override fun getRecentConversations(): Flow<List<MessageEntity>> {
        return messageDao.getRecentConversations()
    }

    override suspend fun sendMessage(
        recipientId: String,
        recipientName: String,
        content: String,
        replyToMessageId: String?,
        replyToSenderId: String?,
        replyToSenderName: String?,
        replyToPreview: String?,
        replyToMessageType: String?
    ): MessageEntity {
        val identity = identityRepository.getOrCreateIdentity()
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "MESSAGE_SEND_INITIATED messageId=$messageId recipient=$recipientId")

        // Retrieve recipient's public key if known (from Room or RoutingManager)
        val peer = peerDao.getPeerById(recipientId)
        val recipientPublicKey = peer?.publicKey ?: routingManager.getRoute(recipientId)?.publicKey

        // Encode inner structured payload containing content & reply reference
        val payloadObj = MessagePayload(
            text = content,
            replyToMessageId = replyToMessageId,
            replyToSenderId = replyToSenderId,
            replyToSenderName = replyToSenderName,
            replyToPreview = replyToPreview,
            replyToMessageType = replyToMessageType
        )
        val rawPayloadJson = json.encodeToString(payloadObj)

        // Encrypt payload if recipient public key is known; otherwise send in clear
        val payloadToSend = if (!recipientPublicKey.isNullOrEmpty()) {
            try {
                keystoreManager.encryptPayload(rawPayloadJson, recipientPublicKey)
            } catch (e: Exception) {
                Log.e(TAG, "ENCRYPTION_FAILED messageId=$messageId error=${e.message}", e)
                rawPayloadJson
            }
        } else {
            rawPayloadJson
        }

        // Generate ECDSA signature over packet
        val signPayload = "$messageId:${identity.userId}:$recipientId:$timestamp:$payloadToSend"
        val signature = try {
            keystoreManager.sign(signPayload.toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "SIGNING_FAILED messageId=$messageId error=${e.message}", e)
            null
        }

        val messageEntity = MessageEntity(
            messageId = messageId,
            conversationId = recipientId,
            senderId = identity.userId,
            senderName = identity.displayName,
            recipientId = recipientId,
            content = content, // Plaintext saved in local Room database
            timestamp = timestamp,
            status = DeliveryStatus.SENDING,
            isOutgoing = true,
            hopCount = 0,
            signature = signature,
            isRead = true,
            isStarred = false,
            replyToMessageId = replyToMessageId,
            replyToSenderId = replyToSenderId,
            replyToSenderName = replyToSenderName,
            replyToPreview = replyToPreview,
            replyToMessageType = replyToMessageType
        )

        // Save immediately in Room DB (offline-first)
        messageDao.insertMessage(messageEntity)

        // Wire protocol packet with encrypted payload
        val packet = ProtocolPacket(
            protocolVersion = 2,
            packetId = messageId,
            packetType = PacketType.DATA_DIRECT,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = recipientId,
            timestamp = timestamp,
            ttl = ProtocolPacket.DEFAULT_TTL,
            hopCount = 0,
            payload = payloadToSend,
            signature = signature
        )

        // Check if next-hop route exists
        val nextHop = routingManager.getNextHopEndpoint(recipientId)
        val newStatus = if (nextHop != null) {
            val sent = transportManager.sendPacket(packet, destinationEndpointId = nextHop)
            if (sent) DeliveryStatus.SENT else DeliveryStatus.FAILED
        } else {
            // Queue in store and forward manager and trigger on-demand route discovery
            storeAndForwardManager.queuePacket(packet)
            initiateRouteDiscovery(recipientId)
            DeliveryStatus.SENDING
        }

        messageDao.updateMessageStatus(messageId, newStatus)
        return messageEntity.copy(status = newStatus)
    }

    private suspend fun initiateRouteDiscovery(targetRecipientId: String) {
        val identity = identityRepository.getOrCreateIdentity()
        val routeRequestPacket = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.ROUTE_REQUEST,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = targetRecipientId,
            timestamp = System.currentTimeMillis(),
            ttl = ProtocolPacket.DEFAULT_TTL,
            hopCount = 0,
            payload = ""
        )
        Log.d(TAG, "INITIATING_ROUTE_DISCOVERY target=$targetRecipientId")
        transportManager.broadcastPacket(routeRequestPacket)
    }

    override suspend fun retrySendMessage(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        val identity = identityRepository.getOrCreateIdentity()

        Log.d(TAG, "MESSAGE_RETRY_INITIATED messageId=$messageId recipient=${message.recipientId}")
        messageDao.updateMessageStatus(messageId, DeliveryStatus.SENDING)

        val peer = peerDao.getPeerById(message.recipientId)
        val recipientPublicKey = peer?.publicKey ?: routingManager.getRoute(message.recipientId)?.publicKey

        val payloadToSend = if (!recipientPublicKey.isNullOrEmpty()) {
            try {
                keystoreManager.encryptPayload(message.content, recipientPublicKey)
            } catch (e: Exception) {
                message.content
            }
        } else {
            message.content
        }

        val packet = ProtocolPacket(
            protocolVersion = 2,
            packetId = message.messageId,
            packetType = PacketType.DATA_DIRECT,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = message.recipientId,
            timestamp = message.timestamp,
            ttl = ProtocolPacket.DEFAULT_TTL,
            hopCount = 0,
            payload = payloadToSend,
            signature = message.signature
        )

        val nextHop = routingManager.getNextHopEndpoint(message.recipientId)
        val newStatus = if (nextHop != null) {
            val sent = transportManager.sendPacket(packet, destinationEndpointId = nextHop)
            if (sent) DeliveryStatus.SENT else DeliveryStatus.FAILED
        } else {
            storeAndForwardManager.queuePacket(packet)
            initiateRouteDiscovery(message.recipientId)
            DeliveryStatus.SENDING
        }

        messageDao.updateMessageStatus(messageId, newStatus)
    }

    override suspend fun deleteMessage(messageId: String) {
        storeAndForwardManager.markPacketDelivered(messageId)
        messageDao.deleteMessageById(messageId)
    }

    override suspend fun clearConversation(conversationId: String) {
        messageDao.clearConversation(conversationId)
        context?.let { NotificationHelper.cancelMessageNotification(it, conversationId) }
    }

    override suspend fun clearAllMessages() {
        messageDao.clearAllMessages()
        context?.let { NotificationHelper.cancelAllNotifications(it) }
    }

    override suspend fun markConversationAsRead(conversationId: String) {
        messageDao.markConversationAsRead(conversationId)
        context?.let { NotificationHelper.cancelMessageNotification(it, conversationId) }
    }

    override fun getUnreadCount(conversationId: String): Flow<Int> {
        return messageDao.getUnreadCountForConversation(conversationId)
    }

    override fun getTotalUnreadCount(): Flow<Int> {
        return messageDao.getTotalUnreadCount()
    }

    override suspend fun toggleStarMessage(messageId: String, isStarred: Boolean) {
        messageDao.setStarred(messageId, isStarred)
    }

    override fun searchMessages(query: String): Flow<List<MessageEntity>> {
        return messageDao.searchMessages(query)
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateMessageStatus(messageId, status)
    }

    override suspend fun syncProfilePhoto() {
        val ctx = context ?: return
        val identity = identityRepository.getOrCreateIdentity()
        val avatarBase64 = AvatarStorageHelper.getSelfAvatarBase64(ctx) ?: return

        val responsePacket = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.AVATAR_RESPONSE,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = ProtocolPacket.BROADCAST_ID,
            timestamp = identity.avatarVersion,
            ttl = 2,
            hopCount = 0,
            payload = avatarBase64
        )
        transportManager.broadcastPacket(responsePacket)
    }

    override suspend fun requestPeerAvatar(peerUserId: String) {
        requestAvatarIfMissing(peerUserId)
    }

    private suspend fun requestAvatarIfMissing(peerUserId: String) {
        val identity = identityRepository.getOrCreateIdentity()
        val existing = peerDao.getPeerById(peerUserId)
        if (existing?.avatarImagePath == null) {
            val requestPacket = ProtocolPacket(
                packetId = UUID.randomUUID().toString(),
                packetType = PacketType.AVATAR_REQUEST,
                senderId = identity.userId,
                senderDisplayName = identity.displayName,
                senderPublicKey = identity.publicKey,
                recipientId = peerUserId,
                timestamp = System.currentTimeMillis(),
                ttl = ProtocolPacket.DEFAULT_TTL,
                hopCount = 0,
                payload = ""
            )
            val nextHop = routingManager.getNextHopEndpoint(peerUserId)
            if (nextHop != null) {
                transportManager.sendPacket(requestPacket, destinationEndpointId = nextHop)
            } else {
                transportManager.broadcastPacket(requestPacket)
            }
        }
    }
}
