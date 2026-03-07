package pl.jsyty.linkstash.linkstash

import android.content.Context
import androidx.room.Room

fun createPendingLinkQueueStore(context: Context): LinkStashPendingQueueStore {
    val database = Room.databaseBuilder(
        context.applicationContext,
        PendingLinkDatabase::class.java,
        "linkstash_pending_links.db"
    ).build()

    return RoomPendingLinkQueueStore(database.pendingLinkDao())
}
