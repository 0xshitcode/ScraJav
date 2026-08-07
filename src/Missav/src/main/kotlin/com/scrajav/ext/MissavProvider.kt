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
 * MISSAV (missav.live / missav.ws / missav.ai) — frontend JS-rendered (Alpine)
 * + Cloudflare Turnstile. Halaman & API hanya berfungsi penuh setelah JS jalan
 * (membutuhkan WebView/headless browser — tidak tersedia di ekstensi murni).
 * Provider ini best-effort: memanfaatkan apa yang tersedia di HTML statis
 * (mis. referensi langsung m3u8/mp4 di halaman video bila ada).
 * Ref: docs/01-riset-sumber-video.md §3
 */
class MissavProvider : MainAPI() {
    override var name = "MissAV"
    override var mainUrl = "https://missav.live"
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = parseListing(req(mainUrl))
        return newHomePageResponse(listOf(HomePageList("Latest", items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/search/${query.encodeUri()}"
        return parseListing(req(url)).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: url
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("video[data-poster]")?.attr("data-poster")
        val code = extractJavCode(url) ?: extractJavCode(title)
        val durationSec = doc.selectFirst("meta[property=og:video:duration]")?.attr("content")?.toIntOrNull()
        val release = doc.selectFirst("meta[property=og:video:release_date]")?.attr("content")
        val genres = doc.selectFirst(".text-secondary")
            ?.select("a[href*='/genres/']")?.mapNotNull { it.text().trim().ifBlank { null } }
            ?: emptyList()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.tags = genres.ifEmpty { null }
            this.year = release?.take(4)?.toIntOrNull()
            this.duration = durationSec
            this.plot = buildList {
                code?.let { add("Kode: $it") }
                if (genres.isNotEmpty()) add("Genre: ${genres.joinToString(", ")}")
                release?.let { if (it.isNotBlank()) add("Rilis: $it") }
                durationSec?.let { if (it > 0) add("Durasi: ${it / 60} menit") }
            }.joinToString("\n").ifBlank { code?.let { "Kode: $it" } }
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

        val direct = findM3u8OrMp4(html)
        if (direct != null) {
            if (direct.contains(".m3u8")) {
                emitHls(name, name, resolveUrl(data, direct), data, callback)
            } else {
                emitVideo(name, name, resolveUrl(data, direct), data, callback)
            }
            found = true
        }

        val embeds = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
        for (e in embeds.distinct()) {
            found = resolveEmbedGeneric(resolveUrl(data, e), subtitleCallback, callback) || found
        }
        return found
    }

    private fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        // URL video missav: /dm{n}/id/{kode} (variabel domain + kode)
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!Regex("/[A-Za-z0-9]{2,10}-\\d+/?$").containsMatchIn(abs)) return@mapNotNull null
            val img = a.selectFirst("img")
            newMovieSearchResponse(a.attr("title").ifBlank { a.text() }.ifBlank { abs }, abs, TvType.NSFW) {
                posterUrl = (img?.attr("data-src") ?: img?.attr("src"))
                    ?.takeUnless { it.startsWith("data:") }
            }
        }.distinctBy { it.url }
    }
}
