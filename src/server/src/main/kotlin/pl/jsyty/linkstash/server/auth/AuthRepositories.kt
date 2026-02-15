package pl.jsyty.linkstash.server.auth

import java.util.UUID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.jsyty.linkstash.contracts.user.UserDto
import pl.jsyty.linkstash.server.db.OauthStatesTable
import pl.jsyty.linkstash.server.db.RaindropTokensTable
import pl.jsyty.linkstash.server.db.SessionsTable
import pl.jsyty.linkstash.server.db.UsersTable

data class StoredSession(
    val id: String,
    val userId: String,
    val expiresAtEpochSeconds: Long,
    val revokedAtEpochSeconds: Long?
)

data class StoredRaindropTokens(
    val userId: String,
    val accessTokenEncrypted: String,
    val refreshTokenEncrypted: String?,
    val expiresAtEpochSeconds: Long?,
    val scope: String?
)

class OauthStateRepository(private val db: Database) {
    fun create(
        state: String,
        redirectUri: String,
        codeVerifierHash: String?,
        nowEpochSeconds: Long,
        expiresAtEpochSeconds: Long
    ) {
        transaction(db = db) {
            OauthStatesTable.insert {
                it[OauthStatesTable.state] = state
                it[OauthStatesTable.redirectUri] = redirectUri
                it[OauthStatesTable.codeVerifierHash] = codeVerifierHash
                it[createdAtEpochSeconds] = nowEpochSeconds
                it[OauthStatesTable.expiresAtEpochSeconds] = expiresAtEpochSeconds
                it[consumedAtEpochSeconds] = null
            }
        }
    }

    fun consumeIfValid(
        state: String,
        redirectUri: String,
        codeVerifierHash: String?,
        nowEpochSeconds: Long
    ): Boolean {
        return transaction(db = db) {
            val row = OauthStatesTable.selectAll()
                .where { OauthStatesTable.state eq state }
                .limit(1)
                .singleOrNull()
                ?: return@transaction false

            if (row[OauthStatesTable.consumedAtEpochSeconds] != null) return@transaction false
            if (row[OauthStatesTable.expiresAtEpochSeconds] < nowEpochSeconds) return@transaction false
            if (row[OauthStatesTable.redirectUri] != redirectUri) return@transaction false

            val expectedCodeVerifierHash = row[OauthStatesTable.codeVerifierHash]
            if (expectedCodeVerifierHash != null && expectedCodeVerifierHash != codeVerifierHash) return@transaction false

            OauthStatesTable.update({ OauthStatesTable.state eq state }) {
                it[consumedAtEpochSeconds] = nowEpochSeconds
            }

            true
        }
    }
}

class SessionRepository(private val db: Database) {
    fun create(
        userId: String,
        tokenHash: String,
        nowEpochSeconds: Long,
        expiresAtEpochSeconds: Long
    ): StoredSession {
        val sessionId = UUID.randomUUID().toString()

        transaction(db = db) {
            SessionsTable.insert {
                it[id] = sessionId
                it[SessionsTable.userId] = userId
                it[SessionsTable.tokenHash] = tokenHash
                it[createdAtEpochSeconds] = nowEpochSeconds
                it[SessionsTable.expiresAtEpochSeconds] = expiresAtEpochSeconds
                it[lastSeenAtEpochSeconds] = nowEpochSeconds
                it[revokedAtEpochSeconds] = null
            }
        }

        return StoredSession(
            id = sessionId,
            userId = userId,
            expiresAtEpochSeconds = expiresAtEpochSeconds,
            revokedAtEpochSeconds = null
        )
    }

    fun findActiveByTokenHash(tokenHash: String, nowEpochSeconds: Long): StoredSession? {
        return transaction(db = db) {
            val row = SessionsTable.selectAll()
                .where { SessionsTable.tokenHash eq tokenHash }
                .limit(1)
                .singleOrNull()
                ?: return@transaction null

            val revokedAt = row[SessionsTable.revokedAtEpochSeconds]
            val expiresAt = row[SessionsTable.expiresAtEpochSeconds]
            if (revokedAt != null || expiresAt <= nowEpochSeconds) return@transaction null

            val sessionId = row[SessionsTable.id]
            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[lastSeenAtEpochSeconds] = nowEpochSeconds
            }

            StoredSession(
                id = sessionId,
                userId = row[SessionsTable.userId],
                expiresAtEpochSeconds = expiresAt,
                revokedAtEpochSeconds = revokedAt
            )
        }
    }

    fun revokeBySessionId(sessionId: String, nowEpochSeconds: Long): Boolean {
        return transaction(db = db) {
            val row = SessionsTable.selectAll()
                .where { SessionsTable.id eq sessionId }
                .limit(1)
                .singleOrNull()
                ?: return@transaction false

            if (row[SessionsTable.revokedAtEpochSeconds] != null) return@transaction false

            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[revokedAtEpochSeconds] = nowEpochSeconds
            }

            true
        }
    }

    fun revokeAllForUser(userId: String, nowEpochSeconds: Long) {
        transaction(db = db) {
            val rows = SessionsTable.selectAll()
                .where { SessionsTable.userId eq userId }
                .toList()

            rows.forEach { row ->
                if (row[SessionsTable.revokedAtEpochSeconds] == null) {
                    SessionsTable.update({ SessionsTable.id eq row[SessionsTable.id] }) {
                        it[revokedAtEpochSeconds] = nowEpochSeconds
                    }
                }
            }
        }
    }
}

