// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

val forcedCoroutinesVersion = libs.versions.kotlinxCoroutines.get()

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" &&
                requested.name.startsWith("kotlinx-coroutines-")
            ) {
                useVersion(forcedCoroutinesVersion)
                because("Align coroutine artifacts while allowing Koog to resolve its transitive graph.")
            }
        }
    }
}
