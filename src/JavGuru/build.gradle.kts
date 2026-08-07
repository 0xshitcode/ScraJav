version = 1

cloudstream {
    description = "JAV streaming dari jav.guru (WordPress + gateway player)"
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
