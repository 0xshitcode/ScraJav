package com.scrajav.ext

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import org.jsoup.Jsoup

/**
 * JAVGG.NET — WordPress tema DooPlayer (probe live Agustus 2026).
 * - Video posts : /jav/{kode}-{slug}/
 * - Player      : iframe embed di halaman detail (earnvidjavgg.xyz, turbovidhls.com,
 *                 dst) → ikuti embed → m3u8/mp4 (atau extractor bawaan CloudStream).
 * Ref: docs/01-riset-sumber-video.md §8 (hipotesis → terverifikasi saat probe).
 */
class JavggProvider : MainAPI() {
    override var name = "JAVGG"
    override var mainUrl = "https://javgg.net"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = parseListing(req(mainUrl))
        return newHomePageResponse(listOf(HomePageList("Latest", items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/?s=${query.encodeUri()}"
        return parseListing(req(url)).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: url
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val code = Regex("/jav/([a-z0-9-]+?)/?$", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)?.substringBefore("-")?.uppercase()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = code?.let { "Kode: $it" }
        }.also { (it as? MovieLoadResponse)?.enrichGlobal() }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val html = runCatching { req(data) }.getOrNull() ?: return false
        var found = false

        // Iframe embed (earnvid, turbovidhls, dst).
        val embeds = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
        for (e in embeds.distinct()) {
            val resolved = resolveUrl(data, e)
            found = resolveEmbedGeneric(resolved, subtitleCallback, callback) || found
        }

        // Fallback: m3u8/mp4 langsung di halaman — multi-resolusi.
        val direct = findM3u8OrMp4(html)
        if (direct != null) {
            if (direct.contains(".m3u8")) {
                emitHls(name, name, resolveUrl(data, direct), data, callback)
            } else {
                emitVideo(name, name, resolveUrl(data, direct), data, callback)
            }
            found = true
        }
        return found
    }

    private fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        val postRe = Regex("^https?://javgg\\.net/jav/")
        return doc.select("a[href*='/jav/']").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!postRe.containsMatchIn(abs)) return@mapNotNull null
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { abs }
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = (img?.attr("data-src") ?: img?.attr("src"))
                    ?.takeUnless { it.startsWith("data:") }
            }
        }.distinctBy { it.url }
    }
}
