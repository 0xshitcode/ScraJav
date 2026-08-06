# 🔬 01 — Hasil Riset Lengkap 14 Sumber Video

> Dokumen ini adalah hasil penyelidikan teknis (endpoint API, mekanisme player, metode
> ekstraksi stream) untuk 14 situs. **Semua data dikumpulkan Agustus 2026** — karena situs-situs
> ini sering berubah, anggap dokumen ini *snapshot* yang harus diverifikasi ulang saat implementasi.
>
> Status verifikasi:
> - ✅ **TERVERIFIKASI** — berasal dari source code scraper yang dibaca langsung, atau diuji live.
> - ⚠️ **HIPOTESIS** — tidak dapat diverifikasi dari jaringan riset (diblokir/down), berdasar
>   kesamaan pola dengan situs lain. Harus diverifikasi saat development.

---

## Ringkasan Tabel 14 Situs

| # | Situs | Keluarga | Stack | Metode Ekstraksi | Anti-bot | Status |
|---|---|---|---|---|---|---|
| 1 | omgjav.com | A | Custom (Next.js-style, Tailwind) | Parse JSON `hosts[]` di halaman → m3u8 | Ringan | ✅ Live-tested |
| 2 | supjav.com | B | post-ID `.html` + backend `lk1.supremejav.com` | API `supjav.php?c={token-terbalik}` → m3u8 | UA + Referer | ✅ Source-diverifikasi |
| 3 | missav.ws | C | Next.js-style, JS-rendered | HTML + HLS m3u8 CDN (header Referer/Origin) | **Cloudflare Turnstile** | ✅ Source-diverifikasi |
| 4 | jav.guru | B | WordPress | Search `/?s=`; stream **HLS** (lokasi m3u8 perlu verifikasi) | UA | ✅ Source-diverifikasi |
| 5 | javhd.today | (custom) | Custom (Bootstrap-era) | Endpoint `/embed/{id}` → player; pola lama: URL mp4 di `div.player-container` | Vercel checkpoint | ✅ Source-diverifikasi |
| 6 | javtsunami.com | B | WordPress | `meta[itemprop=embedURL]` → embed player | Ringan | ✅ Source-diverifikasi |
| 7 | 123av.com | (custom) | Agregator Mandarin custom | Parse HTML + **WebView intercept** (referensi: app Android open-source) | JS | ✅ Source-diverifikasi |
| 8 | javgg.net | A (diduga) | Custom | Diduga JSON `hosts[]` seperti omgjav | Vercel/CF | ⚠️ Hipotesis |
| 9 | javmost.ws | A (diduga) | Custom | Diduga JSON `hosts[]` | ? | ⚠️ Hipotesis |
| 10 | javseen.tv | A (diduga) | Custom | Diduga JSON `hosts[]` | ? | ⚠️ Hipotesis |
| 11 | roshy.tv | ? | ? | Tidak ada data | ? | ❌ Tidak ditemukan |
| 12 | javmit.com | ? | ? | Tidak ada data | ? | ❌ Tidak ditemukan |
| 13 | javbraze.com | ? | ? | Tidak ada data | ? | ❌ Tidak ditemukan |
| 14 | sextb.cc | ? | ? | Tidak ada scraper publik berfungsi | ? | ❌ Tidak ditemukan |

---

## 1. omgjav.com — ✅ TERVERIFIKASI (diuji live)

**Stack:** Situs custom (bukan WordPress). Class Tailwind (`size-full md:rounded-md`), skrip iklan
`//acscdn.com/script/aclib.js`. Server menanggapi permintaan polos (curl) dengan HTTP 200.

**Struktur URL:**
| Fungsi | URL |
|---|---|
| Home | `https://omgjav.com/` |
| Listing | `https://omgjav.com/search/hottest`, `/search/newest` |
| Kategori | `https://omgjav.com/categories` |
| Aktris | `https://omgjav.com/actresses` |
| Studio | `https://omgjav.com/studios` |
| **Halaman video** | `https://omgjav.com/v/{KODE}` — contoh nyata: `/v/259LUXU-1894` |

