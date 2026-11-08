import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "com.payup.shared"
        compileSdk = 37
        minSdk = 26

        // Packages the Compose resources (the translations) into the Android app.
        androidResources {
            enable = true
        }

        withHostTestBuilder {}
    }

    // Compile-only for now: it catches JVM-only code early. The web app itself comes in milestone 2.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.jetbrains.navigation3.ui)
            api(libs.jetbrains.lifecycle.viewmodel.navigation3)
            api(libs.multiplatform.settings)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.animation)
            api(libs.compose.material.icons.core)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.multiplatform.settings.test)
        }
    }
}

// Room generates the database code once per target.
room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspWasmJs", libs.androidx.room.compiler)
}

// The strings live in src/commonMain/composeResources; Res is public so the Android app can use it.
compose.resources {
    packageOfResClass = "com.payup.resources"
    publicResClass = true
    generateResClass = always
}
