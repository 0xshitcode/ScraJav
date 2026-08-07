version = 1

cloudstream {
    description = "JAV streaming dari javgg.net (WordPress DooPlayer, multi embed)"
    authors = listOf("ScraJav")
    status = 1
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
