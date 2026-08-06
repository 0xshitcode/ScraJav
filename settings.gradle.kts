rootProject.name = "ScraJav"

// Semua folder di src/ dengan build.gradle.kts otomatis menjadi modul ekstensi.
val disabled = listOf<String>()

File(rootDir, "src").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(":src:" + dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