**Metode ekstraksi stream (sangat sederhana):**
1. `GET https://omgjav.com/v/{KODE}` dengan User-Agent browser.
2. Parse JSON yang tertanam di HTML halaman (objek konfigurasi player). Struktur yang teramati:

```json
{
  "timeline": { "video": "https://cdn.omgjav.com/259LUXU-1894/preview.webm" },
  "hosts": [
    { "name": "BETA", "media": { "src": "https://cdn.101111001.xyz/{token}/master.m3u8",
                                 "type": "m3u8" } }
  ],
  "videoId": "259LUXU-1894",
  "performers": ["Amane Misyeru"],
  "releaseDate": "2026-08-06T00:00:00.000Z"
}
```

3. Ambil `hosts[].media.src` → URL **master.m3u8**. CDN = `cdn.101111001.xyz` (domain acak per video,
   token kemungkinan bertanda-tangan/kedaluwarsa). Preview webm di `cdn.omgjav.com/{kode}/preview.webm`.

**Anti-bot:** praktis tidak ada (berhasil diakses dengan curl polos). Mungkin ada keperluan
header `Referer: https://omgjav.com/` untuk segmen CDN — verifikasi saat implementasi.

**Tingkat kesulitan:** ⭐ (termudah) — kandidat utama provider pertama.

---

## 2. supjav.com — ✅ TERVERIFIKASI (dari source `xbol0/supjavd/supjav.ts`)

**Stack:** Skema URL post-ID `https://supjav.com/{id}.html` (mirip WordPress). Ada subdirektori
bahasa: `/zh/`, `/ja/`. Backend stream terpisah di `lk1.supremejav.com`.

**Struktur URL:**
| Fungsi | URL |
|---|---|
| Detail | `https://supjav.com/{id}.html` (contoh: `/187772.html`) |
| Popular | `https://supjav.com/popular?sort=day\|week\|month` |
| Kategori | `https://supjav.com/category/{slug}/` |
| Maker | `https://supjav.com/category/maker/{slug}/` |
| Cast | `https://supjav.com/category/cast/{slug}/` |
| Tag | `https://supjav.com/tag/{slug}/` |
| Search | `https://supjav.com/?s={query}` |
| Pagination | `/{path}/page/{n}/` |

**Metode ekstraksi stream (blueprint lengkap dari supjavd):**
1. `GET https://supjav.com/{id}.html` dengan header `Referer: https://supjav.com/` dan UA.
2. Parse elemen `data-link="..."` pada halaman → peta server:

```
regex: /data-link\=".*?">.*?</mg
  val = nilai atribut data-link (token)
  key = teks antara ">" dan "<" (nama server, mis. "TV")
```

3. `tvid = serverMap.TV` **dibalik stringnya** (`split("").reverse().join("")`).
4. `GET https://lk1.supremejav.com/supjav.php?c={tvid-terbalik}`
   dengan header `Referer: https://supjav.com/` + UA.
5. Ambil m3u8 dari respons: `regex /urlPlay.*?(https.*?\.m3u8)/m`.
6. **Segmen CDN m3u8 wajib dikirim dengan header `Referer` = origin URL m3u8** (lihat pola proxy
   `/{cdnHost}/stream/{path}` di supjavd).

**Ekstraksi daftar video (listing/search):**
```
regex: /https:\/\/supjav\.com\/.*?\d+\.html.*?title=".*?".*?data-original=".*?"/gm
  id    = angka dari .../{id}.html
  title = atribut title
  thumb = atribut data-original (buang bagian "!..." jika ada)
```

**Anti-bot:** wajib User-Agent + Referer. Backend `lk1.supremejav.com` bisa ganti — jadikan config.

