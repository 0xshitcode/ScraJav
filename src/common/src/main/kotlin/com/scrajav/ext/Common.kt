package com.scrajav.ext

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

// ---------- util regex ----------

fun String.firstMatch(re: Regex): String? = re.find(this)?.groupValues?.getOrNull(1)

fun String.allMatches(re: Regex): List<String> = re.findAll(this).map { it.groupValues[1] }.toList()

/** Selesaikan URL relatif/root-relatif terhadap [base]. */
fun resolveUrl(base: String, url: String): String {
    if (url.startsWith("http")) return url
    val schemeHost = Regex("^https?://[^/]+").find(base)?.value
        ?: return base.trimEnd('/') + "/" + url.trimStart('/')
    return if (url.startsWith("/")) {
        schemeHost + url
    } else {
        base.substringBeforeLast('/') + "/" + url.trimStart('/')
    }
}

/** URL absolut pertama (m3u8 atau mp4) dalam HTML; fallback ke relatif. */
fun findM3u8OrMp4(html: String): String? {
    val absolute = html.firstMatch(Regex("""(https?://[^"'\s<>]+?\.(?:m3u8|mp4)[^"'\s<>]*?)"""))
    if (absolute != null) return absolute
    return html.firstMatch(Regex("""([^"'\s<>]+?\.(?:m3u8|mp4)[^"'\s<>]*?)"""))
}

val PACKED_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\)\{.*""", setOf(RegexOption.DOT_MATCHES_ALL))

/** Buka kunci (unpack) script packed ala Dean Edwards (dipakai cloudwish/dll). */
fun unpackPacked(html: String): String? {
    val packed = PACKED_REGEX.find(html)?.value ?: return null
    return runCatching { getAndUnpack(packed) }.getOrNull()
}

/** Decode base64 aman untuk minSdk 21 (java.util.Base64 baru API 26+). */
fun decodeBase64(s: String): String? = runCatching {
    String(android.util.Base64.decode(s, android.util.Base64.DEFAULT))
}.getOrNull()

// ---------- builder link ----------

private suspend fun buildLink(
    source: String,
    name: String,
    url: String,
    referer: String,
    m3u8: Boolean,
    quality: Int,
): ExtractorLink = newExtractorLink(source, name, url, if (m3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
    this.quality = quality
    this.referer = referer
    headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
}

suspend fun hlsLink(source: String, name: String, url: String, referer: String): ExtractorLink =
    buildLink(source, name, url, referer, m3u8 = true, quality = Qualities.Unknown.value)

suspend fun videoLink(source: String, name: String, url: String, referer: String): ExtractorLink =
    buildLink(source, name, url, referer, m3u8 = false, quality = Qualities.Unknown.value)

/**
 * Emit stream HLS dengan dukungan **multi-resolusi**: jika [url] adalah master
 * playlist (#EXT-X-STREAM-INF), fetch & parse semua variant lalu emit satu link
 * per kualitas. Jika media playlist/URL tunggal → emit apa adanya.
 */
suspend fun emitHls(
    source: String,
    name: String,
    url: String,
    referer: String,
    callback: (ExtractorLink) -> Unit,
) {
    val variants = runCatching { parseMasterM3u8(url, referer) }.getOrDefault(emptyList())
    if (variants.isEmpty()) {
        callback(buildLink(source, name, url, referer, m3u8 = true, quality = Qualities.Unknown.value))
        return
    }
    variants.forEach { (vUrl, res) ->
        val label = if (res > 0) "${res}p" else name
        callback(buildLink(source, label, vUrl, referer, m3u8 = true, quality = res))
    }
}

suspend fun emitVideo(
    source: String,
    name: String,
    url: String,
    referer: String,
    callback: (ExtractorLink) -> Unit,
) {
    callback(buildLink(source, name, url, referer, m3u8 = false, quality = Qualities.Unknown.value))
}

/** Fetch master m3u8 → daftar (url-variant, resolusi-int). Empty jika bukan master. */
private suspend fun parseMasterM3u8(masterUrl: String, referer: String): List<Pair<String, Int>> {
    val text = runCatching { app.get(masterUrl, referer = referer).text }.getOrNull() ?: return emptyList()
    if (!text.contains("#EXT-X-STREAM-INF")) return emptyList()
    val re = Regex(
        """#EXT-X-STREAM-INF:[^\n]*?(?:RESOLUTION=(\d{2,5})x\d+)?[^\n]*\n\s*([^\s\n]+)"""
    )
    val out = re.findAll(text).map { m ->
        val res = m.groupValues[1].toIntOrNull() ?: 0
        val raw = m.groupValues[2]
        val abs = if (raw.startsWith("http")) raw else resolveUrl(masterUrl, raw)
        abs to res
    }.filter { (u, _) -> u.endsWith(".m3u8") || u.contains("m3u8") }.toList()
    if (out.isEmpty()) return emptyList()
    // Kunci berdasarkan URL + resolusi agar tidak ada duplikat.
    return out.distinctBy { it.first to it.second }
}

/**
 * Ikuti halaman embed (turbovid/etvp/cloudwish/earnvid dll) dan laporkan stream.
 * Urutan: m3u8/mp4 langsung di HTML -> unpack packed JS -> extractor bawaan CloudStream.
 * Stream m3u8 diproses dengan [emitHls] agar multi-resolusi tetap keluar.
 */
