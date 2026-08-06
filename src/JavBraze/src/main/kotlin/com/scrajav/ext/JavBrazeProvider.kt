package com.scrajav.ext

/**
 * JAVBRAZE.COM — keluarga "defboot" (satu template dengan javhd.today & javseen.tv).
 * URL video /{id}/{kode}-{slug}/, player playEmbed → turbovid.vip / cloudwish.xyz.
 * Detail di [DefbootBase]. Ref: docs/01-riset-sumber-video.md §5 + probe live.
 */
class JavBrazeProvider : DefbootBase() {
    override var name = "JavBraze"
    override var mainUrl = "https://javbraze.com"
}
