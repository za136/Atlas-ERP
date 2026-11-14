plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-org"))
    implementation(project(":atlas-platform:plt-iam"))
    implementation(project(":atlas-platform:plt-doc"))
    implementation(project(":atlas-platform:plt-aud"))
    implementation(project(":atlas-finance:fin-accounting-api"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jooq)
}
