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
import org.jsoup.nodes.Element

/**
 * JAV.GURU — WordPress (GeneratePress). Probe live Agustus 2026:
 * - Video URL : /{id}/{kode}-{slug}/
 * - Player    : tombol `wp-btn-iframe` dengan `data-localize`; blok JS berisi
 *   `iframe_url` base64 → halaman gateway `searcho/?xd={token}`.
 *   Gateway: stream-box punya 3 atribut data-* → token = gabungan ketiganya →
 *   player asli = `searcho/?xr={token-dibalik}` → m3u8 (multi-resolusi via emitHls).
 * - Metadata  : infoleft (Code/Release Date/Director/Studio/Label/Actress/Tags).
 * Ref: docs/01-riset-sumber-video.md §4
 */
class JavGuruProvider : MainAPI() {
    override var name = "JavGuru"
    override var mainUrl = "https://jav.guru"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private suspend fun req(url: String) = app.get(url, referer = mainUrl).text

    // Section navigasi + kategori & tag populer dari situs.
    override val mainPage = mainPageOf(
        mainPage("$mainUrl/", "Latest"),
        mainPage("$mainUrl/most-watched-rank/", "Hot"),
        mainPage("$mainUrl/category/english-subbed/", "Subs"),
        mainPage("$mainUrl/category/jav/", "JAV"),
        mainPage("$mainUrl/category/decensored/", "Decensored"),
        mainPage("$mainUrl/category/amateur/", "Amateur"),
        mainPage("$mainUrl/category/idol/", "Idol"),
        mainPage("$mainUrl/category/4k/", "4K"),
        mainPage("$mainUrl/tag/big-tits/", "Big Tits"),
        mainPage("$mainUrl/tag/creampie/", "Creampie"),
        mainPage("$mainUrl/tag/pov/", "POV"),
        mainPage("$mainUrl/tag/solowork/", "Solowork"),
        mainPage("$mainUrl/tag/mature/", "Mature"),
        mainPage("$mainUrl/tag/blowjob/", "Blowjob"),
        mainPage("$mainUrl/tag/orgy/", "Orgy"),
        mainPage("$mainUrl/tag/orgasm/", "Orgasm"),
        mainPage("$mainUrl/tag/squirting/", "Squirting"),
        mainPage("$mainUrl/tag/slender/", "Slender"),
        mainPage("$mainUrl/tag/married/", "Married"),
        mainPage("$mainUrl/tag/anal/", "Anal"),
        mainPage("$mainUrl/tag/gal/", "Gal"),
        mainPage("$mainUrl/tag/maid/", "Maid"),
        mainPage("$mainUrl/tag/female-teacher/", "Female Teacher"),
        mainPage("$mainUrl/tag/nurse/", "Nurse"),
        mainPage("$mainUrl/tag/stepmother/", "Stepmother"),
        mainPage("$mainUrl/tag/incest/", "Incest"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.ifBlank { mainUrl }
        val items = parseListing(req(url))
        return newHomePageResponse(listOf(HomePageList(request.name.ifBlank { "Latest" }, items, false)), false)
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
            ?: doc.selectFirst(".large-screenimg img")?.attr("src")

        val info = parseInfo(doc)
        val code = Regex("/(\\d+)/([a-z0-9]+)-").find(url)?.groupValues?.getOrNull(2)?.uppercase()
            ?: extractJavCode(title)
        val year = info.infoValue("Release Date", "发布日期")?.take(4)?.toIntOrNull()
        val actress = info.infoValue("Actress", "Actresses", "Cast", "演员", "女演员")
        val studio = info.infoValue("Studio", "Maker", "制作人")
        val director = info.infoValue("Director", "导演")
        val tags = info.infoValue("Tags", "Category", "标签", "类型")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.tags = tags?.ifEmpty { null }
            this.year = year
            this.plot = buildList {
                code?.let { add("Kode: $it") }
                actress?.let { if (it.isNotBlank()) add("Aktris: $it") }
                studio?.let { if (it.isNotBlank()) add("Studio: $it") }
                info.infoValue("Release Date")?.let { if (it.isNotBlank()) add("Rilis: $it") }
                director?.let { if (it.isNotBlank()) add("Sutradara: $it") }
            }.joinToString("\n").ifBlank { code?.let { "Kode: $it" } }
        }.also { (it as? MovieLoadResponse)?.enrichGlobal() }
    }

    /**
     * Parse baris infoleft → peta label → nilai teks (a[href] dipakai utk nilai).
     * Key dinormalisasi (lowercase) agar "Studio Label"/"Actresses" dst. tetap cocok.
     */
    private fun parseInfo(doc: org.jsoup.nodes.Document): Map<String, String> =
        doc.select("li").mapNotNull { li ->
            val strong = li.selectFirst("strong")?.text()?.trimEnd(':', ' ') ?: return@mapNotNull null
            val value = li.select("a").map { it.text().trim() }.filter { it.isNotBlank() }
                .joinToString(", ").ifBlank { li.ownText().trim().trimEnd(':') }
            if (value.isBlank()) null else strong.lowercase() to value
        }.toMap()

