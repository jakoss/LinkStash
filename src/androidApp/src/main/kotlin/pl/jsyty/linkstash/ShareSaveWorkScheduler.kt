package pl.jsyty.linkstash

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object ShareSaveWorkScheduler {
    const val uniqueWorkName = "share-save-pipeline"
    const val shareIntakeTag = "share-intake"
    const val pendingLinkSyncTag = "pending-link-sync"
    const val inputUrlsKey = "sharedUrls"

    fun enqueue(context: Context, sharedUrls: List<String>) {
        require(sharedUrls.isNotEmpty()) { "sharedUrls must not be empty" }

        val intakeRequest = OneTimeWorkRequestBuilder<ShareIntakeWorker>()
            .setInputData(
                Data.Builder()
                    .putStringArray(inputUrlsKey, sharedUrls.toTypedArray())
                    .build()
            )
            .addTag(shareIntakeTag)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<PendingQueueSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(pendingLinkSyncTag)
            .build()

        WorkManager.getInstance(context)
            .beginUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                intakeRequest
            )
            .then(syncRequest)
            .enqueue()
    }
}
