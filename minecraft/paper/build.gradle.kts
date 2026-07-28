plugins {
    id("com.gradleup.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    // The old /content/repositories/placeholders/ path 404s now; ExtendedClip
    // moved their release repo to /releases/ (verified live, see docs/BUILD.md).
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    implementation(project(":core"))

    compileOnly("io.papermc.paper:paper-api:${project.findProperty("paperVersion")}")
    compileOnly("me.clip:placeholderapi:${project.findProperty("placeholderapiVersion")}")
    // FlectonePulse's own published API module - see integration/FlectonePulseAccess.
    compileOnly("net.flectone.pulse:core:${project.findProperty("flectonePulseVersion")}")
    // Needed only for javac to resolve type annotations on FlectonePulse's
    // classes - see the comment on commonsLang3Version in gradle.properties.
    compileOnly("org.apache.commons:commons-lang3:${project.findProperty("commonsLang3Version")}")

    implementation("org.incendo:cloud-core:${project.findProperty("cloudCoreVersion")}")
    implementation("org.incendo:cloud-paper:${project.findProperty("cloudPaperVersion")}")
    implementation("org.incendo:cloud-annotations:${project.findProperty("cloudCoreVersion")}")

    implementation("com.google.inject:guice:${project.findProperty("guiceVersion")}")
    implementation("org.yaml:snakeyaml:${project.findProperty("snakeyamlVersion")}")
    implementation("com.zaxxer:HikariCP:${project.findProperty("hikariVersion")}")
    implementation("com.h2database:h2:${project.findProperty("h2Version")}")
    implementation("com.mysql:mysql-connector-j:${project.findProperty("mysqlVersion")}")
    implementation("org.mariadb.jdbc:mariadb-java-client:${project.findProperty("mariadbVersion")}")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("com.google.inject", "org.misqzy.playergrowth.paper.libs.guice")
    relocate("com.zaxxer.hikari", "org.misqzy.playergrowth.paper.libs.hikari")
    relocate("org.yaml.snakeyaml", "org.misqzy.playergrowth.paper.libs.snakeyaml")
    relocate("org.incendo.cloud", "org.misqzy.playergrowth.paper.libs.cloud")
    // adventure/kyori is provided by the Paper server itself - not shaded/relocated.
    //
    // minimize() is deliberately NOT used: it depends on org.vafer:jdependency,
    // which bundles an ASM version that cannot parse Java 25 class files
    // ("Unsupported class file major version 69") - a real failure hit
    // against current paper-api, not a hypothetical. It only trims shaded-jar
    // size, not correctness, so it's dropped until that tooling catches up.
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("pluginVersion" to project.version.toString())
    inputs.properties(props)
    filesMatching(listOf("plugin.yml", "config.yml")) {
        expand(props)
    }

    // Localization, config, and gender files are identical to
    // minecraft:bukkit's - reused straight from there instead of keeping
    // copies in sync by hand.
    from(rootDir.resolve("minecraft/bukkit/src/main/resources/localizations")) {
        into("localizations")
    }
    from(rootDir.resolve("minecraft/bukkit/src/main/resources")) {
        include("config.yml", "gender.yml")
    }
}
