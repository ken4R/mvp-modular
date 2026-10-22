plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Liens profonds : un lien « mvpmodular://… » (ou le tap sur un rappel) devient une destination typée,
// gardée jusqu'à ce que l'app puisse l'afficher (connexion faite, verrou levé). Voir ADR 0008.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.core.deeplink"
}
