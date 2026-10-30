package com.offlinemesh.app.di

import android.content.Context
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.data.local.OfflineMeshDatabase
import com.offlinemesh.app.data.repository.ChatRepositoryImpl
import com.offlinemesh.app.data.repository.CommunityRepositoryImpl
import com.offlinemesh.app.data.repository.IdentityRepositoryImpl
import com.offlinemesh.app.data.repository.PeerRepositoryImpl
import com.offlinemesh.app.data.transport.TransportManager
import com.offlinemesh.app.data.transport.nearby.NearbyConnectionsTransport
import com.offlinemesh.app.domain.mesh.InMemoryRoutingManager
import com.offlinemesh.app.domain.mesh.InMemoryStoreAndForwardManager
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import com.offlinemesh.app.domain.mesh.RoutingManager
import com.offlinemesh.app.domain.mesh.StoreAndForwardManager
import com.offlinemesh.app.domain.repository.ChatRepository
import com.offlinemesh.app.domain.repository.CommunityRepository
import com.offlinemesh.app.domain.repository.IdentityRepository
import com.offlinemesh.app.domain.repository.PeerRepository
import com.offlinemesh.app.domain.usecase.ClearAllMessagesUseCase
import com.offlinemesh.app.domain.usecase.ConnectPeerUseCase
import com.offlinemesh.app.domain.usecase.DeleteConversationUseCase
import com.offlinemesh.app.domain.usecase.DeleteMessageUseCase
import com.offlinemesh.app.domain.usecase.DisconnectPeerUseCase
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.GetUnreadCountUseCase
import com.offlinemesh.app.domain.usecase.MarkConversationAsReadUseCase
import com.offlinemesh.app.domain.usecase.ObserveConnectedPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveDiscoveredPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveKnownPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveMessagesUseCase
import com.offlinemesh.app.domain.usecase.ObserveRecentConversationsUseCase
import com.offlinemesh.app.domain.usecase.RemoveProfilePhotoUseCase
import com.offlinemesh.app.domain.usecase.RetrySendMessageUseCase
import com.offlinemesh.app.domain.usecase.SaveProfilePhotoUseCase
import com.offlinemesh.app.domain.usecase.SearchMessagesUseCase
import com.offlinemesh.app.domain.usecase.SendMessageUseCase
import com.offlinemesh.app.domain.usecase.StartDiscoveryUseCase
import com.offlinemesh.app.domain.usecase.StopDiscoveryUseCase
import com.offlinemesh.app.domain.usecase.ToggleStarMessageUseCase
import com.offlinemesh.app.domain.usecase.UpdateProfileUseCase

interface AppContainer {
    val keystoreManager: AndroidKeystoreManager
    val database: OfflineMeshDatabase
    val transportManager: TransportManager
    val deduplicator: MessageDeduplicator
    val routingManager: RoutingManager
    val storeAndForwardManager: StoreAndForwardManager

    val identityRepository: IdentityRepository
    val peerRepository: PeerRepository
    val chatRepository: ChatRepository
    val communityRepository: CommunityRepository

    // Use Cases
    val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase
    val updateProfileUseCase: UpdateProfileUseCase
    val saveProfilePhotoUseCase: SaveProfilePhotoUseCase
    val removeProfilePhotoUseCase: RemoveProfilePhotoUseCase
    val observeMessagesUseCase: ObserveMessagesUseCase
    val observeRecentConversationsUseCase: ObserveRecentConversationsUseCase
    val sendMessageUseCase: SendMessageUseCase
    val retrySendMessageUseCase: RetrySendMessageUseCase
    val deleteMessageUseCase: DeleteMessageUseCase
    val deleteConversationUseCase: DeleteConversationUseCase
    val clearAllMessagesUseCase: ClearAllMessagesUseCase
    val markConversationAsReadUseCase: MarkConversationAsReadUseCase
    val getUnreadCountUseCase: GetUnreadCountUseCase
    val toggleStarMessageUseCase: ToggleStarMessageUseCase
    val searchMessagesUseCase: SearchMessagesUseCase
    val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase
    val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase
    val connectPeerUseCase: ConnectPeerUseCase
    val disconnectPeerUseCase: DisconnectPeerUseCase
    val startDiscoveryUseCase: StartDiscoveryUseCase
    val stopDiscoveryUseCase: StopDiscoveryUseCase
    val observeKnownPeersUseCase: ObserveKnownPeersUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val keystoreManager: AndroidKeystoreManager by lazy {
        AndroidKeystoreManager()
    }

