# 📚 Dokumentasi Proyek — ScraJav (aplikasi streaming JAV ala CloudStream)

> **Aplikasi Android**: `com.scrajav.app` · Nama tampilan: **ScraJav**
> Stack: **Kotlin native + Jetpack Compose + Media3/ExoPlayer** · 100% live scraping (tanpa mock/demo).

---

## 📄 Daftar Dokumen

| File | Isi |
|---|---|
| [`01-riset-sumber-video.md`](01-riset-sumber-video.md) | Hasil riset lengkap 14 situs: stack, search, halaman video, metode ekstraksi stream (endpoint), anti-bot, scraper open-source yang ada |
| [`02-arsitektur-aplikasi.md`](02-arsitektur-aplikasi.md) | Arsitektur aplikasi ala CloudStream: cara kerja CloudStream, desain provider, strategi ekstraksi 3 lapis, pemutar, anti-bot |
| [`03-roadmap-mvp.md`](03-roadmap-mvp.md) | Roadmap implementasi bertahap (MVP: omgjav → supjav → javtsunami) |

---

## 🏗️ Status Implementasi (terbaru)

| Fase | Status | Bukti |
|---|---|---|
| Fase 0 — Fondasi proyek (Gradle multi-module, Compose, network, player) | ✅ SELESAI | `:app`, `:provider-api`, `:providers`, `:player` |
| Fase 1 — Provider omgjav.com (L1) | ✅ SELESAI | Live scrape + **playback 720p terbukti di HP asli** |
| Fase 2 — Provider supjav.com (L1+L2) | ✅ SELESAI | **Cloudflare ditembus via WebView resolver**; listing + detail + playback 480p terbukti di HP |
| Fase 3 — Provider javtsunami.com (L1) | ✅ SELESAI | Listing + poster + playback terbukti di HP (jaringan rumah) |
| Fase 4 — Metadata global (modul `:metadata`) | ✅ SELESAI | Pola **JellyfinJAV** (R18/DMM); enrich detail SSIS-406 (judul, studio, direktur, aktris, tanggal, durasi) terbukti di HP |

**Update loop engineering 2026-08-07 (via tes HP Redmi Note 11 + ADB):**
- 🔴 **JavTsunami poster tidak muncul** → selector salah (`div.post-thumbnail-container img` + `data-src`).
  Fix: poster dari `data-main-thumb` pada `<article>` (URL absolut) fallback `img.video-main-thumb`
  `data-lazy-src` (placeholder `data:image/svg` ditolak). ✅ poster muncul (42/48 sel warna kaya).
- 🔴 **SupJAV 403 Cloudflare Turnstile** → tidak bisa dilewati OkHttp murni. Solusi baru: **WebViewResolver**
  (strategi L2): WebView UA mobile asli (CF tidak men-challenge) → petik cookie `cf_clearance` →
  suntik ke jar OkHttp + OkHttp memakai UA identik per-host ([`Http.setHostUserAgent`]).
  Halaman video di-challenge ulang → fallback ambil HTML via WebView ([`Http.fetchWithWebView`]).
  ✅ listing + detail + **playback 480p** terbukti di HP (video SNOS-334/448219).

**Hasil build & test (JVM, mesin dev):**
- `./gradlew :metadata:testDebugUnitTest :providers:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**
- Unit test: **41/41 lulus** (metadata 18: parser kode JAV, R18 source, enrich · provider 23: parser fixture HTML asli)
- Live smoke test (`RUN_LIVE_TESTS=true`): **omgjav PASS** · supjav/javtsunami SKIP (diblokir jaringan)
- APK: `app/build/outputs/apk/debug/app-debug.apk`

**Hasil tes perangkat asli (Redmi Note 11, Android 13, via ADB, 2026-08-07) — SEMUA LULUS:**
- Instal APK ✅ · launch 2.2s ✅ · tanpa crash/FATAL ✅
- **Load** ✅: home menampilkan konten live dari 3 provider (omgjav, javtsunami; supjav menampilkan pesan anti-bot dengan benar)
- **Thumbnail** ✅: verifikasi piksel (26/48 sel warna kaya = poster) + URL poster HTTP 200 (`image/webp` 78KB)
- **Detail metadata** ✅: judul lengkap, kode, aktris (mis. HMN-880 · Itsukaichi Mei), tombol putar
- **Playback** ✅: video **1280×720 @ 30fps** + audio AAC ter-decode hardware (`c2.mtk.avc.decoder`, `c2.android.aac.decoder`), frame terbukti bergerak (2 screenshot beda hash), nol error di logcat
- **Search** ✅: query "SSIS" → hasil live dari 2 provider (SSIS_008 omgjav · SSIS_698 javtsunami)
- 🔴 **Bug ditemukan & diperbaiki saat tes HP**: m3u8 omgjav terkadang relatif (`src:"/store/m3u8/X.m3u8"`) → player gagal (`MalformedURLException`). Diperbaiki dengan helper `resolveUrl()` (RFC 3986) di ketiga provider + unit test pengunci + hardening regex javtsunami (absolut dulu, fallback relatif).

---

## 🛠️ Cara Build & Test (mesin dev)

```bash
# Prasyarat sekali: JDK 17 + Gradle 8.13 di ~/tools (tanpa sudo)
export JAVA_HOME=$HOME/tools/jdk17
export PATH=$HOME/tools/jdk17/bin:$PATH

