# 🗺️ 03 — Roadmap Implementasi (MVP: 3 situs termudah dulu)

> Urutan berdasar kesulitan ekstraksi yang ditemukan di riset:
> **omgjav (⭐) → supjav (⭐⭐) → javtsunami (⭐⭐) → jav.guru (⭐⭐) → 123av (⭐⭐)
> → javhd.today (⭐⭐⭐) → keluarga platform (⭐⭐) → missav (⭐⭐⭐⭐) → sisanya (verifikasi dulu).**
>
> Setiap fase diakhiri dengan **verifikasi manual** (aplikasi jalan + video benar-benar
> terputar) sebelum lanjut. Jangan pernah pindah fase kalau fase sebelumnya belum stabil.

---

## Fase 0 — Fondasi Proyek ✅ SELESAI

**Tujuan:** kerangka aplikasi jalan dengan player kosong (bisa memutar URL m3u8 manual).

- [x] Inisialisasi proyek Android: Kotlin 2.x + Jetpack Compose + Gradle version catalog (`libs.versions.toml`).
- [x] Modul `:provider-api` (interface `JavProvider` + model + error taxonomy + OkHttp).
- [x] Modul `:player`: Media3 ExoPlayer + `DefaultHttpDataSource.Factory` dengan headers
      dinamis + UI player (play/pause, seek, pilihan sumber/kualitas, fullscreen).
- [x] Modul `:providers` (skeleton + `ProviderRegistry`).
- [x] Layer network: OkHttp + cookie jar + UA browser.
- [x] Screens dasar: Home, Search, Detail, Player, Settings (Compose + Navigation).
- [x] **Tes:** `./gradlew :providers:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL,
      APK `app/build/outputs/apk/debug/app-debug.apk`.

---

## Fase 1 — Provider: omgjav.com (L1) ✅ SELESAI & TERVERIFIKASI LIVE

**Paling mudah, sudah terverifikasi live.** (Detail: `01-riset-sumber-video.md` §1)

- [x] `OmgjavProvider`: `search()` → `/?s={query}` (bukan `/search/{q}` yang 404!), `home()`
      → `/search/hottest`, `detail()` (`/v/{kode}`), `extractSources()`:
      1. GET halaman video (UA browser, Referer).
      2. Regex/JSON-parse untuk `hosts[].media.src` (pola `"type":"m3u8"`).
      3. Return `VideoSource(m3u8, headers = {Referer, UA}).
- [x] Penanganan **link relatif** (`../v/X` / `./v/X`) via Jsoup `absUrl(href)`.
- [x] **Tes:** live scrape `RUN_LIVE_TESTS=true` → search SSIS → detail → m3u8 asli **PASS**;
      unit test parser 100% (fixture HTML asli hasil riset).

> ⚠️ Temuan penting saat implementasi: search omgjav = `/?s={q}` (HTTP 200), sedangkan
> `/search/{q}` mengembalikan 404. Listing pakai `/search/hottest` (HTTP 200).

---

## Fase 2 — Provider: supjav.com (L1) 🎯

**Blueprint lengkap sudah ada** (`xbol0/supjavd/supjav.ts`). (Detail: `01` §2)

- [ ] `SupjavProvider`:
      1. `search()` → GET `/?s={q}` (+ pagination `/page/{n}/`), regex
         `https://supjav\.com/.*?\d+\.html.*?title="..." data-original="..."`.
      2. `home()` → `/popular?sort=day|week|month`, `/category/...`.
      3. `detail()` → GET `/{id}.html`.
      4. `extractVideo()`:
         a. Parse `data-link="..."` → peta server → ambil token server "TV".
         b. Balik string token.
         c. GET `https://lk1.supremejav.com/supjav.php?c={terbalik}`
            (headers: `Referer: https://supjav.com/`, UA).
         d. Regex `urlPlay.*?(https.*?\.m3u8)` → m3u8.
         e. `VideoSource(m3u8, headers = {Referer: <origin m3u8>, UA})`.
- [ ] Backend `lk1.supremejav.com` → taruh di konfigurasi (bisa berubah).
- [ ] **Tes:** search → play; uji juga server selain "TV" bila ada (multi-server).

---

## Fase 3 — Provider: javtsunami.com (L1) 🎯

**Selector sudah terdokumentasi.** (Detail: `01` §6)

