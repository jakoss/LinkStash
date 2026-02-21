package pl.jsyty.linkstash.server.config

data class AppConfig(
    val host: String,
    val port: Int,
    val dbUrl: String,
    val sessionSecret: String,
    val sessionCookieName: String,
    val sessionCookieSecure: Boolean,
    val sessionTtlSeconds: Long,
    val tokenHashingSecret: String,
    val raindropTokenEncryptionKey: String,
    val raindropApiBaseUrl: String,
    val linkstashRootCollectionTitle: String,
    val linkstashDefaultSpaceTitle: String,
    val corsAllowedOrigins: List<String>
) {
    companion object {
        private val defaultCorsOrigins = listOf(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:8080",
            "http://127.0.0.1:8080"
        )

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            return AppConfig(
                host = env["HOST"]?.ifBlank { null } ?: "0.0.0.0",
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                dbUrl = requiredEnv(env, "DB_URL"),
                sessionSecret = requiredEnv(env, "SESSION_SECRET"),
                sessionCookieName = env["SESSION_COOKIE_NAME"]?.ifBlank { null } ?: "linkstash_session",
                sessionCookieSecure = parseBoolean(env["SESSION_COOKIE_SECURE"]) ?: false,
                sessionTtlSeconds = env["SESSION_TTL_SECONDS"]?.toLongOrNull() ?: 60L * 60L * 24L * 30L,
                tokenHashingSecret = requiredEnv(env, "TOKEN_HASHING_SECRET"),
                raindropTokenEncryptionKey = requiredEnv(env, "RAINDROP_TOKEN_ENCRYPTION_KEY"),
                raindropApiBaseUrl = env["RAINDROP_API_BASE_URL"]?.ifBlank { null }
                    ?: "https://api.raindrop.io/rest/v1",
                linkstashRootCollectionTitle = env["LINKSTASH_ROOT_COLLECTION_TITLE"]?.ifBlank { null } ?: "LinkStash",
                linkstashDefaultSpaceTitle = env["LINKSTASH_DEFAULT_SPACE_TITLE"]?.ifBlank { null } ?: "Inbox",
                corsAllowedOrigins = parseCorsOrigins(env["CORS_ALLOWED_ORIGINS"])
            )
        }

        private fun requiredEnv(env: Map<String, String>, name: String): String {
            return env[name]?.ifBlank { null }
                ?: error("Missing required environment variable: $name")
        }

        private fun parseCorsOrigins(rawValue: String?): List<String> {
            if (rawValue.isNullOrBlank()) return defaultCorsOrigins

            return rawValue.split(',')
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { defaultCorsOrigins }
        }

        private fun parseBoolean(value: String?): Boolean? {
            return when (value?.trim()?.lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> null
            }
        }
    }
}