# Unit test provider (offline, cepat)
./gradlew :providers:testDebugUnitTest

# Build APK
./gradlew :app:assembleDebug

# Live scrape test (butuh jaringan yang bisa menjangkau situs; situs tak terjangkau = SKIP, bukan fail)
RUN_LIVE_TESTS=true ./gradlew :providers:testDebugUnitTest --tests '*.LiveScrapeSmokeTest'
```

---

## 🗂️ Metadata Global (modul `:metadata`)

> Pola **JellyfinJAV** (github.com/Kyuhaku/JellyfinJAV): ekstrak kode JAV → query R18/DMM → fallback chain + cache.

**Alur**: `JavCode.extract(judul/URL)` → kode dinormalisasi (`SSIS-406`) → `JavMetadataService.getForCode`
(query **R18/DMM** `r18.dev/videos/vod/movies/detail/-/dvd_id={kode}/json` — terbukti HTTP 200 dari HP,
javlibrary 403 / r18.com mati) → `JavDetail.enrich(meta)` di layar detail.

**Field yang di-enrich untuk SEMUA provider**: judul EN (menggantikan pola fallback seperti `SupJAV #217765`),
poster cover HD DMM (`pics.dmm.co.jp`), aktris & genre (union dedupe), tanggal rilis, durasi,
plus field tambahan `EnrichedMeta`: **studio, label, direktur, seri, sample video**.

**Dukungan format kode**: `SSIS-406` · `FC2-PPV-123456` (juga `FC2PPV`) · `259LUXU-1894` (juga `MIUM`) ·
tanpa strip (`SSIS406`). Fallback chain `MetadataSource` + cache TTL 24 jam (max 2000 entri, Mutex per-kode
anti duplikat) — siap ditambah sumber baru (JavDB, JavLibrary).

```
metadata/
├── JavCode.kt              → parser kode JAV (regex berurutan: FC2 → LUXU → standard → no-hyphen)
├── JavMeta.kt              → model metadata global
├── MetadataSource.kt       → kontrak sumber metadata (fallback chain)
├── source/R18Source.kt     → client R18/DMM (JSON org.json, request timeout, parse defensif)
├── JavMetadataService.kt   → service + cache TTL + Mutex per-kode
└── Enrich.kt               → JavDetail.enrich() + EnrichedMeta (studio/direktur/seri/sample)
```

---

## 📁 Struktur Proyek

```
scrajav/
├── app/            → UI Compose: Home, Search, Detail, Player, Settings
├── provider-api/   → kontrak JavProvider + model + jaringan (OkHttp) + error taxonomy
├── providers/      → OmgjavProvider, SupjavProvider, JavTsunamiProvider, ProviderRegistry
├── metadata/       → metadata global ala JellyfinJAV (R18/DMM, cache, enrich semua provider)
├── player/         → pemutar Media3/ExoPlayer dengan header per-sumber
└── docs/           → dokumentasi ini
```

---

## 🔑 Kesimpulan Eksekutif (riset)

1. **Tidak ada API resmi** dari 14 situs — semuanya scraping HTML/JSON (melanggar ToS; risiko
   DMCA/blokir). missav.com bahkan telah di-takedown lewat proses hukum.
2. **3 keluarga platform**: (A) custom JSON `hosts[]` — omgjav ✅ + diduga javgg/javmost/javseen/
   roshy/javmit/javbraze; (B) WordPress/post-ID — supjav, jav.guru, javtsunami; (C) JS-rendered +
   Cloudflare — missav (paling sulit).
3. **Semua stream HLS/m3u8 atau MP4 bertoken** (kedaluwarsa 5–30 menit) dan butuh header
   Referer/Origin/UA per CDN.
4. **Domain volatil** — arsitektur harus mendukung rotasi mirror (missav.ws/ai/live, dll).

---

## ⚠️ Catatan Hukum & Risiko

- Scraping melanggar ToS; banyak konten berhak cipta (missav.com di-seize). Gunakan untuk
  penggunaan pribadi, hormati situs (rate-limit wajar), dan patuhi hukum setempat.
- Aplikasi konten dewasa tidak akan lolos Google Play → distribusi via APK sideload/repo sendiri.

---

## 🗂️ Keputusan yang Sudah Diambil

| Keputusan | Pilihan |
|---|---|
| Nama aplikasi | **ScraJav** |
| Package | `com.scrajav.app` |
| Tech stack | **Kotlin native (gaya CloudStream)** |
| Strategi ekstraksi | Seperti CloudStream (pelajari repo + pemutarnya) |
| MVP | Situs termudah dulu: **omgjav → supjav → javtsunami** |
| Data aplikasi | **100% live** — mock/demo hanya untuk pengujian |
