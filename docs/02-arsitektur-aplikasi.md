# 🏛️ 02 — Arsitektur Aplikasi (Kotlin native, gaya CloudStream)

> Keputusan user: **Kotlin native**, pelajari cara kerja CloudStream & pemutarnya.
> Dokumen ini berisi: (A) bagaimana CloudStream bekerja, (B) arsitektur aplikasi kita,
> (C) desain provider/ekstraktor, (D) pemutar video, (E) penanganan anti-bot, (F) referensi kode.

---

## A. Bagaimana CloudStream Bekerja (dari repo `recloudstream/cloudstream`)

CloudStream 3 (repo: `github.com/recloudstream/cloudstream`, Kotlin + Jetpack Compose) bekerja
dengan model **provider sebagai plugin (extension)**:

```
┌────────────────────── CloudStream App ──────────────────────┐
│  UI (Compose): Home · Search · Detail · Player · Settings    │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Provider API (MainAPI abstract class)                  │ │
│  │  • search(query, page) → List<SearchResponse>          │ │
│  │  • load(url) → LoadResponse (Movie/TvSeries/...)       │ │
│  │  • loadVideoData(url) → VideoData                      │ │
│  │  • extractVideo(...) → List<ExtractedLink> (streams)   │ │
│  └────────────────────────────────────────────────────────┘ │
│  Player: ui/player/ (ExoPlayer-based, lihat bagian D)       │
│  Ekstraktor embed: JWPlayer, GMPlayer, Zplayer, dll         │
│  Sync: Trakt/Kitsu/MAL/AniList/OpenSubtitles (syncproviders)│
└─────────────────────────────────────────────────────────────┘
        ▲
        │ reposync / instal APK
   ┌────┴─────┐
   │ Extension │ = APK terpisah berisi 1+ provider + metadata.json
   └──────────┘    (nama, ikon, versi, dll) — dikompilasi dengan
                   CloudStreamExtension API (library `cloudstream3`)
```

**Alur pemutaran video:**
1. User memilih hasil search → `search()` mengembalikan `SearchResponse` (judul, gambar, url).
2. `load(url)` → `LoadResponse` berisi metadata lengkap + daftar episode (untuk series).
3. `loadVideoData(episodeUrl)` → `VideoData` (berisi source) → `extractVideo()`.
4. `extractVideo()` memanggil `ExtractorLink` untuk **setiap source** (m3u8/mp4/embed) dan
   mengembalikan daftar `ExtractedLink` (url, kualitas, tipe, dan **headers custom**).
5. Player menerima list tersebut → `PlayerGeneratorViewModel` → memutar lewat ExoPlayer/Media3.

**Detail penting dari struktur repo (terverifikasi via tree):**
- `ui/player/` berisi: `CS3IPlayer.kt` (interface player), `IPlayer.kt`, `GeneratorPlayer.kt`,
  `LinkGenerator.kt`, `PlayerView.kt`, `FullScreenPlayer.kt`, `PlayerPipHelper.kt` (PiP),
  `PlayerSubtitleHelper.kt` + `CustomSubtitleDecoderFactory.kt` (subtitle), `SSLTrustManager.kt`
  (penanganan sertifikat), `PreviewGenerator.kt`, `OfflinePlaybackHelper.kt` +
  `DownloadFileGenerator.kt` (download offline).
- `extractors/` (di fork `Jacekun/CloudStream-3XXX`): `JWPlayer.kt`, `GMPlayer.kt`,
  `PlayerVoxzer.kt`, `Zplayer.kt` — **ekstraktor generik untuk player embed pihak ketiga**.
- Fork `Jacekun/CloudStream-3XXX` menambah `animeproviders/` + provider dewasa + `site-list.py`.

> Kesimpulan: kita **tidak perlu menulis semuanya dari nol** — bisa fork
> `recloudstream/cloudstream` (atau `Jacekun/CloudStream-3XXX`) dan menambah provider kita,
> ATAU membangun app sendiri dengan pola API provider yang sama (lebih bersih, kontrol penuh).

---

## B. Arsitektur Aplikasi Kita (rekomendasi)

```
┌─────────────────────────────────────────────────────────────┐
│  app (Kotlin + Jetpack Compose, minSdk 26, target 35+)      │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ UI Layer                                               │ │
│  │  HomeScreen (per-sumber) · SearchScreen · DetailScreen │ │
│  │  PlayerScreen · DownloadsScreen · HistoryScreen ·      │ │
│  │  SettingsScreen (domain rotation, UA, cache)           │ │
│  ├────────────────────────────────────────────────────────┤ │
│  │ Domain Layer                                           │ │
│  │  ProviderRegistry  (daftar 14 provider)                │ │
│  │  JavProvider (interface)  ── search / home / detail /  │ │
│  │                            extractVideo                │ │
│  │  ExtractorResult → VideoSource(url, quality, type,     │ │
│  │                      headers: Map<String,String>)      │ │
│  ├────────────────────────────────────────────────────────┤ │
│  │ Data Layer                                             │ │
│  │  L1 DirectParser (OkHttp + Jsoup/regex)                │ │
│  │  L2 WebViewInterceptor (WebView shouldInterceptRequest)│ │
│  │  L3 RemoteProxyClient (ke server kita, opsional)       │ │
│  │  Cache (URL video TTL pendek, gambar, riwayat)         │ │
│  │  PersistentCookieJar (OkHttp)                          │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Modul Gradle:**
- `:app` — UI, navigasi, player, download.
- `:provider-api` — interface `JavProvider`, model (`JavSearchResult`, `JavDetail`,
  `VideoSource`), util parser.
- `:providers` — implementasi per situs (bisa dipecah per provider).
- `:player` — wrapper Media3 + UI player.

---

## C. Desain Provider (interface)

```kotlin
interface JavProvider {
    val id: String            // "omgjav"
    val name: String          // "OMGJAV"
    val baseUrls: List<String>// daftar mirror (rotasi domain)
    val extractionLevel: Level // L1 / L2 / L3

