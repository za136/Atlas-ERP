plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.atlas.erp"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        maven { url = uri("file:///mnt/c/Users/Lenovo/.m2/repository") }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    dependencies {
        // Enforce Spring Boot & Spring Modulith BOM via Gradle native platform
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.3.5"))
        "implementation"(platform("org.springframework.modulith:spring-modulith-bom:1.2.4"))

        // Kotlin Standard Library & Reflection
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib")

        // Jackson Annotations
        "implementation"("com.fasterxml.jackson.core:jackson-annotations")

        // Logging
        "implementation"("io.github.oshai:kotlin-logging-jvm:7.0.0")

        // Testing
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.3.5"))
        "testImplementation"(platform("org.springframework.modulith:spring-modulith-bom:1.2.4"))
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testImplementation"("io.mockk:mockk:1.13.13")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxHeapSize = "512m"
        maxParallelForks = 1
    }
}
