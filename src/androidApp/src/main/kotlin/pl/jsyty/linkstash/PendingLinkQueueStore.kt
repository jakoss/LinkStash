package pl.jsyty.linkstash

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import pl.jsyty.linkstash.linkstash.LinkStashPendingQueueStore
import pl.jsyty.linkstash.linkstash.PendingQueuedLink

@Entity(
    tableName = "pending_links",
    indices = [Index(value = ["url"], unique = true)]
)
data class PendingLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    @ColumnInfo(name = "created_at_epoch_seconds")
    val createdAtEpochSeconds: Long
)

@Dao
interface PendingLinkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingLinkEntity): Long

    @Query(
        """
        SELECT id, url, created_at_epoch_seconds
        FROM pending_links
        ORDER BY created_at_epoch_seconds ASC
        LIMIT :limit
        """
    )
    suspend fun listOldest(limit: Int): List<PendingLinkEntity>

    @Query("DELETE FROM pending_links WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_links")
    suspend fun count(): Int
}

@Database(
    entities = [PendingLinkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PendingLinkDatabase : RoomDatabase() {
    abstract fun pendingLinkDao(): PendingLinkDao
}

class RoomPendingLinkQueueStore(
    private val pendingLinkDao: PendingLinkDao
) : LinkStashPendingQueueStore {
    override suspend fun enqueue(url: String): Boolean {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return false

        val insertedId = pendingLinkDao.insert(
            PendingLinkEntity(
                url = normalizedUrl,
                createdAtEpochSeconds = System.currentTimeMillis() / 1000L
            )
        )

        return insertedId != -1L
    }

    override suspend fun listOldest(limit: Int): List<PendingQueuedLink> {
        return pendingLinkDao.listOldest(limit).map { entity ->
            PendingQueuedLink(
                id = entity.id,
                url = entity.url,
                createdAtEpochSeconds = entity.createdAtEpochSeconds
            )
        }
    }

    override suspend fun deleteById(id: Long) {
        pendingLinkDao.deleteById(id)
    }

    override suspend fun count(): Int {
        return pendingLinkDao.count()
    }
}

object PendingLinkQueueStoreFactory {
    fun create(context: Context): RoomPendingLinkQueueStore {
        val database = Room.databaseBuilder(
            context.applicationContext,
            PendingLinkDatabase::class.java,
            "linkstash_pending_links.db"
        ).build()

        return RoomPendingLinkQueueStore(database.pendingLinkDao())
    }
}
