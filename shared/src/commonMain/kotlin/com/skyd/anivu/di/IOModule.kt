package com.skyd.anivu.di

import com.skyd.anivu.ext.getOrDefault
import com.skyd.anivu.model.preference.behavior.LoadNetImageOnWifiOnlyPreference
import com.skyd.anivu.model.preference.dataStore
import com.skyd.anivu.util.isWifiAvailable
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.IOException

val ioModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(Logging) {
                logger = object : Logger {
                    private val log = co.touchlab.kermit.Logger.withTag("Ktor")
                    override fun log(message: String) = log.v(message)
                }
                level = LogLevel.INFO
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
            install(ContentNegotiation) {
                json(get())
            }
        }
        config
    }
    single {
        HttpClient(get<HttpClientConfig<*>.() -> Unit>())
    }
    single(named("coil")) {
        HttpClient {
            get<HttpClientConfig<*>.() -> Unit>()
            install(
                createClientPlugin("CoilPlugin") {
                    onRequest { request, _ ->
                        val loadNetImageOnWifiOnly =
                            dataStore.getOrDefault(LoadNetImageOnWifiOnlyPreference)
                        if (loadNetImageOnWifiOnly && isWifiAvailable()) {
                            throw IOException("Not on Wi-Fi; network load denied.")
                        }
                        request.headers.append("Cache-Control", "max-age=31536000,public")
                    }
                }
            )
        }
    }
}