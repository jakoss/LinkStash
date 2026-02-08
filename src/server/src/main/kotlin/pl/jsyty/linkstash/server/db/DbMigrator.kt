package pl.jsyty.linkstash.server.db

import java.time.Instant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import pl.jsyty.linkstash.server.config.AppConfig

class DbMigrator(
    private val db: Database,
    private val config: AppConfig
) {
    private data class SchemaMigration(
        val version: Int,
        val name: String,
        val apply: () -> Unit
    )

    private val migrations = listOf(
        SchemaMigration(
            version = 1,
            name = "bootstrap_schema_v1",
            apply = { bootstrapSchemaV1() }
        )
    )

    fun migrate() {
        transaction(db = db) {
            SchemaUtils.createMissingTablesAndColumns(SchemaMigrationsTable)
        }

        migrations.forEach { migration ->
            val alreadyApplied = isMigrationApplied(migration.version)
            if (alreadyApplied) return@forEach

            migration.apply()
            recordAppliedMigration(migration)
        }
    }

    private fun isMigrationApplied(version: Int): Boolean {
        return transaction(db = db) {
            SchemaMigrationsTable
                .selectAll()
                .where { SchemaMigrationsTable.version eq version }
                .limit(1)
                .singleOrNull() != null
        }
    }

    private fun recordAppliedMigration(migration: SchemaMigration) {
        transaction(db = db) {
            SchemaMigrationsTable.insert {
                it[version] = migration.version
                it[name] = migration.name
                it[appliedAtEpochSeconds] = Instant.now().epochSecond
            }
        }
    }

    private fun bootstrapSchemaV1() {
        transaction(db = db) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                RaindropTokensTable,
                SessionsTable,
                LinkStashConfigTable,
                OauthStatesTable
            )

            val hasConfig = LinkStashConfigTable
                .selectAll()
                .limit(1)
                .singleOrNull() != null

            if (!hasConfig) {
                val now = Instant.now().epochSecond
                LinkStashConfigTable.insert {
                    it[id] = 1
                    it[rootCollectionTitle] = config.linkstashRootCollectionTitle
                    it[defaultSpaceTitle] = config.linkstashDefaultSpaceTitle
                    it[createdAtEpochSeconds] = now
                    it[updatedAtEpochSeconds] = now
                }
            }
        }
    }
}
