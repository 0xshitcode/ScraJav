version = 1

cloudstream {
    description = "JAV streaming dari javmit.com (WordPress, multi embed)"
    authors = listOf("ScraJav")
    status = 2
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