**Catatan penting:** Repo `zainaqdas/supjav` (Next.js 15 + TS, "JavOnlineHd") ternyata men-scrape
**javtiful.com** (situs upstream/sibling), bukan supjav.com. Route yang terdokumentasi:
`/main`, `/videos`, `/trending`, `/censored`, `/uncensored`, `/reducing-mosaic`, `/categories`,
`/category/:slug`, `/actresses`, `/actress/:slug`, `/channels`, `/channel/:slug`, `/search?q=...`,
`/video/:id/:slug`, dan **`/api/video/:id/stream`** (stream dari Cloudflare R2). Ini alternatif
yang bisa dieksplorasi bila supjav.com sulit.

**Tingkat kesulitan:** ⭐⭐ (mudah, butuh langkah bertingkat).

---

## 3. missav.ws — ✅ TERVERIFIKASI (dokumentasi + repos)

**Status domain (penting!):** `missav.com` **di-takedown melalui proses hukum** (domain seizure).
Domain aktif saat riset: **missav.ws** (utama), **missav.ai**, **missav.live**. Ganti-ganti —
harus jadi konfigurasi yang bisa dirotasi.

**Stack:** Frontend JS-rendered (class Tailwind palet Nord: `text-nord6`, `text-nord13`).
**Dilindungi Cloudflare Turnstile** (bot check) di banyak path.

**Struktur URL:**
| Fungsi | URL |
|---|---|
| Halaman video | `https://{domain}/{KODE}` (contoh: `/SSIS-406`) |
| Search | `/search/{query}` (perlu verifikasi) |
| Listing | `/new`, `/trending`, kategori (`/genre/{slug}`), aktris (`/actress/{slug}`) |

**Metadata (scraped dari HTML):**
- Judul: `h1.text-base.lg:text-lg.text-nord6` atau `og:title`
- Poster: `og:image` / `video.player[data-poster]` / `video[data-poster]`
- Durasi: `og:video:duration`; Rilis: `og:video:release_date`
- Genre: `div.text-secondary` yang memuat "Genre:" → link `a.text-nord13`

**Metode ekstraksi stream (dari Mark111112/missav-stream + missAV API):**
1. Halaman video hanya bisa dibaca setelah JS jalan / lolos Turnstile → butuh **headless browser
   (Puppeteer/Playwright)** atau **impersonation TLS** (`curl_cffi` dengan JA3 Chrome).
2. M3u8 berada di CDN pihak ketiga, contoh: `https://surrit.com/{xxx}/1080p/video.m3u8`.
3. **Header wajib untuk segmen:**
   ```
   Referer: https://{domain}/{KODE}
   Origin:  https://{domain}
   User-Agent: Mozilla/5.0 (… Chrome/131 …) Safari/537.36
   ```
4. **MissAV tidak mendukung HTTP/2** — klien harus HTTP/1.1 (ini penemuan penting dari missAV API).
5. Multi-kualitas dari master playlist (720p, 1080p, dst).

**Referensi repo:**
- `EchterAlsFake/unofficial-api-for-missav` — Python async wrapper + HLS downloader (resume,
  kualitas best/half/worst), caching, HTTP/1.1, browser impersonation via `curl_cffi`.
- `Cat-Ling/missAV_api` & `EchterAlsFake/missAV_api` — `pip install missAV_api`.
- `gakkiyomi/MissAV-API` ("misscore") — REST FastAPI `/api/v1/movie/{tipe}/{id}?page=` + `auth-key`.
- `Mark111112/missav-stream` — service resolver: `GET /api/resolve/{movie_id}?quality=1080p` →
  `{stream_url, playback.headers}`. Contoh nyata header di atas.
- `austinjklim/MissAV-Downloader`, `nerdnam/missav-dlp-web`.

**Anti-bot:** **berat** — Cloudflare Turnstile + TLS fingerprinting + domain seizure. Strategi:
WebView intercept di Android, atau proxy server sendiri (pola `missav-stream`/`curl_cffi`).

**Tingkat kesulitan:** ⭐⭐⭐⭐ (paling sulit dari 14 situs).

---

