package dev.helm.app.data.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HelmNsd"

data class DiscoveredAgent(
    val name: String,
    val host: String,
    val port: Int,
)

@Singleton
class NsdDiscovery @Inject constructor() {

    fun discoverAgents(context: Context): Flow<List<DiscoveredAgent>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = mutableMapOf<String, DiscoveredAgent>()

        fun makeResolveListener(): NsdManager.ResolveListener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed for ${info.serviceName}: error $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: run {
                        Log.w(TAG, "resolved ${info.serviceName} but host is null")
                        return
                    }
                    Log.d(TAG, "resolved: ${info.serviceName} -> $host:${info.port}")
                    found[info.serviceName] = DiscoveredAgent(
                        name = info.serviceName,
                        host = host,
                        port = info.port,
                    )
                    trySend(found.values.toList())
                }
            }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "discovery start failed: $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery stop failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "discovery stopped for $serviceType")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                Log.d(TAG, "found service: ${info.serviceName}")
                // Create a fresh listener per resolve — Android rejects listener reuse.
                nsdManager.resolveService(info, makeResolveListener())
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                Log.d(TAG, "lost service: ${info.serviceName}")
                found.remove(info.serviceName)
                trySend(found.values.toList())
            }
        }

        nsdManager.discoverServices("_helm._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {}
        }
    }
}