suspend fun resolveEmbedGeneric(
    embedUrl: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val html = runCatching { app.get(embedUrl).text }.getOrNull() ?: return false

    val direct = findM3u8OrMp4(html)
    if (direct != null) {
        if (direct.contains(".m3u8")) {
            emitHls(embedUrl.host, embedUrl.host, direct, embedUrl, callback)
        } else {
            emitVideo(embedUrl.host, embedUrl.host, direct, embedUrl, callback)
        }
        return true
    }

    val unpacked = unpackPacked(html)
    if (unpacked != null) {
        val d = findM3u8OrMp4(unpacked)
        if (d != null) {
            if (d.contains(".m3u8")) {
                emitHls(embedUrl.host, embedUrl.host, d, embedUrl, callback)
            } else {
                emitVideo(embedUrl.host, embedUrl.host, d, embedUrl, callback)
            }
            return true
        }
    }

    return loadExtractor(embedUrl, subtitleCallback, callback)
}

val String.host: String
    get() = Regex("^https?://([^/]+)").find(this)?.groupValues?.getOrNull(1) ?: this

// ---------- kode JAV & metadata global ----------

/** Pola kode JAV: SSIS-406, MIAA-001, IPZZ-904, 259LUXU-1894, dst. */
val JAV_CODE_REGEX = Regex("""\b([A-Z0-9]{2,9}-\d{2,6})\b""")

fun extractJavCode(text: String?): String? =
    text?.let { JAV_CODE_REGEX.find(it)?.groupValues?.getOrNull(1)?.uppercase() }

/** Metadata global R18 (dipakai semua provider bila metadata sumber kosong). */
data class R18Meta(
    val title: String? = null,
    val posterUrl: String? = null,
    val actresses: List<String> = emptyList(),
    val studio: String? = null,
    val release: String? = null, // yyyy-mm-dd
    val genres: List<String> = emptyList(),
)

/**
 * Sumber metadata global: jav.guru (WordPress, tidak CF-protected, metadata
 * terlengkap — Code/Release Date/Studio/Label/Actress/Tags; terverifikasi probe).
 * Pencarian per kode → halaman detail → parse. Hasil di-cache per sesi.
 */
object R18Metadata {
    private const val SOURCE = "https://jav.guru"

    private val cache = java.util.concurrent.ConcurrentHashMap<String, R18Meta>()
    private val negative = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun lookup(code: String): R18Meta? {
        cache[code]?.let { return it }
        if (negative.contains(code)) return null
        val meta = runCatching { fetch(code) }.getOrNull()
        if (meta != null) cache[code] = meta else negative.add(code)
        return meta
    }

    private suspend fun fetch(code: String): R18Meta? {
        val searchHtml = runCatching {
            app.get("$SOURCE/?s=${code.encodeUri()}", referer = SOURCE).text
        }.getOrNull() ?: return null
        val doc = Jsoup.parse(searchHtml, SOURCE)
        val detailLink = doc.select("a[href]").map { it.absUrl("href") }
            .firstOrNull { Regex("^${Regex.escape(SOURCE)}/\\d+/[a-z0-9]+-").containsMatchIn(it) }
            ?: return null
        val detailHtml = runCatching { app.get(detailLink, referer = SOURCE).text }.getOrNull()
            ?: return null
        val d = Jsoup.parse(detailHtml, SOURCE)

        val title = d.selectFirst("meta[property=og:title]")?.attr("content")
            ?: d.selectFirst("h1")?.text()
        val poster = d.selectFirst("meta[property=og:image]")?.attr("content")

        // Baris info: <li><strong>Label:</strong> ... <a>Value</a></li>
        val info = d.select("li").mapNotNull { li ->
            val strong = li.selectFirst("strong")?.text() ?: return@mapNotNull null
            val value = li.text().substringAfter(":", "").trim()
            strong to value
        }.filter { it.second.isNotEmpty() }

        fun pick(label: String): String? = info.firstOrNull { it.first == label }?.second

        val studio = pick("Studio") ?: pick("Label")
        val release = pick("Release Date")
        val actresses = d.select("li").mapNotNull { li ->
            val strong = li.selectFirst("strong")?.text()
            if (strong == "Actress") li.select("a").map { it.text() }.filter { it.isNotBlank() } else null
        }.flatten().distinct()
        val genres = d.select("li").mapNotNull { li ->
            val strong = li.selectFirst("strong")?.text()
            if (strong == "Tags" || strong == "Category") li.select("a").map { it.text() }.filter { it.isNotBlank() } else null
        }.flatten().distinct()

        return R18Meta(
            title = title,
            posterUrl = poster,
            actresses = actresses,
            studio = studio,
            release = release,
            genres = genres,
        )
    }
}

/**
 * Isi field LoadResponse yang kosong dari metadata global (R18Metadata).
 * Hanya melakukan lookup bila kode JAV ditemukan & metadata penting masih kosong.
 */
suspend fun MovieLoadResponse.enrichGlobal() {
    val code = extractJavCode(url) ?: extractJavCode(name) ?: return
    val meta = R18Metadata.lookup(code) ?: return

    if (posterUrl.isNullOrBlank() && !meta.posterUrl.isNullOrBlank()) posterUrl = meta.posterUrl
    if ((name.isBlank() || name == url) && !meta.title.isNullOrBlank()) name = meta.title ?: name
    if (tags.isNullOrEmpty()) tags = meta.genres
    if (year == null) year = meta.release?.take(4)?.toIntOrNull()

    // Plot: selalu tampilkan info kaya (kode + aktris + studio + rilis).
    val lines = buildList {
        add("Kode: $code")
        if (meta.actresses.isNotEmpty()) add("Aktris: ${meta.actresses.joinToString(", ")}")
        if (!meta.studio.isNullOrBlank()) add("Studio: ${meta.studio}")
        if (!meta.release.isNullOrBlank()) add("Rilis: ${meta.release}")
    }
    val current = plot
    if (current.isNullOrBlank() || current.startsWith("Kode:")) plot = lines.joinToString("\n")
}
