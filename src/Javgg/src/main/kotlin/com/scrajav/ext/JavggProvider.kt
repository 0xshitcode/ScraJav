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
import com.lagradost.cloudstream3.mainPage
import com.lagradost.cloudstream3.mainPageOf
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

    // Section navigasi (New/Trending/Featured) + tag/kategori genre dari situs.
    override val mainPage = mainPageOf(
        mainPage("$mainUrl/new-post/", "New Post"),
        mainPage("$mainUrl/trending/", "Trending"),
        mainPage("$mainUrl/trending/?sort=today", "Popular Today"),
        mainPage("$mainUrl/trending/?sort=weekly", "Popular This Week"),
        mainPage("$mainUrl/trending/?sort=monthly", "Popular This Month"),
        mainPage("$mainUrl/featured/", "Featured"),
        mainPage("$mainUrl/tag/english-subtitle/", "English Subtitle"),
        mainPage("$mainUrl/tag/uncensored-leak/", "Uncensored Leak"),
        mainPage("$mainUrl/tag/reduce-mosaic/", "Reduce Mosaic"),
        mainPage("$mainUrl/tag/censored/", "Censored"),
        mainPage("$mainUrl/tag/chinese-subtitle/", "Chinese Subtitle"),
        mainPage("$mainUrl/tag/chinese-porn/", "Chinese Porn"),
        mainPage("$mainUrl/genre/1080p/", "1080p"),
        mainPage("$mainUrl/genre/4k/", "4K"),
        mainPage("$mainUrl/genre/amateur/", "Amateur"),
        mainPage("$mainUrl/genre/anal/", "Anal"),
        mainPage("$mainUrl/genre/big-tits/", "Big Tits"),
        mainPage("$mainUrl/genre/blowjob/", "Blowjob"),
        mainPage("$mainUrl/genre/creampie/", "Creampie"),
        mainPage("$mainUrl/genre/cuckold/", "Cuckold"),
        mainPage("$mainUrl/genre/squirting/", "Squirting"),
        mainPage("$mainUrl/genre/lesbian/", "Lesbian"),
        mainPage("$mainUrl/genre/incest/", "Incest"),
        mainPage("$mainUrl/genre/orgy/", "Orgy"),
        mainPage("$mainUrl/genre/drama/", "Drama"),
        mainPage("$mainUrl/genre/solowork/", "Solowork"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.ifBlank { "$mainUrl/new-post/" }
        val items = parseListing(req(url))
        return newHomePageResponse(listOf(HomePageList(request.name.ifBlank { "New Post" }, items, false)), false)
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
        val code = Regex("/jav/([a-z0-9]{2,8}-\\d+)", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)?.uppercase()
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

    private suspend fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        val postRe = Regex("^https?://javgg\\.net/jav/")
        return doc.select("a[href*='/jav/']").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!postRe.containsMatchIn(abs)) return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { abs }
            val code = extractJavCode(abs) ?: extractJavCode(title)
            val poster = listingPoster(code, anchorPoster(a))
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = poster
            }
        }.distinctBy { it.url }
    }
}
