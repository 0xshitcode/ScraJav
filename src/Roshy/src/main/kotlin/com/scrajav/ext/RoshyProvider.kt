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
 * ROSHY.TV — WordPress. Probe live Agustus 2026:
 * - Video URL : /video/{id}-{kode}-{slug}/ (id acak atau kode-awal).
 * - Home & search menyediakan kartu video statis (poster + title).
 * - Penting: halaman video TIDAK punya player stream — hanya tombol
 *   download (k2s.cc / nitroflare.com / uploadgig.com). loadLinks best-effort:
 *   cek iframe & m3u8 langsung, kemungkinan besar kosong.
 */
class RoshyProvider : MainAPI() {
    override var name = "Roshy"
    override var mainUrl = "https://roshy.tv"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    // Section navigasi (sortir subtitle/decensored) + kategori genre dari situs.
    override val mainPage = mainPageOf(
        mainPage("$mainUrl/category/english-sub-5/?sort_by=new", "New Subtitles"),
        mainPage("$mainUrl/category/english-sub-5/?sort_by=most_viewed", "Most Viewed Subs"),
        mainPage("$mainUrl/category/decensored-4/?sort_by=new", "Decensored New"),
        mainPage("$mainUrl/category/decensored-4/?sort_by=most_viewed", "Decensored Most Viewed"),
        mainPage("$mainUrl/trending/", "Trending"),
        mainPage("$mainUrl/category/anal/", "Anal"),
        mainPage("$mainUrl/category/bbw/", "BBW"),
        mainPage("$mainUrl/category/big-tits-2/", "Big Tits"),
        mainPage("$mainUrl/category/blowjob/", "Blowjob"),
        mainPage("$mainUrl/category/bondage/", "Bondage"),
        mainPage("$mainUrl/category/creampie/", "Creampie"),
        mainPage("$mainUrl/category/cuckold/", "Cuckold"),
        mainPage("$mainUrl/category/hardcore/", "Hardcore"),
        mainPage("$mainUrl/category/incest/", "Incest"),
        mainPage("$mainUrl/category/lesbian/", "Lesbian"),
        mainPage("$mainUrl/category/maid/", "Maid"),
        mainPage("$mainUrl/category/married-woman-3/", "Married Woman"),
        mainPage("$mainUrl/category/mature-woman-2/", "Mature Woman"),
        mainPage("$mainUrl/category/milf/", "Milf"),
        mainPage("$mainUrl/category/mother-3/", "Mother"),
        mainPage("$mainUrl/category/orgy/", "Orgy"),
        mainPage("$mainUrl/category/pov/", "POV"),
        mainPage("$mainUrl/category/schoolgirls/", "Schoolgirl"),
        mainPage("$mainUrl/category/slender/", "Slender"),
        mainPage("$mainUrl/category/squirting/", "Squirting"),
        mainPage("$mainUrl/category/uniform/", "Uniform"),
        mainPage("$mainUrl/category/urination/", "Urination"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.ifBlank { "$mainUrl/category/english-sub-5/?sort_by=new" }
        val items = parseListing(req(url))
        return newHomePageResponse(listOf(HomePageList(request.name.ifBlank { "New Subtitles" }, items, false)), false)
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
        val code = Regex("(?:/[a-z0-9]+-)?([a-z0-9]{2,8}-\\d+)-").find(url)
            ?.groupValues?.getOrNull(1)?.uppercase()
            ?: extractJavCode(title)
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
        val embeds = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
        for (e in embeds.distinct()) {
            found = resolveEmbedGeneric(resolveUrl(data, e), subtitleCallback, callback) || found
        }
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
        val cardRe = Regex("^${Regex.escape(mainUrl)}/(video/)?[a-z0-9]{2,8}-\\d+")
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!cardRe.containsMatchIn(abs)) return@mapNotNull null
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { a.text() }.ifBlank { abs }
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = (img?.attr("data-src") ?: img?.attr("src"))
                    ?.takeUnless { it.startsWith("data:") || it.contains("histats") }
            }
        }.distinctBy { it.url }
    }
}
