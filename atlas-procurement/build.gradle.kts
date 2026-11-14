plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-org"))
    implementation(project(":atlas-platform:plt-doc"))
    implementation(project(":atlas-platform:plt-aud"))
    implementation(project(":atlas-finance:fin-accounting-api"))
    implementation(project(":atlas-finance:fin-ap"))
    implementation(libs.spring.boot.starter.data.jpa)
}
