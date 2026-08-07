version = 1

cloudstream {
    description = "JAV streaming dari sextb.cc (CF-protected — best-effort)"
    authors = listOf("ScraJav")
    status = 3
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
