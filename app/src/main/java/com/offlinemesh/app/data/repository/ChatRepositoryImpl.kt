package com.offlinemesh.app.data.repository

import android.util.Log
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.dao.MessageDao
import com.offlinemesh.app.data.local.dao.PeerDao
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.data.transport.TransportManager
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import com.offlinemesh.app.domain.mesh.DefaultMessageRelay
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import com.offlinemesh.app.domain.mesh.RelayDecision
import com.offlinemesh.app.domain.repository.ChatRepository
import com.offlinemesh.app.domain.repository.IdentityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

class ChatRepositoryImpl(
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val transportManager: TransportManager,
    private val identityRepository: IdentityRepository,
    private val keystoreManager: AndroidKeystoreManager,
    private val deduplicator: MessageDeduplicator
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relay = DefaultMessageRelay(deduplicator)

    init {
        listenToIncomingPackets()
    }

    private fun listenToIncomingPackets() {
        scope.launch {
            transportManager.incomingPackets.collectLatest { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    private suspend fun handleIncomingPacket(packet: ProtocolPacket) {
        val currentIdentity = identityRepository.getOrCreateIdentity()
        val decision = relay.evaluate(packet, currentIdentity.userId)

        when (decision) {
            is RelayDecision.Drop -> {
                Log.d(TAG, "DROPPED_PACKET packetId=${packet.packetId} reason=${decision.reason}")
            }

            is RelayDecision.Forward -> {
                Log.d(TAG, "FORWARDING_PACKET packetId=${packet.packetId} ttl=${decision.updatedPacket.ttl} hops=${decision.updatedPacket.hopCount}")
                transportManager.broadcastPacket(decision.updatedPacket)
            }

            is RelayDecision.DeliverLocally -> {
                processLocalDelivery(decision.packet, currentIdentity.userId)
            }
        }
    }

    private suspend fun processLocalDelivery(packet: ProtocolPacket, localUserId: String) {
        when (packet.packetType) {
            PacketType.DATA_DIRECT -> {
                Log.d(TAG, "MESSAGE_RECEIVED messageId=${packet.packetId} sender=${packet.senderId} payload='${packet.payload}'")

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

                // Persist peer if unknown
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

                // Persist message locally in Room
                val entity = MessageEntity(
                    messageId = packet.packetId,
                    conversationId = packet.senderId,
                    senderId = packet.senderId,
                    senderName = packet.senderDisplayName,
                    recipientId = localUserId,
                    content = packet.payload,
                    timestamp = packet.timestamp,
                    status = DeliveryStatus.DELIVERED,
                    isOutgoing = false,
                    hopCount = packet.hopCount,
                    signature = packet.signature
                )
                messageDao.insertMessage(entity)

                // Transmit DELIVERY_ACK
                val currentIdentity = identityRepository.getOrCreateIdentity()
                val ackPacket = ProtocolPacket(
                    packetId = UUID.randomUUID().toString(),
                    packetType = PacketType.DELIVERY_ACK,
                    senderId = currentIdentity.userId,
                    senderDisplayName = currentIdentity.displayName,
                    senderPublicKey = currentIdentity.publicKey,
                    recipientId = packet.senderId,
                    timestamp = System.currentTimeMillis(),
                    ttl = 3,
                    hopCount = 0,
                    payload = packet.packetId // Original messageId in ACK payload
                )
                val ackSent = transportManager.sendPacket(ackPacket)
                if (ackSent) {
                    Log.d(TAG, "ACK_SENT messageId=${packet.packetId} destination=${packet.senderId}")
                } else {
                    Log.w(TAG, "ACK_SEND_FAILED messageId=${packet.packetId}")
                }
            }

            PacketType.DELIVERY_ACK -> {
                val acknowledgedMessageId = packet.payload
                Log.d(TAG, "ACK_RECEIVED messageId=$acknowledgedMessageId from=${packet.senderId}")
                messageDao.updateMessageStatus(acknowledgedMessageId, DeliveryStatus.DELIVERED)
                Log.d(TAG, "MESSAGE_DELIVERED messageId=$acknowledgedMessageId")
            }

            else -> {
                // Other control frames
            }
        }
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    override fun getRecentConversations(): Flow<List<MessageEntity>> {
        return messageDao.getRecentConversations()
    }

    override suspend fun sendMessage(recipientId: String, recipientName: String, content: String): MessageEntity {
        val identity = identityRepository.getOrCreateIdentity()
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "MESSAGE_SEND_STARTED messageId=$messageId recipient=$recipientId content='$content'")

        // Generate ECDSA signature over packet
        val signPayload = "$messageId:${identity.userId}:$recipientId:$timestamp:$content"
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
            content = content,
            timestamp = timestamp,
            status = DeliveryStatus.SENDING,
            isOutgoing = true,
            hopCount = 0,
            signature = signature
        )

        // Save immediately in Room DB (offline-first)
        messageDao.insertMessage(messageEntity)

        // Wire protocol packet
        val packet = ProtocolPacket(
            protocolVersion = 1,
            packetId = messageId,
            packetType = PacketType.DATA_DIRECT,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = recipientId,
            timestamp = timestamp,
            ttl = 3,
            hopCount = 0,
            payload = content,
            signature = signature
        )

        // Dispatch over radio transport
        val success = transportManager.sendPacket(packet)
        val newStatus = if (success) DeliveryStatus.SENT else DeliveryStatus.FAILED

        if (success) {
            Log.d(TAG, "MESSAGE_SENT messageId=$messageId status=SENT")
        } else {
            Log.w(TAG, "MESSAGE_FAILED messageId=$messageId reason=TRANSPORT_UNREACHABLE")
        }

        messageDao.updateMessageStatus(messageId, newStatus)
        return messageEntity.copy(status = newStatus)
    }

    override suspend fun retrySendMessage(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        val identity = identityRepository.getOrCreateIdentity()

        Log.d(TAG, "MESSAGE_RETRY_STARTED messageId=$messageId recipient=${message.recipientId}")
        messageDao.updateMessageStatus(messageId, DeliveryStatus.SENDING)

        val packet = ProtocolPacket(
            protocolVersion = 1,
            packetId = message.messageId,
            packetType = PacketType.DATA_DIRECT,
            senderId = identity.userId,
            senderDisplayName = identity.displayName,
            senderPublicKey = identity.publicKey,
            recipientId = message.recipientId,
            timestamp = message.timestamp,
            ttl = 3,
            hopCount = 0,
            payload = message.content,
            signature = message.signature
        )

        val success = transportManager.sendPacket(packet)
        val newStatus = if (success) DeliveryStatus.SENT else DeliveryStatus.FAILED

        if (success) {
            Log.d(TAG, "MESSAGE_SENT messageId=$messageId status=SENT")
        } else {
            Log.w(TAG, "MESSAGE_FAILED messageId=$messageId reason=TRANSPORT_UNREACHABLE_ON_RETRY")
        }

        messageDao.updateMessageStatus(messageId, newStatus)
    }

    override suspend fun clearConversation(conversationId: String) {
        messageDao.clearConversation(conversationId)
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateMessageStatus(messageId, status)
    }
}
