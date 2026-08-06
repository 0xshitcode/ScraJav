# 🎬 ScraJav — CloudStream Extension Repository

Kumpulan **provider JAV** untuk aplikasi [CloudStream](https://github.com/recloudstream/cloudstream)
(repo: `com.scrajav.app` → migrasi dari aplikasi Android ScraJav menjadi ekstensi).
**100% live scraping** — tanpa mock/demo.

## ➕ Cara pakai

1. Buka aplikasi **CloudStream** → **Settings** → **Extensions** → **+ Add repository**.
2. Masukkan URL repo (bila sudah di-deploy, mis. `https://scrajav.vercel.app/`).
   - Sebelum deploy, tambahkan URL dari `repo.json` di folder `repo/`.
3. Install provider yang diinginkan (aktifkan **NSFW** di pengaturan bila diminta).
4. Provider muncul di tab **Home / Search**.

> ⚠️ Semua provider bertipe `TvType.NSFW` — perlu pengaturan NSFW aktif di CloudStream.

## 🧱 Struktur

```
├── build.gradle.kts      → template resmi CloudStream v4 (Kotlin 2.3.0, AGP 8.7.3)
├── settings.gradle.kts   → auto-include semua modul di src/
├── src/common/           → helper bersama (regex, resolver embed, unpack packed JS)
└── src/{Provider}/       → satu modul ekstensi per situs (.cs3)
```

## 🛠️ Build (mesin dev)

```bash
export JAVA_HOME=$HOME/tools/jdk17
export PATH=$HOME/tools/jdk17/bin:$PATH

./gradlew make makePluginsJson          # hasil: src/*/build/*.cs3 + build/plugins.json
# artefak repo untuk deploy:
mkdir -p repo && cp **/build/*.cs3 repo/ && cp build/plugins.json repo/
cp repo.json.template repo/repo.json     # sesuaikan URL pluginLists
```

## 🚀 Deploy ke Vercel

- `vercel.json` sudah disiapkan: Vercel menyajikan folder `repo/` di root URL.
- Alur: bangun `.cs3`/`plugins.json` secara lokal (atau GitHub Actions), commit ke `repo/`,
  lalu hubungkan repo ke Vercel. URL deploy = URL repository CloudStream.

## 📊 Status Provider (Agustus 2026, probe live dari jaringan rumah)

| # | Provider | Listing | Detail | Playback | Catatan |
|---|---|---|---|---|---|
| 1 | OMGJAV | ✅ | ✅ | ✅ m3u8 | JSON `hosts[]` di halaman |
| 2 | JavTsunami | ✅ | ✅ | ✅ m3u8 | WordPress, embedURL |
| 3 | JavBraze | ✅ | ✅ | ✅ turbovid/cloudwish | keluarga defboot |
| 4 | JavHD | ⚠️ | ✅ | ✅ turbovid/cloudwish | listing dirender JS |
| 5 | JavSeen | ⚠️ | ✅ | ✅ turbovid/cloudwish | listing dirender JS |
| 6 | JAVGG | ✅ | ✅ | ✅ embed multi-host | WordPress DooPlayer |
| 7 | JavGuru | ✅ | ✅ | ⚠️ gateway searcho | m3u8 via gateway xr |
| 8 | JavMost | ✅ API | ✅ | ⚠️ player AES | listing via showlist2 |
| 9 | Roshy | ⚠️ | ⚠️ | ⚠️ | WordPress, pola umum |
| 10 | JavMit | ⚠️ | ⚠️ | ⚠️ | WordPress, pola umum |
| 11 | SupJAV | ❌ | ❌ | ❌ | Cloudflare Turnstile (butuh WebView) |
| 12 | MissAV | ❌ | ❌ | ❌ | JS-rendered + Cloudflare |
| 13 | SextB | ❌ | ❌ | ❌ | Cloudflare di semua domain |
| 14 | 123AV | ❌ | ❌ | ❌ | tak terjangkau (000) |

✅ terbukti · ⚠️ best-effort / perlu verifikasi live · ❌ diblokir anti-bot (butuh WebView/proxy)

## ⚠️ Catatan

- Situs cepat berubah (domain, player, anti-bot). `docs/01-riset-sumber-video.md`
  berisi peta pola scraping lengkap untuk perbaikan cepat.
- SupJAV/MissAV/SextB membutuhkan eksekusi JS (Cloudflare Turnstile) — tidak bisa
  diselesaikan ekstensi murni; solusi lanjutan: proxy server sendiri atau
  `loadExtractor` tambahan.
