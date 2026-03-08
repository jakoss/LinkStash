package pl.jsyty.linkstash.linkstash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

internal const val LINKSTASH_SESSION_DATASTORE_FILE_NAME = "linkstash_session.preferences_pb"
private val bearerTokenKey = stringPreferencesKey("bearer_token")
private val serverUrlKey = stringPreferencesKey("server_url")

internal fun createLinkStashSessionDataStore(producePath: () -> String): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )
}

internal fun createDataStoreSessionStore(dataStore: DataStore<Preferences>): LinkStashSessionStore {
    return DataStoreSessionStore(dataStore)
}

private class DataStoreSessionStore(
    private val dataStore: DataStore<Preferences>
) : LinkStashSessionStore {
    override suspend fun readBearerToken(): String? {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[bearerTokenKey]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun writeBearerToken(token: String) {
        dataStore.edit { preferences ->
            preferences[bearerTokenKey] = token
        }
    }

    override suspend fun readServerUrl(): String? {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[serverUrlKey]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun writeServerUrl(serverUrl: String) {
        dataStore.edit { preferences ->
            preferences[serverUrlKey] = serverUrl
        }
    }

    override suspend fun clearBearerToken() {
        dataStore.edit { preferences ->
            preferences.remove(bearerTokenKey)
        }
    }
}
