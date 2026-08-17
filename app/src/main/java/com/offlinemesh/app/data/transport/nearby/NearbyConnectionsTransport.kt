package com.offlinemesh.app.data.transport.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.core.model.PeerConnectionState
import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.transport.TransportManager
import com.offlinemesh.app.data.transport.protocol.PacketCodec
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NearbyConnectionsTransport(
    private val context: Context,
    private val keystoreManager: AndroidKeystoreManager
) : TransportManager {

    companion object {
        private const val TAG = "NearbyTransport"
        private const val SERVICE_ID = "com.offlinemesh.comm"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var localIdentity: UserIdentity? = null

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<ProtocolPacket>(extraBufferCapacity = 128)
    override val incomingPackets: SharedFlow<ProtocolPacket> = _incomingPackets.asSharedFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Radio Endpoint <-> Permanent OFC User ID bidirectional mappings
    private val endpointToUserIdMap = ConcurrentHashMap<String, String>()
    private val userIdToEndpointMap = ConcurrentHashMap<String, String>()
    private val connectingEndpoints = ConcurrentHashMap.newKeySet<String>()

    fun setLocalIdentity(identity: UserIdentity) {
        this.localIdentity = identity
        Log.d(TAG, "LOCAL_IDENTITY_SET ofcId=${identity.userId} name=${identity.displayName}")
    }

    override fun start(localUserIdentifier: String) {
        Log.d(TAG, "START_TRANSPORT identifier=$localUserIdentifier")
        startAdvertising(localUserIdentifier)
        startDiscovery()
    }

    override fun stop() {
        Log.d(TAG, "STOP_TRANSPORT")
        stopAdvertising()
        stopDiscovery()
        try {
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping endpoints: ${e.message}", e)
        }
        _connectedPeers.value = emptyList()
        _discoveredPeers.value = emptyList()
        endpointToUserIdMap.clear()
        userIdToEndpointMap.clear()
        connectingEndpoints.clear()
    }

    override fun startAdvertising(localDisplayName: String) {
        if (_isAdvertising.value) return

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        val advertisedName = localIdentity?.let { "${it.displayName}#${it.userId}" } ?: localDisplayName

        connectionsClient.startAdvertising(
            advertisedName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            _isAdvertising.value = true
            Log.d(TAG, "ADVERTISING_STARTED name=$advertisedName serviceId=$SERVICE_ID")
        }.addOnFailureListener { e ->
            _isAdvertising.value = false
            Log.e(TAG, "ADVERTISING_FAILED: ${e.message}", e)
        }
    }

    override fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising: ${e.message}", e)
        }
        _isAdvertising.value = false
        Log.d(TAG, "ADVERTISING_STOPPED")
    }

    override fun startDiscovery() {
        if (_isDiscovering.value) return

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            _isDiscovering.value = true
            Log.d(TAG, "DISCOVERY_STARTED serviceId=$SERVICE_ID")
        }.addOnFailureListener { e ->
            _isDiscovering.value = false
            Log.e(TAG, "DISCOVERY_FAILED: ${e.message}", e)
        }
    }

    override fun stopDiscovery() {
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery: ${e.message}", e)
        }
        _isDiscovering.value = false
        Log.d(TAG, "DISCOVERY_STOPPED")
    }

    override fun connectToPeer(endpointId: String) {
        val identity = localIdentity
        val localName = identity?.let { "${it.displayName}#${it.userId}" } ?: "OfflineMesh User"

        if (connectingEndpoints.contains(endpointId)) {
            Log.d(TAG, "Already connecting to endpoint $endpointId, skipping duplicate request")
            return
        }

        connectingEndpoints.add(endpointId)
        _discoveredPeers.update { list ->
            list.map { if (it.endpointId == endpointId) it.copy(connectionState = PeerConnectionState.CONNECTING) else it }
        }

        Log.d(TAG, "REQUESTING_CONNECTION endpointId=$endpointId localName=$localName")

        connectionsClient.requestConnection(
            localName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "REQUEST_CONNECTION_INITIATED endpointId=$endpointId")
        }.addOnFailureListener { e ->
            connectingEndpoints.remove(endpointId)
            Log.e(TAG, "REQUEST_CONNECTION_FAILED endpointId=$endpointId error=${e.message}", e)
            _discoveredPeers.update { list ->
                list.map { if (it.endpointId == endpointId) it.copy(connectionState = PeerConnectionState.DISCONNECTED) else it }
            }
        }
    }

    override fun disconnectPeer(endpointId: String) {
        Log.d(TAG, "DISCONNECTING_PEER endpointId=$endpointId")
        try {
            connectionsClient.disconnectFromEndpoint(endpointId)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting endpoint: $endpointId", e)
        }
        handleDisconnection(endpointId)
    }

    override suspend fun sendPacket(packet: ProtocolPacket, destinationEndpointId: String?): Boolean {
        // Resolve target endpoint
        val targetEndpoint = destinationEndpointId
            ?: userIdToEndpointMap[packet.recipientId]
            ?: if (_connectedPeers.value.any { it.endpointId == packet.recipientId }) packet.recipientId else null

        if (targetEndpoint == null) {
            Log.w(
                TAG,
                "MESSAGE_FAILED messageId=${packet.packetId} reason=NO_ENDPOINT_FOR_RECIPIENT_${packet.recipientId} " +
                        "availableMappings=${userIdToEndpointMap.keys} connectedEndpoints=${_connectedPeers.value.map { it.endpointId }}"
            )
            return false
        }

        return try {
            Log.d(TAG, "MESSAGE_SEND_STARTED messageId=${packet.packetId} recipient=${packet.recipientId} endpointId=$targetEndpoint")
            val bytes = PacketCodec.encode(packet)
            val payload = Payload.fromBytes(bytes)
            connectionsClient.sendPayload(targetEndpoint, payload).await()
            Log.d(TAG, "PAYLOAD_SENT messageId=${packet.packetId} endpointId=$targetEndpoint")
            true
        } catch (e: Exception) {
            Log.e(TAG, "MESSAGE_FAILED messageId=${packet.packetId} reason=${e.message}", e)
            false
        }
    }

    override suspend fun broadcastPacket(packet: ProtocolPacket): Boolean {
        val connectedEndpoints = _connectedPeers.value.map { it.endpointId }
        if (connectedEndpoints.isEmpty()) {
            Log.d(TAG, "Broadcast skipped: no connected endpoints")
            return false
        }

        return try {
            val bytes = PacketCodec.encode(packet)
            val payload = Payload.fromBytes(bytes)
            connectionsClient.sendPayload(connectedEndpoints, payload).await()
            Log.d(TAG, "BROADCAST_PAYLOAD_SENT packetId=${packet.packetId} endpoints=$connectedEndpoints")
            true
        } catch (e: Exception) {
            Log.e(TAG, "BROADCAST_FAILED packetId=${packet.packetId} error=${e.message}", e)
            false
        }
    }

    // --- Nearby Connections Callbacks ---

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val rawName = info.endpointName
            val parts = rawName.split("#")
            val displayName = parts.getOrNull(0) ?: rawName
            val possibleUserId = parts.getOrNull(1)

            Log.d(TAG, "PEER_DISCOVERED endpointId=$endpointId name=$displayName ofcId=$possibleUserId")

            val peer = PeerDevice(
                endpointId = endpointId,
                userId = possibleUserId,
                displayName = displayName,
                connectionState = PeerConnectionState.DISCOVERED,
                lastSeen = System.currentTimeMillis()
            )

            _discoveredPeers.update { current ->
                val filtered = current.filterNot { it.endpointId == endpointId }
                filtered + peer
            }

            if (possibleUserId != null) {
                endpointToUserIdMap[endpointId] = possibleUserId
                userIdToEndpointMap[possibleUserId] = endpointId
            }

            // Automatic connection tie-breaking:
            // Device with lexicographically smaller user ID initiates connection to avoid double-initiating collision
            val localId = localIdentity?.userId
            if (localId != null && possibleUserId != null && localId < possibleUserId) {
                Log.d(TAG, "AUTO_CONNECTING_TO_PEER localId=$localId < remoteId=$possibleUserId -> endpointId=$endpointId")
                connectToPeer(endpointId)
            } else if (possibleUserId == null) {
                // If remote is legacy or raw name, connect
                connectToPeer(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "ENDPOINT_LOST endpointId=$endpointId")
            _discoveredPeers.update { current -> current.filterNot { it.endpointId == endpointId } }
            connectingEndpoints.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "CONNECTION_INITIATED endpointId=$endpointId name=${info.endpointName}")
            // Accept connection immediately on both sides
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "CONNECTION_ACCEPTED endpointId=$endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ACCEPT_CONNECTION_FAILED endpointId=$endpointId error=${e.message}", e)
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val statusCode = resolution.status.statusCode
            Log.d(TAG, "CONNECTION_RESULT endpointId=$endpointId status=$statusCode")
            connectingEndpoints.remove(endpointId)

            when (statusCode) {
                ConnectionsStatusCodes.STATUS_OK,
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.d(TAG, "ENDPOINT_CONNECTED endpointId=$endpointId")
                    val existing = _discoveredPeers.value.find { it.endpointId == endpointId }
                    val connectedPeer = PeerDevice(
                        endpointId = endpointId,
                        userId = existing?.userId,
                        displayName = existing?.displayName ?: "Nearby Peer",
                        connectionState = PeerConnectionState.CONNECTED
                    )
                    _connectedPeers.update { current ->
                        current.filterNot { it.endpointId == endpointId } + connectedPeer
                    }
                    _discoveredPeers.update { current ->
                        current.map { if (it.endpointId == endpointId) it.copy(connectionState = PeerConnectionState.CONNECTED) else it }
                    }

                    // Exchange permanent cryptographic identity handshake
                    sendHandshake(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "CONNECTION_REJECTED endpointId=$endpointId")
                    handleDisconnection(endpointId)
                }
                else -> {
                    Log.e(TAG, "CONNECTION_ERROR endpointId=$endpointId statusCode=$statusCode")
                    handleDisconnection(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "ENDPOINT_DISCONNECTED endpointId=$endpointId")
            handleDisconnection(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: run {
                Log.w(TAG, "Received non-byte payload from $endpointId")
                return
            }
            val packet = PacketCodec.decodeOrNull(bytes) ?: run {
                Log.w(TAG, "Received unparseable protocol packet from $endpointId (size=${bytes.size})")
                return
            }

            scope.launch {
                handleIncomingPacket(endpointId, packet)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Transfer status update
        }
    }

    private fun sendHandshake(endpointId: String) {
        val identity = localIdentity ?: run {
            Log.w(TAG, "Cannot send handshake: localIdentity is null")
            return
        }
        scope.launch {
            val handshakePacket = ProtocolPacket(
                packetId = UUID.randomUUID().toString(),
                packetType = PacketType.HANDSHAKE,
                senderId = identity.userId,
                senderDisplayName = identity.displayName,
                senderPublicKey = identity.publicKey,
                recipientId = ProtocolPacket.BROADCAST_ID,
                timestamp = System.currentTimeMillis(),
                ttl = 1,
                hopCount = 0,
                payload = identity.avatarColorHex
            )
            val success = sendPacket(handshakePacket, destinationEndpointId = endpointId)
            if (success) {
                Log.d(TAG, "HANDSHAKE_SENT endpointId=$endpointId ofcId=${identity.userId}")
            } else {
                Log.e(TAG, "HANDSHAKE_SEND_FAILED endpointId=$endpointId")
            }
        }
    }

    private suspend fun handleIncomingPacket(endpointId: String, packet: ProtocolPacket) {
        when (packet.packetType) {
            PacketType.HANDSHAKE -> {
                Log.d(TAG, "HANDSHAKE_RECEIVED endpointId=$endpointId senderId=${packet.senderId} name=${packet.senderDisplayName}")
                // Register verified permanent OFC mapping
                endpointToUserIdMap[endpointId] = packet.senderId
                userIdToEndpointMap[packet.senderId] = endpointId
                Log.d(TAG, "PEER_IDENTITY_VERIFIED ofcId=${packet.senderId} endpointId=$endpointId")

                _connectedPeers.update { current ->
                    current.map {
                        if (it.endpointId == endpointId) {
                            it.copy(
                                userId = packet.senderId,
                                displayName = packet.senderDisplayName,
                                publicKey = packet.senderPublicKey,
                                avatarColorHex = packet.payload
                            )
                        } else it
                    }
                }

                _discoveredPeers.update { current ->
                    current.map {
                        if (it.endpointId == endpointId) {
                            it.copy(
                                userId = packet.senderId,
                                displayName = packet.senderDisplayName,
                                publicKey = packet.senderPublicKey,
                                avatarColorHex = packet.payload
                            )
                        } else it
                    }
                }

                // Reply with HANDSHAKE_ACK
                val identity = localIdentity
                if (identity != null) {
                    val ackPacket = ProtocolPacket(
                        packetId = UUID.randomUUID().toString(),
                        packetType = PacketType.HANDSHAKE_ACK,
                        senderId = identity.userId,
                        senderDisplayName = identity.displayName,
                        senderPublicKey = identity.publicKey,
                        recipientId = packet.senderId,
                        timestamp = System.currentTimeMillis(),
                        ttl = 1,
                        hopCount = 0,
                        payload = identity.avatarColorHex
                    )
                    sendPacket(ackPacket, destinationEndpointId = endpointId)
                    Log.d(TAG, "HANDSHAKE_ACK_SENT endpointId=$endpointId")
                }

                _incomingPackets.emit(packet)
            }

            PacketType.HANDSHAKE_ACK -> {
                Log.d(TAG, "HANDSHAKE_ACK_RECEIVED endpointId=$endpointId senderId=${packet.senderId}")
                endpointToUserIdMap[endpointId] = packet.senderId
                userIdToEndpointMap[packet.senderId] = endpointId
                Log.d(TAG, "PEER_IDENTITY_VERIFIED ofcId=${packet.senderId} endpointId=$endpointId")

                _connectedPeers.update { current ->
                    current.map {
                        if (it.endpointId == endpointId) {
                            it.copy(
                                userId = packet.senderId,
                                displayName = packet.senderDisplayName,
                                publicKey = packet.senderPublicKey,
                                avatarColorHex = packet.payload
                            )
                        } else it
                    }
                }
                _incomingPackets.emit(packet)
            }

            else -> {
                _incomingPackets.emit(packet)
            }
        }
    }

    private fun handleDisconnection(endpointId: String) {
        val userId = endpointToUserIdMap.remove(endpointId)
        if (userId != null) {
            userIdToEndpointMap.remove(userId)
        }
        connectingEndpoints.remove(endpointId)

        _connectedPeers.update { current -> current.filterNot { it.endpointId == endpointId } }
        _discoveredPeers.update { current ->
            current.map { if (it.endpointId == endpointId) it.copy(connectionState = PeerConnectionState.DISCONNECTED) else it }
        }
        Log.d(TAG, "DISCONNECTION_HANDLED endpointId=$endpointId ofcId=$userId activeConnectedCount=${_connectedPeers.value.size}")
    }
}
