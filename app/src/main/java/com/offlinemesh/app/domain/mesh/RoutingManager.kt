package com.offlinemesh.app.domain.mesh

data class RouteEntry(
    val destinationUserId: String,
    val nextHopEndpointId: String,
    val hopCount: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

interface RoutingManager {
    fun addOrUpdateRoute(destinationUserId: String, nextHopEndpointId: String, hopCount: Int)
    fun getNextHop(destinationUserId: String): String?
    fun removeRoutesForEndpoint(endpointId: String)
    fun getAllRoutes(): List<RouteEntry>
    fun clear()
}

class InMemoryRoutingManager : RoutingManager {
    private val routes = mutableMapOf<String, RouteEntry>()

    @Synchronized
    override fun addOrUpdateRoute(destinationUserId: String, nextHopEndpointId: String, hopCount: Int) {
        val existing = routes[destinationUserId]
        if (existing == null || hopCount <= existing.hopCount || System.currentTimeMillis() - existing.lastUpdated > 60_000) {
            routes[destinationUserId] = RouteEntry(
                destinationUserId = destinationUserId,
                nextHopEndpointId = nextHopEndpointId,
                hopCount = hopCount,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    @Synchronized
    override fun getNextHop(destinationUserId: String): String? {
        return routes[destinationUserId]?.nextHopEndpointId
    }

    @Synchronized
    override fun removeRoutesForEndpoint(endpointId: String) {
        routes.entries.removeIf { it.value.nextHopEndpointId == endpointId }
    }

    @Synchronized
    override fun getAllRoutes(): List<RouteEntry> {
        return routes.values.toList()
    }

    @Synchronized
    override fun clear() {
        routes.clear()
    }
}