    private fun Map<String, String>.infoValue(vararg labels: String): String? =
        labels.mapNotNull { this[it.lowercase()] }.firstOrNull()?.takeIf { it.isNotBlank() }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val html = runCatching { req(data) }.getOrNull() ?: return false
        var found = false

        // 1) Gateway searcho dari tombol wp-btn-iframe (iframe_url base64) — bisa lebih dari satu.
        val b64s = Regex("""iframe_url[\"']?\s*[:=]\s*[\"']([A-Za-z0-9+/=]{20,})[\"']""")
            .findAll(html).map { it.groupValues[1] }.toList()
        for (b64 in b64s) {
            val decoded = decodeBase64(b64) ?: continue
            if (decoded.contains("searcho")) {
                found = resolveSearcho(decoded, callback) || found
            }
        }

        // 2) Fallback: m3u8/mp4 langsung — multi-resolusi.
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

    /**
     * Ikuti gateway searcho → player asli:
     * 1. Baca cfg.keys + atribut data-* pada stream-box → token (gabungan urutan keys).
     * 2. Player asli = `searcho/?xr={token-reversed}`.
     * 3. `?xr=` me-redirect (302) ke `javclan.com/e/{hex-decode(reversed)}` yang berisi
     *    packed JS dengan objek `links` = {hls2, hls3, hls4} → URL master m3u8.
     *    hls2/hls3 absolut (premilkyway/handmadecraftstore), hls4 relatif ke javclan.
     *    Multi-resolusi via [emitHls].
     */
    private suspend fun resolveSearcho(searchoUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
        val html = runCatching { app.get(searchoUrl, referer = mainUrl).text }.getOrNull() ?: return false

        val base = html.firstMatch(Regex("""base:\s*'([^']+)'""")) ?: return false
        val keys = html.firstMatch(Regex("""keys:\s*\[([^\]]+)\]"""))
            ?.let { Regex("'([^']+)'").findAll(it).map { m -> m.groupValues[1] }.toList() }
            ?: return false
        if (keys.isEmpty()) return false

        val box = html.firstMatch(Regex("""class="stream-box"([^>]*)""")) ?: return false
        val token = keys.joinToString("") { key ->
            Regex("""$key=\"([^\"]+)\"""").find(box)?.groupValues?.getOrNull(1) ?: ""
        }
        if (token.length < 8) return false

        val realSrc = "$base?xr=${token.reversed()}"

        // ?xr= redirect (302) ke javclan.com/e/{hex-reversed}; pakai followRedirects
        // default agar sampai ke halaman player, lalu ambil URL aktual untuk referer.
        val playerHtml = runCatching { app.get(realSrc, referer = searchoUrl).text }.getOrNull() ?: return false

        // Stream m3u8 bisa saja langsung ada (beberapa host), atau di dalam packed JS.
        val all = StringBuilder(playerHtml)
        unpackPackedWithRadix(playerHtml)?.let { all.append('\n').append(it) }

        val links = resolveHlsLinks(all.toString())
        var found = false
        links.forEach { url ->
            val abs = if (url.startsWith("http")) url else resolveUrl("https://javclan.com/", url)
            if (abs.contains(".m3u8") || abs.contains("master")) {
                emitHls(name, name, abs, "https://javclan.com/", callback)
            } else {
                emitVideo(name, name, abs, "https://javclan.com/", callback)
            }
            found = true
        }
        if (found) return true

        // Fallback terakhir: m3u8/mp4 langsung di HTML (tanpa unpack).
        val direct = findM3u8OrMp4(all.toString())
        if (direct != null) {
            val abs = resolveUrl("https://javclan.com/", direct)
            if (abs.contains(".m3u8")) {
                emitHls(name, name, abs, "https://javclan.com/", callback)
            } else {
                emitVideo(name, name, abs, "https://javclan.com/", callback)
            }
            return true
        }
        return false
    }

    /** Ekstrak URL stream dari objek `links` (hls2/hls3/hls4) pada JS player. */
    private fun resolveHlsLinks(js: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("""["'](hls2|hls3|hls4|hls)["']\s*:\s*["']([^"']+)["']""").findAll(js).forEach {
            out.add(it.groupValues[2])
        }
        // Fallback: URL m3u8/mp4 absolut apa pun.
        if (out.isEmpty()) {
            out.addAll(js.allMatches(Regex("""(https?://[^"'\s<>]+?\.(?:m3u8|mp4|txt)[^"'\s<>]*?)""")))
        }
        return out.toList()
    }

    private suspend fun parseListing(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html, mainUrl)
        val cardRe = Regex("^${Regex.escape(mainUrl)}/\\d+/[a-z0-9-]+/?$")
        return doc.select("a[href]").mapNotNull { a ->
            val abs = a.absUrl("href")
            if (!cardRe.containsMatchIn(abs)) return@mapNotNull null
            val title = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt") }
                ?.ifBlank { a.text() } ?: abs
            val code = extractJavCode(abs) ?: extractJavCode(title)
            newMovieSearchResponse(title, abs, TvType.NSFW) {
                posterUrl = listingPoster(code, anchorPoster(a))
            }
        }.distinctBy { it.url }
    }
}