    override val database: OfflineMeshDatabase by lazy {
        OfflineMeshDatabase.getInstance(context)
    }

    override val deduplicator: MessageDeduplicator by lazy {
        MessageDeduplicator(maxCacheSize = 3000)
    }

    override val routingManager: RoutingManager by lazy {
        InMemoryRoutingManager()
    }

    override val storeAndForwardManager: StoreAndForwardManager by lazy {
        InMemoryStoreAndForwardManager()
    }

    override val transportManager: TransportManager by lazy {
        NearbyConnectionsTransport(context, keystoreManager)
    }

    override val identityRepository: IdentityRepository by lazy {
        IdentityRepositoryImpl(context, keystoreManager)
    }

    override val peerRepository: PeerRepository by lazy {
        PeerRepositoryImpl(transportManager, database.peerDao(), routingManager)
    }

    override val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(
            messageDao = database.messageDao(),
            peerDao = database.peerDao(),
            transportManager = transportManager,
            identityRepository = identityRepository,
            keystoreManager = keystoreManager,
            deduplicator = deduplicator,
            routingManager = routingManager,
            storeAndForwardManager = storeAndForwardManager,
            context = context
        )
    }

    override val communityRepository: CommunityRepository by lazy {
        CommunityRepositoryImpl(database.communityDao())
    }

    // Use Cases
    override val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase by lazy {
        GetOrCreateIdentityUseCase(identityRepository)
    }

    override val updateProfileUseCase: UpdateProfileUseCase by lazy {
        UpdateProfileUseCase(identityRepository)
    }

    override val saveProfilePhotoUseCase: SaveProfilePhotoUseCase by lazy {
        SaveProfilePhotoUseCase(identityRepository)
    }

    override val removeProfilePhotoUseCase: RemoveProfilePhotoUseCase by lazy {
        RemoveProfilePhotoUseCase(identityRepository)
    }

    override val observeMessagesUseCase: ObserveMessagesUseCase by lazy {
        ObserveMessagesUseCase(chatRepository)
    }

    override val observeRecentConversationsUseCase: ObserveRecentConversationsUseCase by lazy {
        ObserveRecentConversationsUseCase(chatRepository)
    }

    override val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(chatRepository)
    }

    override val retrySendMessageUseCase: RetrySendMessageUseCase by lazy {
        RetrySendMessageUseCase(chatRepository)
    }

    override val deleteMessageUseCase: DeleteMessageUseCase by lazy {
        DeleteMessageUseCase(chatRepository)
    }

    override val deleteConversationUseCase: DeleteConversationUseCase by lazy {
        DeleteConversationUseCase(chatRepository)
    }

    override val clearAllMessagesUseCase: ClearAllMessagesUseCase by lazy {
        ClearAllMessagesUseCase(chatRepository)
    }

    override val markConversationAsReadUseCase: MarkConversationAsReadUseCase by lazy {
        MarkConversationAsReadUseCase(chatRepository)
    }

    override val getUnreadCountUseCase: GetUnreadCountUseCase by lazy {
        GetUnreadCountUseCase(chatRepository)
    }

    override val toggleStarMessageUseCase: ToggleStarMessageUseCase by lazy {
        ToggleStarMessageUseCase(chatRepository)
    }

    override val searchMessagesUseCase: SearchMessagesUseCase by lazy {
        SearchMessagesUseCase(chatRepository)
    }

    override val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase by lazy {
        ObserveDiscoveredPeersUseCase(peerRepository)
    }

    override val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase by lazy {
        ObserveConnectedPeersUseCase(peerRepository)
    }

    override val connectPeerUseCase: ConnectPeerUseCase by lazy {
        ConnectPeerUseCase(peerRepository)
    }

    override val disconnectPeerUseCase: DisconnectPeerUseCase by lazy {
        DisconnectPeerUseCase(peerRepository)
    }

    override val startDiscoveryUseCase: StartDiscoveryUseCase by lazy {
        StartDiscoveryUseCase(peerRepository)
    }

    override val stopDiscoveryUseCase: StopDiscoveryUseCase by lazy {
        StopDiscoveryUseCase(peerRepository)
    }

    override val observeKnownPeersUseCase: ObserveKnownPeersUseCase by lazy {
        ObserveKnownPeersUseCase(peerRepository)
    }
}
