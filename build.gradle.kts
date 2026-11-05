plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// The project folder has a non-ASCII (Hebrew) path, which breaks the unit-test JVM's classpath on Windows.
// Build outputs go to an ASCII folder instead, which also keeps them out of OneDrive sync.
allprojects {
    layout.buildDirectory.set(file("${System.getProperty("user.home")}/.payup-build/${project.name}"))
}
