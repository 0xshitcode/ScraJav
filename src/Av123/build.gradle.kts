version = 1

cloudstream {
    description = "JAV streaming dari 123av.com (agregator Mandarin — best-effort)"
    authors = listOf("ScraJav")
    status = 3
    tvTypes = listOf("Movie")
    language = "id"
}

android {
    sourceSets {
        getByName("main") {
            java.srcDir("../common/src/main/kotlin")
        }
    }
}
