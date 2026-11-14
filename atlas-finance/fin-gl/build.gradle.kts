plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-org"))
    implementation(project(":atlas-finance:fin-accounting-api"))
    implementation(project(":atlas-finance:fin-accounting-core"))
    implementation(libs.spring.boot.starter.data.jpa)
}
