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
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import org.jsoup.Jsoup

/**
 * JAVMOST.WS — SPA Bootstrap; listing via API JSON (probe live Agustus 2026):
 *   GET /showlist2/{group}/{page}/{type}/ → {"result":[{url,name,cover,...}]}
 * Video URL : /{KODE}/  (mis. /IPZZ-904/)
 * Player    : script obfuscated + blok AES (OpenSSL "Salted__") — stream tidak
 *             tersedia langsung di HTML; extractor mengandalkan m3u8/mp4/embed
 *             yang terekspos + extractor bawaan CloudStream (best-effort).
 */
class JavMostProvider : MainAPI() {
    override var name = "JavMost"
    override var mainUrl = "https://www.javmost.ws"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = listingFromApi("new", page)
        return newHomePageResponse(listOf(HomePageList("New", items, false)), items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        // Coba search URL umum; fallback ke API listing bila kosong.
        val items = runCatching {
            parseSearch(req("$mainUrl/search/${query.encodeUri()}/"))
        }.getOrDefault(emptyList())
        return items.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: url
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val code = Regex("/([A-Za-z0-9]+-\\d+)/?$").find(url)?.groupValues?.getOrNull(1)
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
        var found = false

        // Embed yang terekspos (jika ada).
        val embeds = Jsoup.parse(html).select("iframe[src]").mapNotNull { it.attr("src") }
        for (e in embeds.distinct()) {
            found = resolveEmbedGeneric(resolveUrl(data, e), subtitleCallback, callback) || found
        }

        // Fallback: m3u8/mp4 langsung.
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

    /** Panggil API showlist2 dan ubah JSON → SearchResponse. */
    private suspend fun listingFromApi(type: String, page: Int): List<SearchResponse> {
        val url = "$mainUrl/showlist2/0/$page/$type/"
        val json = runCatching { req(url) }.getOrNull() ?: return emptyList()
        return parseShowlist(json)
    }

    internal fun parseShowlist(json: String): List<SearchResponse> {
        val root = runCatching { mapper.readValue(json, Map::class.java) }.getOrNull() ?: return emptyList()
        val result = root["result"] as? List<*> ?: return emptyList()
        return result.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val url = (map["url"] as? String) ?: return@mapNotNull null
            val name = (map["name"] as? String) ?: url
            val cover = map["cover"] as? String
            newMovieSearchResponse(name, url, TvType.NSFW) {
                posterUrl = cover
            }
        }
    }

    private fun parseSearch(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        val cardRe = Regex("^${Regex.escape(mainUrl)}/[A-Za-z0-9]+-\\d+/?$")
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!cardRe.containsMatchIn(abs)) return@mapNotNull null
            val img = a.selectFirst("img")
            newMovieSearchResponse(a.attr("title").ifBlank { a.text() }.ifBlank { abs }, abs, TvType.NSFW) {
                posterUrl = img?.attr("src")?.takeUnless { it.startsWith("data:") }
            }
        }.distinctBy { it.url }
    }
}
