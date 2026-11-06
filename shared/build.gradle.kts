import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "com.payup.shared"
        compileSdk = 37
        minSdk = 26

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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
