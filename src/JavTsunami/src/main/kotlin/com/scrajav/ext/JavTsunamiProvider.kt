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
 * JAVTSUNAMI — WordPress. Selector dari `Snowball-01/AdultColony-API` (terverifikasi live).
 * Player: `meta[itemprop=embedURL]` berisi halaman embed → cari m3u8 di sana;
 * bila embed berisi iframe, ikuti satu level.
 * Ref: docs/01-riset-sumber-video.md §6
 */
class JavTsunamiProvider : MainAPI() {
    override var name = "JavTsunami"
    override var mainUrl = "https://javtsunami.com"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    // Section navigasi (Latest/New Release/Trending/dll) + kategori & tag dari situs.
    override val mainPage = mainPageOf(
        mainPage("$mainUrl/?filter=latest", "Latest"),
        mainPage("$mainUrl/category/new-release", "New Release"),
        mainPage("$mainUrl/category/trending", "Trending"),
        mainPage("$mainUrl/category/hot-jav", "Hot JAV"),
        mainPage("$mainUrl/category/featured", "Featured"),
        mainPage("$mainUrl/category/upcoming", "Upcoming"),
        mainPage("$mainUrl/category/jav-censored", "Censored"),
        mainPage("$mainUrl/category/jav-uncensored", "Uncensored"),
        mainPage("$mainUrl/category/chinese", "Chinese"),
        mainPage("$mainUrl/category/jav-sub", "Jav Sub"),
        mainPage("$mainUrl/category/fc2", "FC2"),
        mainPage("$mainUrl/category/amateur", "Amateur"),
        mainPage("$mainUrl/category/cosplay", "Cosplay"),
        mainPage("$mainUrl/category/anal", "Anal"),
        mainPage("$mainUrl/category/big-boobs", "Big Boobs"),
        mainPage("$mainUrl/category/bbw", "BBW"),
        mainPage("$mainUrl/category/blowjob", "Blowjob"),
        mainPage("$mainUrl/category/bondage-bdsm", "Bondage BDSM"),
        mainPage("$mainUrl/category/creampie", "Creampie"),
        mainPage("$mainUrl/category/deep-throat", "Deep Throat"),
        mainPage("$mainUrl/category/family", "Family"),
        mainPage("$mainUrl/category/gangbang", "Gangbang"),
        mainPage("$mainUrl/category/squirting", "Squirting"),
        mainPage("$mainUrl/category/stepmom", "Stepmom"),
        mainPage("$mainUrl/category/cheating", "Cheating"),
        mainPage("$mainUrl/tag/uncensored-leaked", "Uncensored Leaked"),
        mainPage("$mainUrl/tag/time-stop", "Time Stop"),
        mainPage("$mainUrl/tag/mother-and-son", "Mother & Son"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.ifBlank { "$mainUrl/?filter=latest" }
        val items = parseListing(req(url))
        return newHomePageResponse(listOf(HomePageList(request.name.ifBlank { "Latest" }, items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/?s=${query.encodeUri()}"
        return parseListing(req(url)).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("div.title-block.box-shadow h1.entry-title")?.text()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: url
        val poster = doc.selectFirst("div.desc img")?.attr("data-lazy-src")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        val embed = doc.selectFirst("meta[itemprop=embedURL]")?.attr("content")
        val genres = doc.select("div.tags-list a").mapNotNull { it.attr("title").ifBlank { it.text() } }
        val date = doc.selectFirst("meta[itemprop=uploadDate]")?.attr("content")?.take(10)
        return newMovieLoadResponse(title, url, TvType.NSFW, embed ?: url) {
            this.posterUrl = poster
            this.tags = genres
            this.year = date?.take(4)?.toIntOrNull()
            this.plot = extractJavCode(title)?.let { "Kode: $it" }
        }.also { (it as? MovieLoadResponse)?.enrichGlobal() }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val embedUrl = data
        val html = runCatching { req(embedUrl) }.getOrNull() ?: return false

        val direct = findM3u8OrMp4(html)
        if (direct != null) {
            if (direct.contains(".m3u8")) {
                emitHls(name, name, resolveUrl(embedUrl, direct), embedUrl, callback)
            } else {
                emitVideo(name, name, resolveUrl(embedUrl, direct), embedUrl, callback)
            }
            return true
        }

        // Satu level iframe (batasi untuk keamanan) — semua iframe, multi-sumber.
        var found = false
        val iframes = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
            .filter { it.isNotBlank() && !it.startsWith("javascript") }.distinct()
        for (iframe in iframes) {
            val next = resolveUrl(embedUrl, iframe)
            val nextHtml = runCatching { req(next) }.getOrNull() ?: continue
            val m = findM3u8OrMp4(nextHtml)
            if (m != null) {
                if (m.contains(".m3u8")) {
                    emitHls(name, name, resolveUrl(next, m), next, callback)
                } else {
                    emitVideo(name, name, resolveUrl(next, m), next, callback)
                }
                found = true
            }
        }
        return found
    }

    private suspend fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        return doc.select("article").mapNotNull { art ->
            val a = art.selectFirst("a[href]") ?: return@mapNotNull null
            val abs = a.absUrl("href")
            if (abs.isBlank()) return@mapNotNull null
            val title = art.selectFirst("header.entry-header span")?.text() ?: a.text()
            val code = extractJavCode(abs) ?: extractJavCode(title)
            val local = art.attr("data-main-thumb").ifBlank { imgPoster(art) }
            newMovieSearchResponse(title.ifBlank { abs }, abs, TvType.NSFW) {
                this.posterUrl = listingPoster(code, local)
            }
        }.distinctBy { it.url }
    }

    private fun imgPoster(art: org.jsoup.nodes.Element): String? {
        val img = art.selectFirst("img.video-main-thumb")
            ?: art.selectFirst("div.post-thumbnail-container img")
            ?: return null
        return img.attr("data-lazy-src").ifBlank {
            img.attr("data-src").ifBlank {
                img.attr("src").takeUnless { it.startsWith("data:") }.orEmpty()
            }
        }.ifBlank { null }
    }
}
