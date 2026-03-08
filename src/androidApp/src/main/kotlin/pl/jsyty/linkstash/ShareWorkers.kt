package pl.jsyty.linkstash

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode

class ShareIntakeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sharedUrls = inputData.getStringArray(ShareSaveWorkScheduler.inputUrlsKey).orEmpty()
        if (sharedUrls.isEmpty()) {
            return Result.success()
        }

        val repository = LinkStashAndroidRepositoryFactory.create(applicationContext)
        return try {
            repository.hydrateSessionToken()
            sharedUrls.forEach { url ->
                repository.enqueuePendingLink(url)
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            repository.close()
        }
    }
}

class PendingQueueSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = LinkStashAndroidRepositoryFactory.create(applicationContext)
        return try {
            repository.hydrateSessionToken()
            if (!repository.hasSessionToken()) {
                return Result.success()
            }

            val spaces = repository.listSpaces()
            repository.flushPendingToDefaultSpace(spaces)
            Result.success()
        } catch (error: ApiException) {
            if (error.error.code == ApiErrorCode.UNAUTHORIZED) {
                repository.clearSession()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            repository.close()
        }
    }
}
