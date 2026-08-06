package com.scrajav.ext

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
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
import com.lagradost.cloudstream3.utils.encodeUri
import org.jsoup.Jsoup

/**
 * SEXTB.CC — host video JAV terkenal; situs & mirror semuanya diblokir
 * Cloudflare saat probe (cc/net 403, ws/com 000). Struktur halaman belum bisa
 * diverifikasi. Provider best-effort dengan pola umum /{kode}-{slug}/.
 * Ref: docs/01-riset-sumber-video.md §14
 */
class SextbProvider : MainAPI() {
    override var name = "SextB"
    override var mainUrl = "https://sextb.cc"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private fun req(url: String) = app.get(url, referer = mainUrl).text

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
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val html = runCatching { req(data) }.getOrNull() ?: return false
        var found = false
        val embeds = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
        for (e in embeds.distinct()) {
            found = resolveEmbedGeneric(resolveUrl(data, e), subtitleCallback, callback) || found
        }
        val direct = findM3u8OrMp4(html)
        if (direct != null) {
            callback(
                if (direct.contains(".m3u8")) hlsLink(name, name, resolveUrl(data, direct), data)
                else videoLink(name, name, resolveUrl(data, direct), data)
            )
            found = true
        }
        return found
    }

    private fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        val cardRe = Regex("^${Regex.escape(mainUrl)}/[a-z0-9]{2,8}-\\d+")
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!cardRe.containsMatchIn(abs)) return@mapNotNull null
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { abs }
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = (img?.attr("data-src") ?: img?.attr("src"))
                    ?.takeUnless { it.startsWith("data:") }
            }
        }.distinctBy { it.url }
    }
}
