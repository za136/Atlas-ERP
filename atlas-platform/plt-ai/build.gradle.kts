plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-iam"))
    implementation(project(":atlas-platform:plt-aud"))
    implementation(libs.spring.boot.starter)
}