## 4. jav.guru — ✅ TERVERIFIKASI (dari `SubMaRk/javguru` + `Hiro8541/javguru-downloader`)

**Stack:** WordPress (tema GeneratePress — selector `.inside-article`).

**Struktur URL & scraping metadata:**
| Fungsi | URL / selector |
|---|---|
| Search | `https://jav.guru/?s={query}` |
| Pagination search | `https://jav.guru/page/{n}/?s={query}` |
| Item listing | `.grid1 a` (href = URL detail) |
| Jumlah halaman | `.pages` |
| Detail | `.inside-article` → `.infoleft li` (7 field: **ID Code, Release Date, Tags, Series, Actress, Studio Label** + 1) |
| Judul | `.titl` |
| Cover | `.large-screenimg img` (src) |

Contoh skrip Python (`javguru.py`) juga menunjukkan: User-Agent Chrome wajib, dan di versi lama
ada pengaturan TLS cipher khusus (`TLS13-CHACHA20-...`) — kemungkinan untuk melewati filter TLS.

**Metode ekstraksi stream:** **HLS (m3u8)** — dikonfirmasi oleh `Hiro8541/javguru-downloader`
("JavGuru uses HLS"). Lokasi pasti m3u8 (di halaman HTML / script player / API internal) **belum
diekstrak dari source yang ditemukan** → wajib diverifikasi saat development (buka DevTools,
filter `.m3u8`).

**Anti-bot:** ringan (UA + TLS cipher).

**Tingkat kesulitan:** ⭐⭐ (metadata mudah; m3u8 perlu verifikasi 1 langkah).

---

## 5. javhd.today — ✅ TERVERIFIKASI (dari `AdultColony-API` + `Get-link-JAVHD`)

**Stack:** custom (class Bootstrap-era: `.col-xs-12.col-sm-6.col-md-8`), bukan WordPress.
Dari jaringan riset terlihat "Vercel Security Checkpoint" di beberapa path.

**Struktur halaman detail (selector dari AdultColony):**
- Judul: `div.content-container h1`
- Gambar: `div[style='display: inline-grid;'] img`
- Genre: `.col-xs-12.col-sm-6.col-md-8` (pertama) → `a`
- Rilis: `Release Day:\s*(\d{4}-\d{2}-\d{2})`
- Negara: link berisi `tag/japan-sex`
- Deskripsi: `.description`
- **Player:** endpoint **`https://javhd.today/embed/{id}`** (di luar halaman detail)

**Metode ekstraksi stream:**
1. `GET {site}/embed/{id}` → halaman player → ambil m3u8/mp4 dari sana.
2. **Pola lama javhd.com** (dari `Get-link-JAVHD`, masih relevan sebagai referensi):
   - URL video disembunyikan di atribut `style` elemen `div.player-container`.
   - Format MP4: `http://cs92.wpc.alphacdn.net/802D70B/OriginJHVD/contents/{ID}/videos/{ID}_{hq|med|low}.mp4?cdn_hash={hash}&cdn_creation_time={ts}&cdn_ttl=1200&cdn_cv_memberid=14`
   - **Bertoken & kedaluwarsa** (`cdn_ttl=1200` → 20 menit), kualitas hq/med/low.

**Anti-bot:** sedang (Vercel checkpoint untuk bot; butuh UA normal + mungkin Referer).

**Tingkat kesulitan:** ⭐⭐⭐ (embed resolver + kemungkinan token).

---

## 6. javtsunami.com — ✅ TERVERIFIKASI (dari `AdultColony-API`)

**Stack:** WordPress.

**Selector (search):**
- Item: elemen `article` → `a[href]` (link), `div.post-thumbnail-container img[data-src]` (gambar),
  `header.entry-header span` (judul), `span.views`, `span.duration`.

