plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
}

springBoot {
    mainClass.set("com.atlas.app.AtlasApplicationKt")
}

dependencies {
    implementation(project(":atlas-platform:plt-common"))
    implementation(project(":atlas-platform:plt-org"))
    implementation(project(":atlas-platform:plt-iam"))
    implementation(project(":atlas-platform:plt-doc"))
    implementation(project(":atlas-platform:plt-aud"))
    implementation(project(":atlas-platform:plt-cfg"))
    implementation(project(":atlas-platform:plt-ai"))

    implementation(project(":atlas-finance:fin-accounting-api"))
    implementation(project(":atlas-finance:fin-accounting-core"))
    implementation(project(":atlas-finance:fin-gl"))
    implementation(project(":atlas-finance:fin-ap"))
    implementation(project(":atlas-finance:fin-ar"))

    implementation(project(":atlas-procurement"))
    implementation(project(":atlas-sales"))
    implementation(project(":atlas-inventory"))

    // Spring Boot & Web
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jooq)

    // Spring Modulith
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jpa)

    // Database & Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly("com.h2database:h2:2.4.240")
    testImplementation("com.h2database:h2:2.4.240")

    // Documentation
    implementation(libs.springdoc.openapi)

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
