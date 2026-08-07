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
 * 123AV.COM — agregator Mandarin (jaringan multi-situs). Referensi
 * `Discover999/123AV_app`: URL video bisa di halaman HTML (videoUrl) atau
 * didapat via WebView intercept (tidak tersedia di ekstensi murni).
 * Field detail (Mandarin): 代码=code, 发布日期=rilis, 时长=durasi, 女演员=aktris.
 * Ref: docs/01-riset-sumber-video.md §7
 */
class Av123Provider : MainAPI() {
    override var name = "123AV"
    override var mainUrl = "https://123av.com"
    override var lang = "zh"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = parseListing(req(mainUrl))
        return newHomePageResponse(listOf(HomePageList("Latest", items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/search?query=${query.encodeUri()}"
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

        // videoUrl di HTML (pola app referensi) atau m3u8/mp4 langsung.
        val direct = html.firstMatch(Regex("""videoUrl["']?\s*[:=]\s*["']([^"']+)["']"""))
            ?: findM3u8OrMp4(html)
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
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { return@mapNotNull null }
            if (img == null && abs == mainUrl) return@mapNotNull null
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = (img?.attr("data-src") ?: img?.attr("src"))
                    ?.takeUnless { it.startsWith("data:") }
            }
        }.distinctBy { it.url }.filter { it.url.startsWith(mainUrl) }
    }
}
