package pl.jsyty.linkstash

import android.content.Context
import android.content.Intent

object SharedUrlExtractor {
    private val httpUrlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val trailingUrlPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

    fun extract(intent: Intent, context: Context): List<String> {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }

        val rawCandidates = buildList {
            intent.dataString?.let(::add)
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::add)
            intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.let(::add)
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.let(::add)

            intent.clipData?.let { clipData ->
                repeat(clipData.itemCount) { index ->
                    val item = clipData.getItemAt(index)
                    item.uri?.toString()?.let(::add)
                    item.coerceToText(context)?.toString()?.let(::add)
                }
            }

            intent.extras?.keySet().orEmpty().forEach { key ->
                when (val value = intent.extras?.get(key)) {
                    is String -> add(value)
                    is CharSequence -> add(value.toString())
                    is ArrayList<*> -> value.filterIsInstance<CharSequence>().mapTo(this) { it.toString() }
                }
            }
        }

        return extractFromCandidates(rawCandidates)
    }

    internal fun extractFromCandidates(rawCandidates: List<String>): List<String> {
        return rawCandidates
            .flatMap(::extractHttpUrls)
            .distinct()
    }

    internal fun extractHttpUrls(raw: String): List<String> {
        return httpUrlRegex.findAll(raw)
            .map { match -> match.value.trimEnd { it in trailingUrlPunctuation } }
            .filter {
                it.startsWith(prefix = "http://", ignoreCase = true) ||
                    it.startsWith(prefix = "https://", ignoreCase = true)
            }
            .toList()
    }
}
