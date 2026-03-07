package pl.jsyty.linkstash.linkstash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private val iosSessionDataStore: DataStore<Preferences> by lazy {
    createLinkStashSessionDataStore {
        iosSessionDataStorePath(LINKSTASH_SESSION_DATASTORE_FILE_NAME)
    }
}

fun createLinkStashSessionStore(): LinkStashSessionStore {
    return createDataStoreSessionStore(iosSessionDataStore)
}

private fun iosSessionDataStorePath(fileName: String): String {
    val documentsDirectory = NSFileManager.defaultManager
        .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        .firstOrNull() as? NSURL
        ?: error("Could not resolve iOS documents directory")

    return "${documentsDirectory.path}/$fileName"
}
