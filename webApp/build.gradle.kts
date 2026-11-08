import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The web app: the entry point, the page (index.html), the bundled fonts and the "update your browser" page.
// Everything else is the shared code in :shared.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The version shown on the home screen is the Android app's versionName, so there is one place to change it.
val appVersionDir = layout.buildDirectory.dir("generated/appVersion")
val generateAppVersion by tasks.registering {
    val gradleFile = rootProject.file("androidApp/build.gradle.kts")
    val outDir = appVersionDir
    inputs.file(gradleFile)
    outputs.dir(outDir)
    doLast {
        val version = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradleFile.readText())!!.groupValues[1]
        val file = outDir.get().asFile.resolve("AppVersion.kt")
        file.parentFile.mkdirs()
        file.writeText("const val APP_VERSION = \"$version\"\n")
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("payup")
        browser {
            commonWebpackConfig {
                outputFileName = "payup.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain { kotlin.srcDir(files(appVersionDir).builtBy(generateAppVersion)) }
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.androidx.sqlite.web)
            implementation(libs.kotlinx.browser)
            implementation(libs.navigation3.browser)
            // The Web Worker that runs SQLite, bundled from webApp/worker.
            implementation(npm("sqlite-wasm-worker", layout.projectDirectory.dir("worker").asFile))
        }
    }
}

// The service worker (sw.js) caches every file of the build when it installs, so the app works offline after the first
// visit. It reads the list of files from precache.json, which is written next to them when the site is built.
tasks.named("wasmJsBrowserDistribution") {
    doLast {
        outputs.files.files.filter { it.isDirectory && File(it, "index.html").exists() }.forEach { dir ->
            val files = dir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".map") && it.name != "precache.json" && it.name != "sw.js" }
                .map { it.relativeTo(dir).invariantSeparatorsPath }
                .sorted()
                .toList()
            File(dir, "precache.json").writeText(files.joinToString(",\n", "[\n", "\n]\n") { "  \"$it\"" })
        }
    }
}

// The fonts live in src/commonMain/composeResources/font.
compose.resources {
    packageOfResClass = "com.payup.web.resources"
    generateResClass = always
}
