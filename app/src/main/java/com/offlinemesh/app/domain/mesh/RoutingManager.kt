package com.offlinemesh.app.domain.mesh

import kotlinx.serialization.Serializable

@Serializable
data class RouteEntry(
    val destinationUserId: String,
    val nextHopEndpointId: String,
    val nextHopUserId: String,
    val hopCount: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val displayName: String? = null,
    val publicKey: String? = null,
    val avatarColorHex: String? = null
)

interface RoutingManager {
    fun addOrUpdateRoute(
        destinationUserId: String,
        nextHopEndpointId: String,
        nextHopUserId: String,
        hopCount: Int,
        displayName: String? = null,
        publicKey: String? = null,
        avatarColorHex: String? = null
    ): Boolean

    fun getNextHopEndpoint(destinationUserId: String): String?
    fun getRoute(destinationUserId: String): RouteEntry?
    fun removeRoutesForEndpoint(endpointId: String)
    fun removeRoute(destinationUserId: String)
    fun getAllRoutes(): List<RouteEntry>
    fun getReachableMeshNodes(): List<MeshNode>
    fun getRoutesForAdvertisement(excludeEndpointId: String? = null): List<RouteEntry>
    fun expireStaleRoutes(maxAgeMillis: Long = ROUTE_EXPIRATION_MS)
    fun clear()

    companion object {
        const val ROUTE_EXPIRATION_MS = 5 * 60 * 1000L // 5 minutes
    }
}

class InMemoryRoutingManager : RoutingManager {
    private val routes = mutableMapOf<String, RouteEntry>()

    @Synchronized
    override fun addOrUpdateRoute(
        destinationUserId: String,
        nextHopEndpointId: String,
        nextHopUserId: String,
        hopCount: Int,
        displayName: String?,
        publicKey: String?,
        avatarColorHex: String?
    ): Boolean {
        val existing = routes[destinationUserId]
        val now = System.currentTimeMillis()

        // Update if:
        // 1. No existing route
        // 2. Existing route is through the same next-hop (refresh)
        // 3. Shorter path discovered (fewer hops)
        // 4. Existing route has expired (> 5 minutes old)
        val shouldUpdate = existing == null ||
                existing.nextHopEndpointId == nextHopEndpointId ||
                hopCount < existing.hopCount ||
                (now - existing.lastUpdated > RoutingManager.ROUTE_EXPIRATION_MS)

        if (shouldUpdate) {
            routes[destinationUserId] = RouteEntry(
                destinationUserId = destinationUserId,
                nextHopEndpointId = nextHopEndpointId,
                nextHopUserId = nextHopUserId,
                hopCount = hopCount,
                lastUpdated = now,
                displayName = displayName ?: existing?.displayName,
                publicKey = publicKey ?: existing?.publicKey,
                avatarColorHex = avatarColorHex ?: existing?.avatarColorHex
            )
            return true
        }
        return false
    }

    @Synchronized
    override fun getNextHopEndpoint(destinationUserId: String): String? {
        val entry = routes[destinationUserId] ?: return null
        if (System.currentTimeMillis() - entry.lastUpdated > RoutingManager.ROUTE_EXPIRATION_MS) {
            routes.remove(destinationUserId)
            return null
        }
        return entry.nextHopEndpointId
    }

    @Synchronized
    override fun getRoute(destinationUserId: String): RouteEntry? {
        val entry = routes[destinationUserId] ?: return null
        if (System.currentTimeMillis() - entry.lastUpdated > RoutingManager.ROUTE_EXPIRATION_MS) {
            routes.remove(destinationUserId)
            return null
        }
        return entry
    }

    @Synchronized
    override fun removeRoutesForEndpoint(endpointId: String) {
        routes.entries.removeIf { it.value.nextHopEndpointId == endpointId }
    }

    @Synchronized
    override fun removeRoute(destinationUserId: String) {
        routes.remove(destinationUserId)
    }

    @Synchronized
    override fun getAllRoutes(): List<RouteEntry> {
        val now = System.currentTimeMillis()
        routes.entries.removeIf { now - it.value.lastUpdated > RoutingManager.ROUTE_EXPIRATION_MS }
        return routes.values.toList()
    }

    @Synchronized
    override fun getReachableMeshNodes(): List<MeshNode> {
        val now = System.currentTimeMillis()
        routes.entries.removeIf { now - it.value.lastUpdated > RoutingManager.ROUTE_EXPIRATION_MS }
        return routes.values.map { entry ->
            MeshNode(
                userId = entry.destinationUserId,
                displayName = entry.displayName ?: "Peer ${entry.destinationUserId.takeLast(4)}",
                publicKey = entry.publicKey ?: "",
                directEndpointId = if (entry.hopCount == 1) entry.nextHopEndpointId else null,
                isDirectPeer = entry.hopCount == 1,
                hopCount = entry.hopCount,
                lastSeen = entry.lastUpdated,
                avatarColorHex = entry.avatarColorHex ?: "#3B82F6"
            )
        }
    }

    @Synchronized
    override fun getRoutesForAdvertisement(excludeEndpointId: String?): List<RouteEntry> {
        val now = System.currentTimeMillis()
        routes.entries.removeIf { now - it.value.lastUpdated > RoutingManager.ROUTE_EXPIRATION_MS }
        // Split-horizon rule: Do not advertise routes back to the endpoint from which they were learned
        return if (excludeEndpointId == null) {
            routes.values.toList()
        } else {
            routes.values.filterNot { it.nextHopEndpointId == excludeEndpointId }
        }
    }

    @Synchronized
    override fun expireStaleRoutes(maxAgeMillis: Long) {
        val now = System.currentTimeMillis()
        routes.entries.removeIf { (now - it.value.lastUpdated) >= maxAgeMillis }
    }

    @Synchronized
    override fun clear() {
        routes.clear()
    }
}
