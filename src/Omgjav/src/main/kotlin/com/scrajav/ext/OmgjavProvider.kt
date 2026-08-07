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
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import org.jsoup.Jsoup

/**
 * OMGJAV — terverifikasi live (Agustus 2026).
 * - Listing/home : GET /search/hottest
 * - Search       : GET /?s={query}
 * - Halaman video: GET /v/{kode}; m3u8 tertanam di JSON player:
 *      hosts:[{name:"BETA", media:{src:"https://cdn.xxx/master.m3u8", type:"m3u8"}}]
 * - Link listing relatif ("../v/X") — diselesaikan via Jsoup absUrl.
 * Ref: docs/01-riset-sumber-video.md §1
 */
class OmgjavProvider : MainAPI() {
    override var name = "OMGJAV"
    override var mainUrl = "https://omgjav.com"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/search/hottest"
        val items = parseListing(req(url), url)
        return newHomePageResponse(listOf(HomePageList("Hot", items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/?s=${query.encodeUri()}"
        return parseListing(req(url), url).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: url
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val code = Regex("/v/([^/?#]+)").find(url)?.groupValues?.getOrNull(1)
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = code?.let { "Kode: $it" }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val html = runCatching { req(data) }.getOrNull() ?: return false
        val m3u8 = html.firstMatch(Regex("""(?:src|"src")\s*[:=]\s*"([^"]+\.m3u8[^"]*)"""))
            ?: return false
        callback(hlsLink(name, name, resolveUrl(data, m3u8), "$mainUrl/"))
        return true
    }

    private fun parseListing(html: String, pageUrl: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, pageUrl)
        val videoLink = Regex("/v/[A-Za-z0-9_-]+/?$")
        return doc.select("a[href*='/v/']").mapNotNull { a ->
            val href = a.attr("href")
            if (!videoLink.containsMatchIn(href)) return@mapNotNull null
            val abs = a.absUrl("href")
            if (abs.isBlank()) return@mapNotNull null
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { abs }
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = img?.attr("src") ?: img?.attr("data-src")
            }
        }.distinctBy { it.url }
    }
}