**Selector (detail):**
- Judul: `div.title-block.box-shadow h1.entry-title`
- Gambar: `div.desc img[data-lazy-src]`
- Durasi: `meta[itemprop=duration]` (format ISO8601)
- Tanggal rilis: `meta[itemprop=uploadDate]`
- Tags: `div.tags-list a[title]`
- Deskripsi: `div.desc p`
- **Player:** `meta[itemprop=embedURL]` → URL embed yang berisi player sebenarnya
  (kemungkinan iframe ke host video; perlu 1 request lanjutan untuk m3u8).

`id` video = segmen path ke-3 dari URL (`url.split("/")[3]`).

**Anti-bot:** ringan.

**Tingkat kesulitan:** ⭐⭐ (metadata mudah; embed → stream perlu 1 langkah).

---

## 7. 123av.com — ✅ TERVERIFIKASI (dari aplikasi Android `Discover999/123AV_app`)

**Stack:** Agregator berbahasa Mandarin, custom. Field halaman detail (dalam Bahasa Mandarin):
`代码` (code), `发布日期` (rilis), `时长` (durasi), `女演员` (aktris), `制作人` (maker), `标签` (tag).
Navigasi punya menu "更多站点" (lebih banyak situs) → **situs ini bagian dari jaringan multi-situs**
(123av, dst). Tombol favorite: `button.favourite` dengan atribut `v-scope` berisi
`Favourite('movie', {id})` → framework JS custom (Alpine-like, `v-scope`).

**Referensi aplikasi Android lengkap (open-source):** `Discover999/123AV_app`
- Kotlin 2.0 + Jetpack Compose, SDK 36.
- Network: **OkHttp** dengan persistent cookie jar + cache 50MB + connection pool.
- Parsing: **Jsoup**.
- **Strategi pengambilan URL video berlapis** (`ImprovedVideoUrlFetcher`):
  1. Pakai `videoUrl` yang sudah ada / cache (TTL 30 menit).
  2. Parse HTTP langsung dari halaman.
  3. **WebView `shouldInterceptRequest`** — muat halaman di WebView dan tangkap request
     m3u8/mp4 di network layer (bypass JS & sebagian anti-bot).
  4. Fallback: parse "video part".
  - Plus: retry 3x dengan **exponential backoff**, cache URL, jalankan paralel.
- Download: `M3U8DownloadManager` (unduh HLS per-segment, database SQLite lokal).
- Fitur: watch history, kategori, pagination, PiP, tema, login (opsional), multi-situs
  (`KEY_SELECTED_SITE_ID`, default `123av_com`).

**Tingkat kesulitan:** ⭐⭐ (app referensi sudah ada; tinggal adaptasi).

---

## 8. javgg.net — ⚠️ HIPOTESIS (belum terverifikasi langsung)

- Repo scraper lama `javgg/watch-jav-online` (path `javgg.net/jav/`, file `jav.py`) saat ini
  hanya berisi *stub* (teks acknowledgment) — **tidak lagi berfungsi sebagai referensi teknis**.
- Tidak dapat diakses dari jaringan riset (DNS unreachable / Vercel security checkpoint).
- **Hipotesis kuat:** javgg.net berbagi platform dengan omgjav.com (JSON `hosts[]` tertanam di
  halaman video). Bila benar, ekstraksi = `GET https://javgg.net/...` → parse JSON → m3u8.
- **Wajib diverifikasi saat development** (buka halaman video, cari pola `hosts:` di HTML).

**Tingkat kesulitan (perkiraan):** ⭐⭐ jika hipotesis benar.

---

## 9–13. javmost.ws, javseen.tv, roshy.tv, javmit.com, javbraze.com — ⚠️ TIDAK TERVERIFIKASI

- **Tidak ditemukan scraper publik yang berfungsi** untuk kelima situs ini (pencarian GitHub,
  grep.app, dan repos komunitas).
- Semua tidak dapat dijangkau dari jaringan riset (diblokir di level jaringan/DNS datacenter).
- **Hipotesis:** javmost.ws & javseen.tv kemungkinan besar satu keluarga platform dengan
  omgjav/javgg (JSON `hosts[]`) — perlu konfirmasi visual/struktur saat development.
