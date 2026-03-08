package pl.jsyty.linkstash

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUrls = intent?.let { SharedUrlExtractor.extract(it, applicationContext) }.orEmpty()
        if (sharedUrls.isEmpty()) {
            showToastAndFinish(getString(R.string.share_save_no_link_found))
            return
        }

        val toastMessage = if (sharedUrls.size == 1) {
            getString(R.string.share_save_success_single)
        } else {
            getString(R.string.share_save_success_multiple, sharedUrls.size)
        }

        try {
            ShareSaveWorkScheduler.enqueue(applicationContext, sharedUrls)
            showToastAndFinish(toastMessage)
        } catch (_: Throwable) {
            showToastAndFinish(getString(R.string.share_save_failure))
        }
    }

    @Suppress("DEPRECATION")
    private fun showToastAndFinish(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
        overridePendingTransition(0, 0)
    }
}
