package com.scrajav.ext

import com.lagradost.cloudstream3.mainPage
import com.lagradost.cloudstream3.mainPageOf

/**
 * JAVHD.TODAY — keluarga "defboot" (template sama dengan javbraze.com & javseen.tv).
 * URL video /{id}/{kode}-{slug}/, player playEmbed → turbovid/cloudwish (multi-server
 * via data-embed base64). Home `/` cuma link-farm kategori → listing diambil dari
 * section navigasi/sortir yang disediakan situs. Detail di [DefbootBase].
 */
class JavHdProvider : DefbootBase() {
    override var name = "JavHD"
    override var mainUrl = "https://javhd.today"
    override val mainPagePath: String = "/recent/"

    // Section navigasi (sortir) + kategori genre dari situs.
    override val mainPage = mainPageOf(
        mainPage("$mainUrl/recent/", "Latest Update"),
        mainPage("$mainUrl/releaseday/", "Release Day"),
        mainPage("$mainUrl/popular/today/", "Most Viewed Today"),
        mainPage("$mainUrl/popular/week/", "Most Viewed Week"),
        mainPage("$mainUrl/jav-sub/", "Jav Sub"),
        mainPage("$mainUrl/uncensored-jav/", "Uncensored"),
        mainPage("$mainUrl/reducing-mosaic/", "Reducing Mosaic"),
        mainPage("$mainUrl/amateur/", "Amateur"),
        mainPage("$mainUrl/chinese-subtitle/", "Chinese Sub"),
        mainPage("$mainUrl/creampie/", "Creampie"),
        mainPage("$mainUrl/big-tits/", "Big Tits"),
        mainPage("$mainUrl/bbw/", "BBW"),
        mainPage("$mainUrl/breasts/", "Breasts"),
        mainPage("$mainUrl/married-woman/", "Married Woman"),
        mainPage("$mainUrl/beautiful-girl/", "Beautiful Girl"),
        mainPage("$mainUrl/mature-woman/", "Mature Woman"),
        mainPage("$mainUrl/cuckold/", "Cuckold"),
        mainPage("$mainUrl/squirting/", "Squirting"),
        mainPage("$mainUrl/nasty/", "Nasty"),
        mainPage("$mainUrl/hardcore/", "Hardcore"),
        mainPage("$mainUrl/cosplay/", "Cosplay"),
        mainPage("$mainUrl/incest/", "Incest"),
        mainPage("$mainUrl/lesbian/", "Lesbian"),
        mainPage("$mainUrl/massage/", "Massage"),
    )
}
