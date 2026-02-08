package pl.jsyty.linkstash.contracts

import kotlinx.serialization.json.Json

object LinkStashJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
