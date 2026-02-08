package pl.jsyty.linkstash.server.config

data class AppConfig(
    val host: String,
    val port: Int,
    val dbUrl: String,
    val sessionSecret: String,
    val tokenHashingSecret: String,
    val raindropTokenEncryptionKey: String,
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
                tokenHashingSecret = requiredEnv(env, "TOKEN_HASHING_SECRET"),
                raindropTokenEncryptionKey = requiredEnv(env, "RAINDROP_TOKEN_ENCRYPTION_KEY"),
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
    }
}
