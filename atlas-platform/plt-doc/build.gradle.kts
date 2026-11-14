plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-iam"))
    implementation(libs.spring.boot.starter.data.jpa)
}
