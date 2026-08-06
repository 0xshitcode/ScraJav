package com.scrajav.ext

/**
 * JAVHD.TODAY — keluarga "defboot" (template sama dengan javbraze.com & javseen.tv).
 * URL video /{id}/{kode}-{slug}/, player playEmbed → turbovid/cloudwish.
 * Listing home/recent di situs ini dirender JS (skeleton) — kartu diambil dari
 * markup statis yang tersedia; search via /search/video/?q=. Detail di [DefbootBase].
 */
class JavHdProvider : DefbootBase() {
    override var name = "JavHD"
    override var mainUrl = "https://javhd.today"
}
