# 🎬 ScraJav — CloudStream Extension Repository

Kumpulan **provider JAV** untuk aplikasi [CloudStream](https://github.com/recloudstream/cloudstream)
(repo: `com.scrajav.app` → migrasi dari aplikasi Android ScraJav menjadi ekstensi).
**100% live scraping** — tanpa mock/demo.

## ➕ Cara pakai

1. Buka aplikasi **CloudStream** → **Settings** → **Extensions** → **+ Add repository**.
2. Masukkan URL repository: `https://raw.githubusercontent.com/0xshitcode/ScraJav/main/`
   (artefak `.cs3` + `plugins.json` di branch `main`).
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
# artefak repo untuk hosting statis (opsional, bila tidak memakai CI):
mkdir -p repo && cp src/*/build/*.cs3 repo/ && cp build/plugins.json repo/
printf '%s' '{ "name": "ScraJav", "description": "ScraJav extension repository", "manifestVersion": 1, "pluginLists": ["https://raw.githubusercontent.com/0xshitcode/ScraJav/main/plugins.json"] }' > repo/repo.json
```

## 🚀 Rilis

- Artefak dibangun **lokal** (`./gradlew make makePluginsJson`) lalu `.cs3` + `plugins.json`
  di-commit ke branch `main` (single-branch — tanpa branch artefak terpisah).
- GitHub Actions (`.github/workflows/build.yml`) hanya dijalankan manual via
  **Actions → Run workflow** (`workflow_dispatch`) — tidak otomatis per push.
- URL repository CloudStream: `https://raw.githubusercontent.com/0xshitcode/ScraJav/main/`

## 📊 Status Provider (Agustus 2026, probe live dari jaringan rumah)

| # | Provider | Listing | Detail | Playback | Catatan |
|---|---|---|---|---|---|
| 1 | OMGJAV | ✅ | ✅ | ✅ m3u8 | JSON `hosts[]` di halaman |
| 2 | JavTsunami | ✅ | ✅ | ✅ m3u8 | WordPress, embedURL |
| 3 | 123AV | ✅ | ✅ | ✅ m3u8 | search `keyword`, player JSON → javplayer.cc |
| 4 | JAVGG | ✅ | ✅ | ✅ embed multi-host | WordPress DooPlayer |
| 5 | JavGuru | ✅ | ✅ | ⚠️ gateway searcho | m3u8 via gateway xr |
| 6 | JavHD | ⚠️ | ✅ | ⚠️ | turbovid/cloudwish, listing dirender JS |
| 7 | MissAV | ⚠️ | ⚠️ | ⚠️ | JS-rendered, butuh verifikasi ulang |
| 8 | Roshy | ⚠️ | ⚠️ | ⚠️ | WordPress, pola umum |
| 9 | JavMit | ⚠️ | ⚠️ | ⚠️ | WordPress, pola umum |

✅ terbukti · ⚠️ best-effort / perlu verifikasi live

## ⚠️ Catatan

- Situs cepat berubah (domain, player, anti-bot). `docs/01-riset-sumber-video.md`
  berisi peta pola scraping lengkap untuk perbaikan cepat.
- Provider yang sudah **dihapus** karena tidak layak: JavBraze (search broken di sisi
  situs), JavSeen (link-farm), JavMost (player dooplayer berlapis obfuscation), SupJAV
  & SextB (Cloudflare Turnstile global — butuh WebView manual, imbalan rendah).
- Daftar ini **best-effort**; status bisa berubah kapan saja seiring perubahan situs.
