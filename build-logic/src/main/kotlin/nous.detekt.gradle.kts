// NOUS — Detekt convention plugin
// Plugin ID: `nous.detekt`

plugins {
    id("io.gitlab.arturbosch.detekt")
}

extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt.yml")
    parallel = true
    ignoreFailures = true  // disabled during initial bring-up
    autoCorrect = false
}
