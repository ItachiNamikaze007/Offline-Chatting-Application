package com.offlinemesh.app

import android.app.Application
import com.offlinemesh.app.data.transport.nearby.NearbyConnectionsTransport
import com.offlinemesh.app.di.AppContainer
import com.offlinemesh.app.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OfflineMeshApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Initialize user identity and link with transport layer
        applicationScope.launch {
            val identity = container.getOrCreateIdentityUseCase()
            (container.transportManager as? NearbyConnectionsTransport)?.setLocalIdentity(identity)
        }
    }
}
