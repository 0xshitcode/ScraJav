package com.scrajav.ext

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

// ---------- util regex ----------

fun String.firstMatch(re: Regex): String? = re.find(this)?.groupValues?.getOrNull(1)

fun String.allMatches(re: Regex): List<String> = re.findAll(this).map { it.groupValues[1] }.toList()

/** Selesaikan URL relatif/root-relatif terhadap [base] (RFC 3986). */
fun resolveUrl(base: String, url: String): String = try {
    java.net.URL(base).resolve(url).toString()
} catch (_: Exception) {
    if (url.startsWith("http")) url else base.trimEnd('/') + "/" + url.trimStart('/')
}

/** URL absolut pertama (m3u8 atau mp4) dalam HTML; fallback ke relatif. */
fun findM3u8OrMp4(html: String): String? {
    val absolute = html.firstMatch(Regex("""(https?://[^"'\s<>]+?\.(?:m3u8|mp4)[^"'\s<>]*)"""))
    if (absolute != null) return absolute
    return html.firstMatch(Regex("""([^"'\s<>]+?\.(?:m3u8|mp4)[^"'\s<>]*)"""))
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

fun hlsLink(source: String, name: String, url: String, referer: String): ExtractorLink =
    newExtractorLink(source, name, url, ExtractorLinkType.M3U8) {
        quality = Qualities.Unknown.value
        this.referer = referer
        headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
    }

fun videoLink(source: String, name: String, url: String, referer: String): ExtractorLink =
    newExtractorLink(source, name, url, ExtractorLinkType.VIDEO) {
        quality = Qualities.Unknown.value
        this.referer = referer
        headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
    }

/**
 * Ikuti halaman embed (turbovid/etvp/cloudwish/earnvid dll) dan laporkan stream.
 * Urutan: m3u8/mp4 langsung di HTML -> unpack packed JS -> extractor bawaan CloudStream.
 */
suspend fun resolveEmbedGeneric(
    embedUrl: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val html = runCatching { app.get(embedUrl).text }.getOrNull() ?: return false

    val direct = findM3u8OrMp4(html)
    if (direct != null) {
        val isM3u8 = direct.contains(".m3u8")
        callback(
            if (isM3u8) hlsLink(embedUrl.host, embedUrl.host, direct, embedUrl)
            else videoLink(embedUrl.host, embedUrl.host, direct, embedUrl)
        )
        return true
    }

    val unpacked = unpackPacked(html)
    if (unpacked != null) {
        val d = findM3u8OrMp4(unpacked)
        if (d != null) {
            val isM3u8 = d.contains(".m3u8")
            callback(
                if (isM3u8) hlsLink(embedUrl.host, embedUrl.host, d, embedUrl)
                else videoLink(embedUrl.host, embedUrl.host, d, embedUrl)
            )
            return true
        }
    }

    return loadExtractor(embedUrl, subtitleCallback, callback)
}

val String.host: String
    get() = Regex("^https?://([^/]+)").find(this)?.groupValues?.getOrNull(1) ?: this
