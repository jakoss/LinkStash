package pl.jsyty.linkstash

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedUrlExtractorTest {
    @Test
    fun `extractHttpUrls returns a plain URL`() {
        val urls = SharedUrlExtractor.extractHttpUrls("https://example.com/path")

        assertEquals(listOf("https://example.com/path"), urls)
    }

    @Test
    fun `extractHttpUrls finds URL embedded in text`() {
        val urls = SharedUrlExtractor.extractHttpUrls("Read this later https://example.com/article thanks")

        assertEquals(listOf("https://example.com/article"), urls)
    }

    @Test
    fun `extractHttpUrls trims trailing punctuation`() {
        val urls = SharedUrlExtractor.extractHttpUrls("https://example.com/article),")

        assertEquals(listOf("https://example.com/article"), urls)
    }

    @Test
    fun `extractFromCandidates removes duplicate URLs`() {
        val urls = SharedUrlExtractor.extractFromCandidates(
            listOf(
                "https://example.com/one",
                "before https://example.com/one after",
                "https://example.com/two"
            )
        )

        assertEquals(
            listOf("https://example.com/one", "https://example.com/two"),
            urls
        )
    }
}
