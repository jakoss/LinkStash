package pl.jsyty.linkstash.linkstash

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

private object AndroidSessionDataStoreHolder {
    @Volatile
    private var sessionDataStore: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> {
        val appContext = context.applicationContext
        val existing = sessionDataStore
        if (existing != null) {
            return existing
        }

        return synchronized(this) {
            val current = sessionDataStore
            if (current != null) {
                current
            } else {
                PreferenceDataStoreFactory.createWithPath {
                    appContext.filesDir
                        .resolve(LINKSTASH_SESSION_DATASTORE_FILE_NAME)
                        .absolutePath
                        .toPath()
                }.also { created ->
                    sessionDataStore = created
                }
            }
        }
    }
}

fun createLinkStashSessionStore(context: Context): LinkStashSessionStore {
    return createDataStoreSessionStore(
        dataStore = AndroidSessionDataStoreHolder.get(context)
    )
}
