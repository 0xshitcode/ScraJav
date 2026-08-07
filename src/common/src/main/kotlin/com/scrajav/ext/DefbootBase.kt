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
 * Keluarga "defboot" — javbraze.com, javhd.today, javseen.tv.
 * Satu template: halaman video /{id}/{code}-{slug}/, player via playEmbed('...') ke
 * turbovid.vip/t/{token} & cloudwish.xyz/e/{token} (halaman embed berisi m3u8/mp4).
 * Ref: docs/01-riset-sumber-video.md §5 + probe live Agustus 2026.
 */
open class DefbootBase : MainAPI() {

    override var name = "Defboot"
    override var mainUrl = "https://javbraze.com"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    /** Path halaman daftar video terbaru; bisa di-override per situs. */
    open val mainPagePath: String = "/"

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.ifBlank { "$mainUrl$mainPagePath" }
        val items = scrapeCards(url)
        return newHomePageResponse(listOf(HomePageList(request.name.ifBlank { "Latest" }, items, false)), false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/search/video/?q=${query.encodeUri()}"
        return scrapeCards(url).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = Jsoup.parse(req(url))
        val title = doc.selectFirst("h1")?.text()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: url
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val code = Regex("/(\\d+)/([a-z0-9]{2,8}-\\d+)-").find(url)
            ?.groupValues?.getOrNull(2)?.uppercase()
        // Info rows javhd.today: "Release Day: 2014-10-24", "Studio: ...", "Director: ...", "Label: ..."
        val infoRows = doc.select("div[style*='text-transform']").mapNotNull { el ->
            val txt = el.text()
            if (txt.contains(':')) txt.substringBefore(':').trim() to txt.substringAfter(':').trim()
            else null
        }
        val year = infoRows.firstOrNull { it.first == "Release Day" }
            ?.second?.take(4)?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = buildList {
                code?.let { add("Kode: $it") }
                infoRows.forEach { (k, v) -> if (v.isNotBlank() && v != "----") add("$k: $v") }
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
        val embeds = LinkedHashSet<String>().apply {
            // playEmbed('...') literal
            addAll(Regex("playEmbed\\('([^']+)'\\)").findAll(html).map { it.groupValues[1] })
            // playEmbed("...")
            addAll(Regex("""playEmbed\("([^"]+)"\)""").findAll(html).map { it.groupValues[1] })
            // data-embed="base64" (javhd.today pakai pola ini untuk 5 server)
            addAll(Regex("""data-embed="([A-Za-z0-9+/=]+)"""").findAll(html).map {
                decodeBase64(it.groupValues[1]) ?: ""
            })
            // iframe langsung di halaman (mis. /embed/{id}/)
            addAll(Regex("""<iframe[^>]*src="([^"]+)"""").findAll(html).map { it.groupValues[1] })
        }.filter { it.startsWith("http") || it.startsWith("/") }
        for (embed in embeds) {
            val resolved = resolveUrl(data, embed)
            found = resolveEmbedGeneric(resolved, subtitleCallback, callback) || found
        }
        // Fallback: m3u8/mp4 langsung di halaman (trailer dll) — multi-resolusi via emitHls.
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

    /** Parse kartu video /{id}/{code}-{slug}/ beserta poster. */
    private suspend fun scrapeCards(url: String): List<SearchResponse> {
        val html = runCatching { req(url) }.getOrNull() ?: return emptyList()
        val doc = Jsoup.parse(html, mainUrl)
        val cardRe = Regex("^${Regex.escape(mainUrl)}/\\d+/[a-z0-9-]+/?$")
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!cardRe.containsMatchIn(abs)) return@mapNotNull null
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { img?.attr("alt") }
                ?.ifBlank { a.text() } ?: abs
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = (img?.attr("data-src") ?: img?.attr("src"))
                    ?.takeUnless { it.startsWith("data:") || it.contains("flag") }
            }
        }.distinctBy { it.url }
    }
}
