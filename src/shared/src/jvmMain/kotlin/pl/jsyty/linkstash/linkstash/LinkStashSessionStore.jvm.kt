package pl.jsyty.linkstash.linkstash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

private val jvmSessionDataStore: DataStore<Preferences> by lazy {
    createLinkStashSessionDataStore {
        jvmSessionDataStorePath(LINKSTASH_SESSION_DATASTORE_FILE_NAME)
    }
}

fun createLinkStashSessionStore(): LinkStashSessionStore {
    return createDataStoreSessionStore(jvmSessionDataStore)
}

private fun jvmSessionDataStorePath(fileName: String): String {
    val storageDirectory = File(System.getProperty("user.home"), ".linkstash")
    if (!storageDirectory.exists()) {
        storageDirectory.mkdirs()
    }

    return File(storageDirectory, fileName).absolutePath
}
