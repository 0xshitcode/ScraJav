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

    // Section navigasi (New/Hot/Recent/sortir) + kategori genre & maker dari situs.
    override val mainPage = mainPageOf(
        mainPage("$mainUrl/en/hot", "Hot"),
        mainPage("$mainUrl/en/new", "New"),
        mainPage("$mainUrl/en/recent", "Recent"),
        mainPage("$mainUrl/en/all?sort=today", "Today"),
        mainPage("$mainUrl/en/all?sort=week", "This Week"),
        mainPage("$mainUrl/en/all?sort=month", "This Month"),
        mainPage("$mainUrl/en/censored", "Censored"),
        mainPage("$mainUrl/en/uncensored", "Uncensored"),
        mainPage("$mainUrl/en/uncensored-leaked", "Uncensored Leaked"),
        mainPage("$mainUrl/en/genres/solowork", "Solowork"),
        mainPage("$mainUrl/en/genres/creampie", "Creampie"),
        mainPage("$mainUrl/en/genres/big-tits", "Big Tits"),
        mainPage("$mainUrl/en/genres/married-womanhousewife", "Housewife"),
        mainPage("$mainUrl/en/genres/amateur", "Amateur"),
        mainPage("$mainUrl/en/genres/blowjob", "Blowjob"),
        mainPage("$mainUrl/en/genres/beautiful-girl", "Beautiful Girl"),
        mainPage("$mainUrl/en/genres/slender", "Slender"),
        mainPage("$mainUrl/en/genres/slut", "Slut"),
        mainPage("$mainUrl/en/genres/squirting", "Squirting"),
        mainPage("$mainUrl/en/genres/cuckold-cuckolded-ntr", "Cuckold"),
        mainPage("$mainUrl/en/genres/anal", "Anal"),
        mainPage("$mainUrl/en/genres/schoolgirl", "Schoolgirl"),
        mainPage("$mainUrl/en/genres/maid", "Maid"),
        mainPage("$mainUrl/en/genres/lesbian", "Lesbian"),
        mainPage("$mainUrl/en/genres/incest", "Incest"),
        mainPage("$mainUrl/en/genres/vr", "VR"),
        mainPage("$mainUrl/en/makers/fc2", "FC2"),
        mainPage("$mainUrl/en/makers/heyzo", "HEYZO"),
        mainPage("$mainUrl/en/makers/1pondo", "1pondo"),
        mainPage("$mainUrl/en/makers/caribbeancom", "Caribbeancom"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.ifBlank { "$mainUrl/en/hot" }
        val items = parseListing(req(url))
        return newHomePageResponse(listOf(HomePageList(request.name.ifBlank { "Hot" }, items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/en/search?keyword=${query.encodeUri()}"
        return parseListing(req(url)).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: url

        // Format judul 123AV: "ABF-375 — judul... — Aktris"
        val code = extractJavCode(title) ?: Regex("/en/v/([^/?#]+)").find(url)
            ?.groupValues?.getOrNull(1)?.uppercase()
        val actress = title.substringAfter(" — ", "").substringBeforeLast(" — ")
            ?.takeIf { it.isNotBlank() && it != "123AV" }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.plot = buildList {
                code?.let { add("Kode: $it") }
                if (!actress.isNullOrBlank() && actress != title) add("Aktris: $actress")
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

        // m3u8/mp4 langsung di HTML.
        val direct = findM3u8OrMp4(html)
        if (direct != null) {
            if (direct.contains(".m3u8")) {
                emitHls(name, name, resolveUrl(data, direct), data, callback)
            } else {
                emitVideo(name, name, resolveUrl(data, direct), data, callback)
            }
            found = true
        }

        // Episode dari pola player(JSON.parse('...')) → embed javplayer.cc → stream API.
        val embeds = parsePlayerEpisodes(html)
        for (e in embeds.distinct()) {
            found = if (e.contains("javplayer.cc")) {
                resolveJavPlayer(e, subtitleCallback, callback)
            } else {
                resolveEmbedGeneric(e, subtitleCallback, callback)
            } || found
        }

        // iframe embed lain.
        val iframes = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
        for (e in iframes.distinct()) {
            found = resolveEmbedGeneric(resolveUrl(data, e), subtitleCallback, callback) || found
        }
        return found
    }

    private suspend fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!Regex("^${Regex.escape(mainUrl)}/en/v/[^/?#]+").containsMatchIn(abs)) {
                return@mapNotNull null
            }
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { return@mapNotNull null }
            val code = extractJavCode(abs) ?: extractJavCode(title)
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = listingPoster(code, anchorPoster(a))
            }
        }.distinctBy { it.url }
    }
}
