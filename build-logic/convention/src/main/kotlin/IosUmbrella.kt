import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

/**
 * Framework iOS « Shared » d'un module parapluie, assemblé en XCFramework (tâche `assembleShared<Config>XCFramework`)
 * et copié dans le package SPM iosApp/Packages/SharedKMP par scripts/build-ios-framework.sh (ADR 0027).
 *
 * Tous les parapluies portent le même nom de framework : les packages Swift font `import Shared` quel que soit
 * celui qui est lié. [exported] : modules visibles en Swift (`api` + `export`) ; [linked] : modules liés sans être
 * exportés (`implementation`), dont le code est nécessaire au graphe Koin.
 */
fun Project.iosUmbrellaFramework(exported: List<Any>, linked: List<Any> = emptyList()) {
    val xcFramework = XCFramework("Shared")
    extensions.configure<KotlinMultiplatformExtension> {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                // Dynamique : SPM l'embarque une seule fois même si plusieurs packages en dépendent.
                isStatic = false
                binaryOption("bundleId", "com.mvp.shared")
                exported.forEach { export(it) }
                xcFramework.add(this)
            }
            // SQLDelight (core:database) utilise le SQLite fourni par iOS. Les modules qui appliquent le plugin
            // SQLDelight le lient d'eux-mêmes (linkSqlite) ; le parapluie ne l'applique pas. Framework et
            // exécutable de test (UmbrellaKoinTest) en ont besoin.
            target.binaries.all { linkerOpts("-lsqlite3") }
        }
        sourceSets.getByName("commonMain").dependencies {
            exported.forEach { api(it) }
            linked.forEach { implementation(it) }
        }
    }
}

/**
 * Parapluie d'une app de démo iOS (umbrella/<nom>-demo) : son framework, plus le test commun qui vérifie que chaque
 * définition de `umbrellaModules` se résout (umbrella/koin-check), lancé sur le simulateur par `iosSimulatorArm64Test`.
 */
fun Project.demoUmbrella(exported: List<Any>, linked: List<Any> = emptyList()) {
    iosUmbrellaFramework(exported, linked)
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.matching { it.name == "iosTest" }.configureEach {
            kotlin.srcDir(rootProject.file("umbrella/koin-check"))
        }
    }
}
