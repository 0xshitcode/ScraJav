package com.scrajav.ext

/**
 * JAVHD.TODAY — keluarga "defboot" (template sama dengan javbraze.com & javseen.tv).
 * URL video /{id}/{kode}-{slug}/, player playEmbed → turbovid/cloudwish (multi-server
 * via data-embed base64). Home `/` cuma link-farm kategori → listing diambil dari
 * `/latest-videos/` yang menyediakan kartu video statis. Detail di [DefbootBase].
 */
class JavHdProvider : DefbootBase() {
    override var name = "JavHD"
    override var mainUrl = "https://javhd.today"
    override val mainPagePath: String = "/latest-videos/"
}
