package pl.jsyty.linkstash.server.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {
    val id = varchar("id", 64)
    val raindropUserId = varchar("raindrop_user_id", 64).nullable().uniqueIndex()
    val displayName = varchar("display_name", 255).nullable()
    val createdAtEpochSeconds = long("created_at_epoch_seconds")
    val updatedAtEpochSeconds = long("updated_at_epoch_seconds")

    override val primaryKey = PrimaryKey(id)
}

object RaindropTokensTable : Table("raindrop_tokens") {
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val accessTokenEncrypted = text("access_token_encrypted")
    val refreshTokenEncrypted = text("refresh_token_encrypted").nullable()
    val expiresAtEpochSeconds = long("expires_at_epoch_seconds").nullable()
    val scope = text("scope").nullable()
    val createdAtEpochSeconds = long("created_at_epoch_seconds")
    val updatedAtEpochSeconds = long("updated_at_epoch_seconds")
}

object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val createdAtEpochSeconds = long("created_at_epoch_seconds")
    val expiresAtEpochSeconds = long("expires_at_epoch_seconds")
    val lastSeenAtEpochSeconds = long("last_seen_at_epoch_seconds").nullable()
    val revokedAtEpochSeconds = long("revoked_at_epoch_seconds").nullable()

    override val primaryKey = PrimaryKey(id)
}

object LinkStashConfigTable : Table("linkstash_config") {
    val id = integer("id")
    val rootCollectionTitle = varchar("root_collection_title", 255)
    val defaultSpaceTitle = varchar("default_space_title", 255)
    val createdAtEpochSeconds = long("created_at_epoch_seconds")
    val updatedAtEpochSeconds = long("updated_at_epoch_seconds")

    override val primaryKey = PrimaryKey(id)
}

object OauthStatesTable : Table("oauth_states") {
    val state = varchar("state", 128)
    val codeVerifierHash = varchar("code_verifier_hash", 255).nullable()
    val redirectUri = text("redirect_uri")
    val createdAtEpochSeconds = long("created_at_epoch_seconds")
    val expiresAtEpochSeconds = long("expires_at_epoch_seconds")
    val consumedAtEpochSeconds = long("consumed_at_epoch_seconds").nullable()

    override val primaryKey = PrimaryKey(state)
}

object SchemaMigrationsTable : Table("schema_migrations") {
    val version = integer("version")
    val name = varchar("name", 255)
    val appliedAtEpochSeconds = long("applied_at_epoch_seconds")

    override val primaryKey = PrimaryKey(version)
}
