version = 1

cloudstream {
    description = "JAV streaming dari omgjav.com (listing, detail, m3u8 langsung)"
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
