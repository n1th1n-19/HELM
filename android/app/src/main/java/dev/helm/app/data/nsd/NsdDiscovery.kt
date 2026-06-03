package dev.helm.app.data.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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

        // Serial resolve queue: NsdManager only allows one in-flight resolveService call
        // at a time (pre-API 34). Funnelling all resolve requests through a Channel
        // ensures they execute one at a time and never race.
        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        launch {
            for (serviceInfo in resolveQueue) {
                suspendCancellableCoroutine { cont ->
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "resolve failed for ${info.serviceName}: error $errorCode")
                            cont.resume(Unit)
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: run {
                                Log.w(TAG, "resolved ${info.serviceName} but host is null")
                                cont.resume(Unit)
                                return
                            }
                            Log.d(TAG, "resolved: ${info.serviceName} -> $host:${info.port}")
                            found[info.serviceName] = DiscoveredAgent(
                                name = info.serviceName,
                                host = host,
                                port = info.port,
                            )
                            trySend(found.values.toList())
                            cont.resume(Unit)
                        }
                    })
                }
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
                resolveQueue.trySend(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                Log.d(TAG, "lost service: ${info.serviceName}")
                found.remove(info.serviceName)
                trySend(found.values.toList())
            }
        }

        nsdManager.discoverServices("_helm._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            resolveQueue.close()
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {}
        }
    }
}
