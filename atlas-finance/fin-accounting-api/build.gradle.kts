plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-org"))
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}