class UserRepository(private val db: Database) {
    fun upsertByRaindropUserId(
        raindropUserId: String,
        displayName: String?,
        nowEpochSeconds: Long
    ): UserDto {
        return transaction(db = db) {
            val existingRow = UsersTable.selectAll()
                .where { UsersTable.raindropUserId eq raindropUserId }
                .limit(1)
                .singleOrNull()

            if (existingRow != null) {
                val userId = existingRow[UsersTable.id]
                UsersTable.update({ UsersTable.id eq userId }) {
                    it[UsersTable.displayName] = displayName
                    it[updatedAtEpochSeconds] = nowEpochSeconds
                }

                UserDto(id = userId, displayName = displayName)
            } else {
                val userId = UUID.randomUUID().toString()
                UsersTable.insert {
                    it[id] = userId
                    it[UsersTable.raindropUserId] = raindropUserId
                    it[UsersTable.displayName] = displayName
                    it[createdAtEpochSeconds] = nowEpochSeconds
                    it[updatedAtEpochSeconds] = nowEpochSeconds
                }

                UserDto(id = userId, displayName = displayName)
            }
        }
    }

    fun updateDisplayName(userId: String, displayName: String?, nowEpochSeconds: Long): UserDto? {
        return transaction(db = db) {
            val existingRow = UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .limit(1)
                .singleOrNull()
                ?: return@transaction null

            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.displayName] = displayName
                it[updatedAtEpochSeconds] = nowEpochSeconds
            }

            UserDto(
                id = existingRow[UsersTable.id],
                displayName = displayName
            )
        }
    }

    fun findById(userId: String): UserDto? {
        return transaction(db = db) {
            UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    UserDto(
                        id = row[UsersTable.id],
                        displayName = row[UsersTable.displayName]
                    )
                }
        }
    }
}

class RaindropTokenRepository(private val db: Database) {
    fun upsert(
        userId: String,
        accessTokenEncrypted: String,
        refreshTokenEncrypted: String?,
        expiresAtEpochSeconds: Long?,
        scope: String?,
        nowEpochSeconds: Long
    ) {
        transaction(db = db) {
            val existingRow = RaindropTokensTable.selectAll()
                .where { RaindropTokensTable.userId eq userId }
                .limit(1)
                .singleOrNull()

            if (existingRow == null) {
                RaindropTokensTable.insert {
                    it[RaindropTokensTable.userId] = userId
                    it[RaindropTokensTable.accessTokenEncrypted] = accessTokenEncrypted
                    it[RaindropTokensTable.refreshTokenEncrypted] = refreshTokenEncrypted
                    it[RaindropTokensTable.expiresAtEpochSeconds] = expiresAtEpochSeconds
                    it[RaindropTokensTable.scope] = scope
                    it[createdAtEpochSeconds] = nowEpochSeconds
                    it[updatedAtEpochSeconds] = nowEpochSeconds
                }
            } else {
                RaindropTokensTable.update({ RaindropTokensTable.userId eq userId }) {
                    it[RaindropTokensTable.accessTokenEncrypted] = accessTokenEncrypted
                    it[RaindropTokensTable.refreshTokenEncrypted] = refreshTokenEncrypted
                    it[RaindropTokensTable.expiresAtEpochSeconds] = expiresAtEpochSeconds
                    it[RaindropTokensTable.scope] = scope
                    it[updatedAtEpochSeconds] = nowEpochSeconds
                }
            }
        }
    }

    fun findByUserId(userId: String): StoredRaindropTokens? {
        return transaction(db = db) {
            RaindropTokensTable.selectAll()
                .where { RaindropTokensTable.userId eq userId }
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    StoredRaindropTokens(
                        userId = row[RaindropTokensTable.userId],
                        accessTokenEncrypted = row[RaindropTokensTable.accessTokenEncrypted],
                        refreshTokenEncrypted = row[RaindropTokensTable.refreshTokenEncrypted],
                        expiresAtEpochSeconds = row[RaindropTokensTable.expiresAtEpochSeconds],
                        scope = row[RaindropTokensTable.scope]
                    )
                }
        }
    }

    fun deleteForUser(userId: String) {
        transaction(db = db) {
            RaindropTokensTable.deleteWhere { RaindropTokensTable.userId eq userId }
        }
    }
}
