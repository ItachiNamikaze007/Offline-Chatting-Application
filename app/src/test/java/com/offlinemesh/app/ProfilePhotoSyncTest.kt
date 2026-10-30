package com.offlinemesh.app

import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import com.offlinemesh.app.domain.mesh.DefaultMessageRelay
import com.offlinemesh.app.domain.mesh.InMemoryRoutingManager
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import com.offlinemesh.app.domain.mesh.RelayDecision
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProfilePhotoSyncTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun avatarPacket_serializationAndDeserialization_preservesPayloadAndTypes() {
        val sampleBase64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
        val packet = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.AVATAR_RESPONSE,
            senderId = "OFC-ALICE001",
            senderDisplayName = "Alice",
            senderPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
            recipientId = "OFC-BOB00002",
            timestamp = 1700000000000L,
            ttl = 8,
            hopCount = 0,
            payload = sampleBase64
        )

        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<ProtocolPacket>(serialized)

        assertEquals(PacketType.AVATAR_RESPONSE, deserialized.packetType)
        assertEquals("OFC-ALICE001", deserialized.senderId)
        assertEquals("OFC-BOB00002", deserialized.recipientId)
        assertEquals(sampleBase64, deserialized.payload)
        assertEquals(8, deserialized.ttl)
        assertEquals(0, deserialized.hopCount)
    }

    @Test
    fun avatarRequestPacket_serializationAndDeserialization() {
        val packet = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.AVATAR_REQUEST,
            senderId = "OFC-BOB00002",
            senderDisplayName = "Bob",
            senderPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
            recipientId = "OFC-ALICE001",
            timestamp = System.currentTimeMillis(),
            ttl = 8,
            hopCount = 0,
            payload = ""
        )

        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<ProtocolPacket>(serialized)

        assertEquals(PacketType.AVATAR_REQUEST, deserialized.packetType)
        assertEquals("OFC-BOB00002", deserialized.senderId)
        assertEquals("OFC-ALICE001", deserialized.recipientId)
    }

    @Test
    fun userIdentity_profilePhotoUpdateMaintainsCryptographicKeys() {
        val original = UserIdentity(
            userId = "OFC-USER0001",
            displayName = "Original Name",
            publicKey = "BASE64KEY12345",
            avatarColorHex = "#3B82F6",
            avatarImagePath = null,
            avatarVersion = 0L,
            createdAt = 1000L
        )

        val updated = original.copy(
            displayName = "Updated Name",
            avatarImagePath = "/data/user/0/app/files/avatars/self_avatar.jpg",
            avatarVersion = 1700000005000L
        )

        assertEquals("OFC-USER0001", updated.userId)
        assertEquals("BASE64KEY12345", updated.publicKey)
        assertEquals(1000L, updated.createdAt)
        assertEquals("/data/user/0/app/files/avatars/self_avatar.jpg", updated.avatarImagePath)
        assertEquals(1700000005000L, updated.avatarVersion)
    }

    @Test
    fun peerEntity_storesAndUpdatesCachedPhotoVersion() {
        val initialPeer = PeerEntity(
            userId = "OFC-PEER0001",
            displayName = "Peer One",
            publicKey = "PUBKEY987",
            avatarColorHex = "#10B981",
            avatarImagePath = null,
            avatarVersion = 0L
        )

        assertNull(initialPeer.avatarImagePath)
        assertEquals(0L, initialPeer.avatarVersion)

        val updatedPeer = initialPeer.copy(
            avatarImagePath = "/data/user/0/app/files/avatars/peer_OFC-PEER0001.jpg",
            avatarVersion = 1700000009999L
        )

        assertNotNull(updatedPeer.avatarImagePath)
        assertEquals("/data/user/0/app/files/avatars/peer_OFC-PEER0001.jpg", updatedPeer.avatarImagePath)
        assertEquals(1700000009999L, updatedPeer.avatarVersion)
    }

    @Test
    fun avatarSync_multiHopPacketRelay_forwardsAvatarPacketsWithoutInterference() {
        val deduplicator = MessageDeduplicator()
        val routingManager = InMemoryRoutingManager()
        val relay = DefaultMessageRelay(deduplicator, routingManager)

        // Topology: Local node is Relay Node B. Route to D is via Endpoint-C
        routingManager.addOrUpdateRoute(
            destinationUserId = "OFC-DEVICE-D",
            nextHopEndpointId = "EP-C",
            nextHopUserId = "OFC-DEVICE-C",
            hopCount = 2,
            displayName = "Device D",
            publicKey = "KEY-D",
            avatarColorHex = "#EC4899"
        )

        val avatarPacket = ProtocolPacket(
            packetId = "AVATAR-PKT-999",
            packetType = PacketType.AVATAR_RESPONSE,
            senderId = "OFC-DEVICE-A",
            senderDisplayName = "Device A",
            senderPublicKey = "KEY-A",
            recipientId = "OFC-DEVICE-D",
            timestamp = 1700000000000L,
            ttl = 6,
            hopCount = 1,
            payload = "BASE64_AVATAR_DATA_CHUNK"
        )

        val decision = relay.evaluate(
            packet = avatarPacket,
            localUserId = "OFC-DEVICE-B",
            incomingEndpointId = "EP-A"
        )

        assertTrue(decision is RelayDecision.Forward)
        val forwardDecision = decision as RelayDecision.Forward
        assertEquals("EP-C", forwardDecision.targetEndpointId)
        assertEquals("AVATAR-PKT-999", forwardDecision.updatedPacket.packetId)
        assertEquals("BASE64_AVATAR_DATA_CHUNK", forwardDecision.updatedPacket.payload)
        assertEquals(5, forwardDecision.updatedPacket.ttl)
        assertEquals(2, forwardDecision.updatedPacket.hopCount)
    }
}
