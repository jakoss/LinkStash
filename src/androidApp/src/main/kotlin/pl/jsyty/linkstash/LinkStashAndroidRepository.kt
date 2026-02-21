package pl.jsyty.linkstash

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import pl.jsyty.linkstash.linkstash.LinkStashClientConfig
import pl.jsyty.linkstash.linkstash.LinkStashRepository
import pl.jsyty.linkstash.linkstash.LinkStashSessionStore

object AndroidAppConfig {
    const val apiBaseUrl = "http://10.0.2.2:8080/v1/"
    const val defaultSpaceTitle = "Inbox"
}

private val Context.linkStashSessionDataStore by preferencesDataStore(name = "linkstash_session")

class DataStoreSessionStore(
    private val context: Context
) : LinkStashSessionStore {
    override suspend fun readBearerToken(): String? {
        return context.linkStashSessionDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[KEY_BEARER_TOKEN]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun writeBearerToken(token: String) {
        context.linkStashSessionDataStore.edit { preferences ->
            preferences[KEY_BEARER_TOKEN] = token
        }
    }

    override suspend fun clearBearerToken() {
        context.linkStashSessionDataStore.edit { preferences ->
            preferences.remove(KEY_BEARER_TOKEN)
        }
    }

    private companion object {
        val KEY_BEARER_TOKEN = stringPreferencesKey("bearer_token")
    }
}

object LinkStashAndroidRepositoryFactory {
    fun create(context: Context): LinkStashRepository {
        val appContext = context.applicationContext
        return LinkStashRepository(
            sessionStore = DataStoreSessionStore(appContext),
            pendingQueueStore = PendingLinkQueueStoreFactory.create(appContext),
            config = LinkStashClientConfig(
                apiBaseUrl = AndroidAppConfig.apiBaseUrl,
                defaultSpaceTitle = AndroidAppConfig.defaultSpaceTitle
            )
        )
    }
}
