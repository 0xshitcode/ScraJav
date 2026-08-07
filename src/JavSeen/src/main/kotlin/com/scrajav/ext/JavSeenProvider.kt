package com.scrajav.ext

import com.lagradost.cloudstream3.SearchResponseList

/**
 * JAVSEEN.TV — dulu keluarga "defboot", tapi per Agustus 2026 situs sudah berubah
 * jadi link-farm (home & search mengembalikan halaman kategori tanpa kartu video,
 * tidak ada /latest-videos/). Provider dinonaktifkan (hasMainPage=false, search
 * ditutup) sampai situs menyediakan listing video yang valid lagi.
 */
class JavSeenProvider : DefbootBase() {
    override var name = "JavSeen"
    override var mainUrl = "https://javseen.tv"
    override val hasMainPage = false

    override suspend fun search(query: String, page: Int): SearchResponseList? = null
}
