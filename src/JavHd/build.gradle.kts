version = 1

cloudstream {
    description = "JAV streaming dari javhd.today (turbovid/cloudwish)"
    authors = listOf("ScraJav")
    status = 2
    tvTypes = listOf("NSFW")
    language = "id"
}

android {
    sourceSets {
        getByName("main") {
            java.srcDir("../common/src/main/kotlin")
        }
    }
}
