package com.scrajav.ext

/**
 * JAVSEEN.TV — keluarga "defboot" (template sama dengan javbraze.com & javhd.today).
 * URL video /{id}/{kode}-{slug}/, player playEmbed → turbovid/cloudwish.
 * Listing home/recent dirender JS (skeleton). Detail di [DefbootBase].
 */
class JavSeenProvider : DefbootBase() {
    override var name = "JavSeen"
    override var mainUrl = "https://javseen.tv"
}
