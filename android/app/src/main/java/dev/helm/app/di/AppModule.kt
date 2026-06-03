package dev.helm.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.connectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
        engine {
            // Disable connection pooling — stale pooled connections cause
            // "unexpected end of stream" on WebSocket upgrade after agent restart.
            preconfigured = OkHttpClient.Builder()
                .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.connectionDataStore
}