    suspend fun home(): List<JavSearchResult>
    suspend fun search(query: String, page: Int): List<JavSearchResult>
    suspend fun detail(url: String): JavDetail          // metadata + server list
    suspend fun extractVideo(detail: JavDetail): List<VideoSource>
}
```

`VideoSource` membawa headers-nya sendiri karena **setiap CDN butuh Referer berbeda**:
```kotlin
data class VideoSource(
    val url: String,          // master.m3u8 atau mp4
    val quality: String?,     // "1080p", "hq", dst
    val type: String,         // "m3u8" | "mp4"
    val headers: Map<String, String> = emptyMap() // Referer/Origin/UA
)
```

**Strategi ekstraksi 3 lapis** (sesuai temuan riset):

| Lapisan | Cara kerja | Cocok untuk |
|---|---|---|
| **L1 — Parse langsung** | OkHttp GET halaman + Jsoup/regex → ambil m3u8/mp4 dari HTML/JSON tertanam | omgjav, supjav (via API `supjav.php`), javtsunami (via embedURL), jav.guru, 123av (sebagian) |
| **L2 — WebView intercept** | Muat halaman di WebView headless (off-screen), tangkap request m3u8/mp4 lewat `shouldInterceptRequest` + sinkronisasi cookie | missav, javhd.today, situs JS-rendered/CF ringan. **Pola ini sudah terbukti di `Discover999/123AV_app`** |
| **L3 — Proxy server** (opsional) | Server sendiri (Node/Deno/Python) yang scraping dengan impersonation TLS (`curl_cffi`), lalu app tinggal minta URL | missav (CF berat), situs yang menolak datacenter IP. Pola: `supjavd`, `missav-stream` |

> Rekomendasi: semua provider **mulai dari L1**, naik ke L2 bila gagal, dan L3 hanya untuk
> kasus yang benar-benar butuh (missav). Hindari server kalau bisa — lebih sederhana & privat.

---

## D. Pemutar Video (cara CloudStream & rekomendasi kita)

CloudStream memakai **ExoPlayer/Media3** dengan UI player custom. Komponen penting yang kita tiru:
- **Media3 ExoPlayer** + `HlsMediaSource` untuk m3u8, `ProgressiveMediaSource` untuk mp4.
- **Header per-sumber**: `DefaultHttpDataSource.Factory` dengan
  `setDefaultRequestProperties(headers)` — **wajib** karena CDN minta Referer/Origin/UA.
- **Multi-kualitas**: parse `master.m3u8` → daftar `#EXT-X-STREAM-INF` → pilihan resolusi.
- Fitur yang direncanakan (meniru CloudStream `ui/player/`):
  - PiP (`PlayerPipHelper`), gesture kontrol, subtitle (CustomSubtitleDecoder), preview
    thumbnail (`PreviewGenerator`), cast, download offline (`OfflinePlaybackHelper`),
    penanganan SSL trust khusus (`SSLTrustManager`) bila CDN punya sertifikat bermasalah.
- **Perilaku error**: URL expired (403) → otomatis `extractVideo()` ulang → coba server lain
  (pola supjav: ada peta server "TV" dll) → coba mirror domain lain.

---

## E. Penanganan Anti-Bot & Robustness

1. **Cookie jar persisten** (OkHttp `CookieJar` ke SharedPreferences/Room) — penting untuk situs
   yang butuh cookie sesi/cf_clearance.
2. **UA realistis** per provider (bukan default library).
3. **Cache URL video TTL pendek** (5–30 menit, sesuai masa token) + cache gambar.
4. **Rotasi domain**: setiap provider simpan daftar mirror; gagal → coba mirror berikutnya.
5. **Rate-limit diri sendiri**: jeda antar-request, jangan scrape berlebihan (hormati situs).
6. **Error taxonomy**: `SiteBlocked` (CF/Turnstile) → naik ke L2/L3; `VideoExpired` → re-extract;
   `NotFound` → coba mirror.
7. **Jangan pernah hardcode URL video** — selalu ekstrak saat itu juga.

---

## F. Referensi Kode (repo yang dibaca saat riset)

| Topik | Repo | File kunci |
|---|---|---|
| Provider API CloudStream | `recloudstream/cloudstream` | `apiprovider/`, `ui/player/` |
| Fork + extractor embed | `Jacekun/CloudStream-3XXX` | `extractors/JWPlayer.kt`, `GMPlayer.kt`, `Zplayer.kt`, `PlayerVoxzer.kt` |
| Ekstraksi supjav (blueprint) | `xbol0/supjavd` | `supjav.ts` (handler, getM3U8ById, extractMediaList) |
| WebView intercept (Android) | `Discover999/123AV_app` | `network/ImprovedVideoUrlFetcher.kt`, `NetworkConfig.kt` |
| Scraper multi-situs (server) | `Snowball-01/AdultColony-API` | `src/services/scrapers/{javtsunami,javhdtoday,missav}/*` |
| MissAV (Python, HLS) | `EchterAlsFake/unofficial-api-for-missav` | `missav_api` client + downloader |

> Saat implementasi dimulai, langkah pertama adalah **membaca file-file kunci di atas** dan
> menyesuaikan ke arsitektur kita (bukan menyalin mentah).