- [ ] `JavTsunamiProvider`:
      search: parse `article` (`post-thumbnail-container img[data-src]`, `entry-header span`,
      `span.views`, `span.duration`).
      detail: `h1.entry-title`, `div.desc img`, `meta[itemprop=duration/uploadDate]`,
      `div.tags-list a`.
      extractVideo: ambil `meta[itemprop=embedURL]` → **request lanjutan** ke URL embed →
      cari m3u8/mp4 (mungkin butuh pola extractor embed: JWPlayer/flowplayer/hls.js).
- [ ] **Tes:** search → detail → play.

---

## ✅ Milestone MVP — Rilis internal #1

Aplikasi bisa: Home (3 sumber) · Search gabungan · Detail · Play HLS multi-kualitas · History.
Setelah milestone ini, evaluasi bersama sebelum ekspansi.

---

## Fase 4 — Provider: jav.guru (L1)

- [ ] Metadata: `/?s={q}` + `.grid1 a` + `.inside-article/.infoleft li` (7 field) + `.titl` + cover.
- [ ] **Verifikasi lokasi m3u8** (langkah riset yang belum tuntas): buka detail, DevTools,
      filter `.m3u8`; cek apakah di HTML, script player, atau API. Catat hasilnya di `01` §4.

## Fase 5 — Provider: 123av.com (L1 + L2)

- [ ] Adaptasi pola `Discover999/123AV_app`:
      `ImprovedVideoUrlFetcher` (parse langsung → WebView intercept → fallback) + M3U8 download.
- [ ] Sinkronkan cookie OkHttp ↔ WebView (agar sesi konsisten).

## Fase 6 — Provider: javhd.today (L1/L2)

- [ ] `GET /embed/{id}` → ekstrak m3u8/mp4; cek pola token (`cdn_hash/cdn_creation_time`).
- [ ] Siapkan L2 (WebView intercept) bila `/embed/` JS-rendered.

## Fase 7 — Keluarga platform A: javgg.net, javmost.ws, javseen.tv (dan cek roshy/javmit/javbraze)

- [ ] **Verifikasi hipotesis `hosts[]` JSON** di masing-masing situs (buka halaman video).
- [ ] Buat **`FamilyHostsProvider` generik**: satu implementasi, beda baseUrl + selector regex.
- [ ] Daftarkan situs yang terbukti sama; tandai yang ternyata beda untuk ditangani sendiri.

## Fase 8 — Provider: missav.ws (L2 → L3)

- [ ] Mulai dengan **WebView intercept** (L2) di Android.
- [ ] Bila tidak cukup: buat **proxy server kecil** (pola `missav-stream` / `supjavd`):
      endpoint `GET /api/resolve/{kode}?quality=` → `{stream_url, headers}`;
      server pakai `curl_cffi` (impersonasi Chrome, HTTP/1.1 — **missav tidak support HTTP/2**).
- [ ] Konfigurasi rotasi domain: `missav.ws / missav.ai / missav.live`.

## Fase 9 — sisanya (sextb.cc, roshy.tv, javmit.com, javbraze.com)

- [ ] Verifikasi manual struktur → klasifikasikan keluarga → implementasi (generik bila cocok).

---

## Uji Kualitas (berjalan terus di tiap fase)

| Jenis | Cara |
|---|---|
| Unit test ekstraktor | Simpan sampel HTML (`test fixtures`) per situs → test `extractVideo`/parser tanpa jaringan |
| Instrumented test | WebView intercept & player (device emulator) |
| Build CI | `./gradlew assembleDebug` tiap fase |
| Manual | Play 3+ video per situs, cek multi-kualitas, PiP, download, error saat URL expired |
| Smoke test harian | Cek semua base URL (situs pindah domain / down) → laporkan |

---

## Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| Situs berubah struktur | Fixture test + selector di satu file per provider; rutin verifikasi |
| Domain seizure / pindah (missav, dll) | Daftar mirror + setting rotasi di aplikasi |
| Token video kedaluwarsa cepat | Cache TTL pendek; auto re-extract saat 403 |
| Cloudflare makin ketat | Eskalasi L1 → L2 → L3 per situs |
| Google Play menolak | Distribusi APK langsung / repo sendiri (dokumentasi cara install) |
| Legal/ToS | Lihat disclaimer di `README.md`; gunakan untuk penggunaan pribadi |
