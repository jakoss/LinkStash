package pl.jsyty.linkstash

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.jsyty.linkstash.linkstash.createPendingLinkQueueStore

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val pendingQueueStore by lazy { createPendingLinkQueueStore(context) }

    @Before
    fun setUp() {
        clearWorkQueue()
    }

    @After
    fun tearDown() {
        clearWorkQueue()
    }

    @Test
    fun singleSharedUrlEnqueuesTheShareSavePipeline() {
        launchShareActivity(
            Intent(context, ShareReceiverActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com/one")
            }
        )

        val workInfos = waitForWorkInfos(1)
        assertTrue(workInfos.any { it.tags.contains(ShareSaveWorkScheduler.shareIntakeTag) })

        val queuedUrls = waitForQueuedUrls(1)
        assertEquals(listOf("https://example.com/one"), queuedUrls)
    }

    @Test
    fun multipleSharedUrlsEnqueueOneIntakeWorkWithAllUrls() {
        launchShareActivity(
            Intent(context, ShareReceiverActivity::class.java).apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "https://example.com/one https://example.com/two"
                )
            }
        )

        val workInfos = waitForWorkInfos(1)
        assertTrue(workInfos.any { it.tags.contains(ShareSaveWorkScheduler.shareIntakeTag) })

        val queuedUrls = waitForQueuedUrls(2)
        assertEquals(
            listOf("https://example.com/one", "https://example.com/two"),
            queuedUrls
        )
    }

    @Test
    fun shareWithNoUrlEnqueuesNoWork() {
        launchShareActivity(
            Intent(context, ShareReceiverActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "hello world")
            }
        )

        Thread.sleep(250)
        val workInfos = workManager.getWorkInfosForUniqueWork(ShareSaveWorkScheduler.uniqueWorkName)
            .get(5, TimeUnit.SECONDS)
        assertTrue(workInfos.isEmpty())
    }

    private fun launchShareActivity(intent: Intent) {
        ActivityScenario.launch<ShareReceiverActivity>(intent).use {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    private fun waitForWorkInfos(minCount: Int): List<WorkInfo> {
        return waitUntil {
            val workInfos = workManager.getWorkInfosForUniqueWork(ShareSaveWorkScheduler.uniqueWorkName)
                .get(5, TimeUnit.SECONDS)
            if (workInfos.size >= minCount) workInfos else null
        }
    }

    private fun waitForQueuedUrls(expectedCount: Int): List<String> {
        return waitUntil {
            val queuedUrls = runBlocking {
                pendingQueueStore.listOldest(limit = expectedCount).map { it.url }
            }
            if (queuedUrls.size == expectedCount) queuedUrls else null
        }
    }

    private fun clearWorkQueue() {
        workManager.cancelAllWork().result.get(5, TimeUnit.SECONDS)
        workManager.pruneWork().result.get(5, TimeUnit.SECONDS)

        val workInfos = workManager.getWorkInfosForUniqueWork(ShareSaveWorkScheduler.uniqueWorkName)
            .get(5, TimeUnit.SECONDS)
        assertEquals(emptyList<WorkInfo>(), workInfos)
        runBlocking {
            pendingQueueStore.listOldest(limit = 500).forEach { pendingQueueStore.deleteById(it.id) }
        }
    }

    private fun <T> waitUntil(block: Callable<T?>): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)

        while (System.nanoTime() < deadline) {
            val value = block.call()
            if (value != null) {
                return value
            }
            Thread.sleep(100)
        }

        error("Timed out waiting for asynchronous state")
    }
}
