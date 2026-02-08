package pl.jsyty.linkstash.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import pl.jsyty.linkstash.server.config.AppConfig

fun main() {
    val config = AppConfig.fromEnv()

    embeddedServer(
        factory = Netty,
        host = config.host,
        port = config.port
    ) {
        linkStashModule(config)
    }.start(wait = true)
}
