package pl.jsyty.linkstash.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.UUID
import org.slf4j.event.Level
import pl.jsyty.linkstash.contracts.LinkStashJson
import pl.jsyty.linkstash.server.auth.configureAuthModule
import pl.jsyty.linkstash.server.config.AppConfig
import pl.jsyty.linkstash.server.db.DatabaseFactory

fun Application.linkStashModule(config: AppConfig = AppConfig.fromEnv()) {
    val database = DatabaseFactory.connectAndMigrate(config)

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { callId -> callId.isNotBlank() && callId.length <= 200 }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
        mdc("requestId") { call -> call.callId }
    }

    install(ContentNegotiation) {
        json(LinkStashJson.instance)
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.XRequestId)
        allowCredentials = true
        config.corsAllowedOrigins.forEach { origin ->
            val trimmed = origin.trim().removeSuffix("/")
            val scheme = when {
                trimmed.startsWith("https://") -> "https"
                trimmed.startsWith("http://") -> "http"
                else -> "http"
            }
            val host = trimmed.removePrefix("https://").removePrefix("http://")
            if (host.isNotBlank()) {
                allowHost(host, schemes = listOf(scheme))
            }
        }
    }

    configureAuthModule(config, database)

    routing {
        get("/healthz") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}
