package pl.jsyty.linkstash.server.db

import org.jetbrains.exposed.v1.jdbc.Database
import pl.jsyty.linkstash.server.config.AppConfig

object DatabaseFactory {
    fun connectAndMigrate(config: AppConfig): Database {
        val database = Database.connect(
            url = config.dbUrl,
            driver = "org.sqlite.JDBC"
        )

        DbMigrator(database, config).migrate()
        return database
    }
}
