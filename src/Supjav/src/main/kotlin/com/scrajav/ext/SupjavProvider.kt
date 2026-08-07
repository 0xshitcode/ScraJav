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
 * SUPJAV — blueprint `xbol0/supjavd/supjav.ts` (terverifikasi di app ScraJav):
 * 1. GET /{id}.html → parse `data-link="TOKEN">SERVER<` → peta server.
 * 2. Token server "TV" dibalik string-nya.
 * 3. GET {streamApiHost}/supjav.php?c={terbalik} (Referer: supjav.com).
 * 4. Regex /urlPlay.*?(https.*?\.m3u8)/ → m3u8; segmen CDN butuh Referer origin.
 *
 * ⚠️ supjav.com dilindungi Cloudflare Turnstile — dari HP curl polos pun 403
 * ("Just a moment"). Tanpa WebView, ekstensi TIDAK bisa menyelesaikan challenge;
 * provider ini best-effort (kemungkinan besar kosong di CloudStream).
 * Ref: docs/01-riset-sumber-video.md §2
 */
class SupjavProvider : MainAPI() {
    override var name = "SupJAV"
    override var mainUrl = "https://supjav.com"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    // Backend API stream — bisa berubah; jadikan config.
    internal var streamApiHost = "https://lk1.supremejav.com"

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = parseListing(req("$mainUrl/popular?sort=day"))
        return newHomePageResponse(listOf(HomePageList("Popular", items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/?s=${query.encodeUri()}"
        return parseListing(req(url)).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = runCatching { req(url) }.getOrNull() ?: return null
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: url
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val id = Regex("(\\d+)\\.html").find(url)?.groupValues?.getOrNull(1)
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = id?.let { "SupJAV #$it" }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val html = runCatching { req(data) }.getOrNull() ?: return false
        val servers = parseServers(html)
        if (servers.isEmpty()) return false

        // Multi-sumber: coba semua server (TV, dst); emit semua m3u8 unik yang ketemu.
        var found = false
        val seen = mutableSetOf<String>()
        for ((serverName, token) in servers) {
            val apiUrl = "$streamApiHost/supjav.php?c=${token.reversed()}"
            val apiBody = runCatching { req(apiUrl) }.getOrNull() ?: continue
            val m3u8 = apiBody.firstMatch(Regex("""urlPlay.*?(https.*?\.m3u8)""")) ?: continue
            if (!seen.add(m3u8)) continue
            val origin = Regex("^https?://[^/]+").find(m3u8)?.value ?: mainUrl
            emitHls(name, "$name $serverName", m3u8, origin, callback)
            found = true
        }
        return found
    }

    internal fun parseServers(html: String): Map<String, String> =
        Regex("""data-link="([^"]*)"[^>]*>([^<]*)<""")
            .findAll(html)
            .associate { it.groupValues[2].trim() to it.groupValues[1].trim() }
            .filterValues { it.isNotEmpty() }

    internal fun parseListing(html: String): List<SearchResponse> {
        val re = Regex(
            """href="([^"]*?(\d+)\.html)"[^>]*title="([^"]*)"[\s\S]*?data-original="([^"]*)""""
        )
        return re.findAll(html).map { m ->
            val href = m.groupValues[1]
            val abs = if (href.startsWith("http")) href else "$mainUrl$href"
            newMovieSearchResponse(m.groupValues[3], abs, TvType.NSFW) {
                posterUrl = m.groupValues[4].substringBefore("!")
            }
        }.distinctBy { it.url }.toList()
    }
}
