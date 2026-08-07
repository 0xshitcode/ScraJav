version = 1

cloudstream {
    description = "JAV streaming dari javmost.ws (listing API showlist2)"
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
