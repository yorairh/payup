import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The web app: the entry point, the page (index.html), the bundled fonts and the "update your browser" page.
// Everything else is the shared code in :shared.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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

// The fonts live in src/commonMain/composeResources/font.
compose.resources {
    packageOfResClass = "com.payup.web.resources"
    generateResClass = always
}