- roshy.tv, javmit.com, javbraze.com: sama sekali tidak ada data. Rencana: verifikasi manual
  (buka situs, DevTools, filter `.m3u8`/`.mp4`/`api`), lalu klasifikasikan.

**Tingkat kesulitan:** tidak diketahui — ⭐⭐ s/d ⭐⭐⭐ (verifikasi dulu).

---

## 14. sextb.cc — ⚠️ TIDAK TERVERIFIKASI

- Repo yang ditemukan ternyata kosong/stub: `Niaz264/Sextb` (hanya README + Dockerfile),
  `MequiPitbuLL/sextbt` (404), `hackerspecter/elysia-bun-sextbt` (template Elysia tanpa logika scraping).
- sextb adalah host video JAV terkenal; sextb.cc adalah agregator di atasnya.
- Tidak dapat dijangkau dari jaringan riset.
- **Rencana:** verifikasi manual struktur halaman; klasifikasikan ke keluarga A/B/C.

**Tingkat kesulitan:** tidak diketahui.

---

## 🧩 Pola Umum yang Harus Dihormati oleh Arsitektur

1. **Semua URL video bertoken/kedaluwarsa** (5–30 menit). Jangan cache lama; simpan TTL pendek
   dan jangan pernah hardcode contoh URL.
2. **Header Referer/Origin/UA hampir selalu wajib** untuk CDN segment (m3u8 + .ts/.m4s).
3. **Anti-bot berlapis:** Vercel Security Checkpoint → Cloudflare Turnstile → TLS fingerprint
   (JA3) → JS-rendering. Lapisan ini yang membedakan tingkat kesulitan.
4. **Domain & backend API cepat berubah** (DMCA/seizure, pindah hosting):
   `missav.com → missav.ws/ai/live`, `javhd.com → javhd.today`, backend supjav `lk1.supremejav.com`.
   Semua base URL + backend harus di-file konfigurasi.
5. **Keluarga situs** (A/B/C) memungkinkan **provider generik** yang dipakai ulang dengan sedikit
   perbedaan konfigurasi — efisiensi besar untuk 14 situs.

---

## 📦 Daftar Repo Open-Source yang Ditemukan (referensi implementasi)

| Repo | Bahasa | Keterangan |
|---|---|---|
| `xbol0/supjavd` | Deno / CF Workers | Proxy m3u8 supjav.com — **blueprint ekstraksi supjav** |
| `Snowball-01/AdultColony-API` | Node/TS (Express) | Scraper API 13+ situs: **javtsunami, javhd.today, missav, javgiga** dll. |
| `EchterAlsFake/unofficial-api-for-missav` | Python (async) | API + HLS downloader missav.ws (resume, quality) |
| `Cat-Ling/missAV_api` / `EchterAlsFake/missAV_api` | Python | `pip install missAV_api`; HLS download |
| `gakkiyomi/MissAV-API` | Python (FastAPI) | REST misscore `/api/v1/movie/...` |
| `Mark111112/missav-stream` | JS (FastAPI?) | Resolver stream + headers untuk missav.ai |
| `Discover999/123AV_app` | Kotlin/Compose | **Aplikasi Android lengkap** untuk 123av (referensi terbaik) |
| `javgg/watch-jav-online` | Python | Scraper javgg lama (saat ini stub — tidak berguna teknis) |
| `SubMaRk/javguru` | Python | Scraper metadata jav.guru |
| `duongtuanqb/Get-link-JAVHD` | PHP | Ekstraksi link javhd.com (pola lama) |
| `Jacekun/CloudStream-3XXX` | Kotlin | Fork CloudStream dewasa (anime + adult, extractors JWPlayer/GMPlayer/Zplayer) |
| `Phisher98/cloudstream-extensions-phisher` | Kotlin | Repo ekstensi CloudStream komunitas (juga `CSX`, `CXXX`, `TVVVV`) |
| `recloudstream/cloudstream` | Kotlin | Aplikasi CloudStream 3 resmi (dibahas di dokumen 02) |
