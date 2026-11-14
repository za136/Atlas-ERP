package com.atlas.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.modulith.Modulithic

@Modulithic(
    systemName = "Atlas ERP",
    sharedModules = ["com.atlas.platform.common"]
)
@SpringBootApplication(
    scanBasePackages = [
        "com.atlas.platform",
        "com.atlas.finance",
        "com.atlas.procurement",
        "com.atlas.sales",
        "com.atlas.inventory",
        "com.atlas.app"
    ]
)
@EntityScan(
    basePackages = [
        "com.atlas.platform",
        "com.atlas.finance",
        "com.atlas.procurement",
        "com.atlas.sales",
        "com.atlas.inventory",
        "org.springframework.modulith.events.jpa"
    ]
)
@EnableJpaRepositories(
    basePackages = [
        "com.atlas.platform",
        "com.atlas.finance",
        "com.atlas.procurement",
        "com.atlas.sales",
        "com.atlas.inventory",
        "org.springframework.modulith.events.jpa"
    ]
)
class AtlasApplication

fun main(args: Array<String>) {
    runApplication<AtlasApplication>(*args)
}
