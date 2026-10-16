plugins {
    `kotlin-dsl`
}

group = "com.mvp.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "mvp.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
    }
}
