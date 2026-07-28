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

    // spigot-api (not paper-api) so this module only ever compiles against
    // what plain Bukkit/CraftBukkit/Spigot actually provide - this is the
    // single distributable module, installed unmodified on Spigot,
    // CraftBukkit, Paper, and forks like Purpur. Where Paper genuinely
    // offers something better (see LegacyText's Audience capability check),
    // it's detected at runtime instead of adding a paper-api dependency.
    compileOnly("org.spigotmc:spigot-api:${project.findProperty("spigotVersion")}")
    compileOnly("me.clip:placeholderapi:${project.findProperty("placeholderapiVersion")}")
    // FlectonePulse's own published API module - see integration/FlectonePulseAccess.
    compileOnly("net.flectone.pulse:core:${project.findProperty("flectonePulseVersion")}")
    // Needed only for javac to resolve type annotations on FlectonePulse's
    // classes - see the comment on commonsLang3Version in gradle.properties.
    compileOnly("org.apache.commons:commons-lang3:${project.findProperty("commonsLang3Version")}")
    // Unlike paper-api, spigot-api's own pom does not pull in com.mojang:brigadier
    // transitively (verified: no such dependency in its published pom.xml) - but
    // cloud-bukkit transitively depends on cloud-brigadier, whose compiled classes
    // reference com.mojang.brigadier.* types, so javac needs them resolvable even
    // though this module never calls BukkitCommandManager#registerBrigadier(). The
    // actual server jar always bundles brigadier at runtime (vanilla Minecraft uses
    // it internally), so this is compileOnly, matching spigot-api itself above.
    compileOnly("com.mojang:brigadier:1.0.500")

    implementation("org.incendo:cloud-core:${project.findProperty("cloudCoreVersion")}")
    implementation("org.incendo:cloud-bukkit:${project.findProperty("cloudBukkitVersion")}")
    implementation("org.incendo:cloud-annotations:${project.findProperty("cloudCoreVersion")}")

    // Vanilla Spigot/CraftBukkit does not bundle kyori Adventure, and its
    // CommandSender doesn't implement Audience the way Paper's does - see
    // LegacyText's runtime capability check, which uses native Adventure
    // delivery when available and falls back to legacy serialization
    // otherwise. Rather than pull in net.kyori:adventure-platform-bukkit as
    // a bridge for the fallback path (its 4.4.1 release pins adventure-api
    // 4.21.0, which conflicts with the 5.2.0 this project already depends
    // on via core - verified live against its published pom.xml, not
    // assumed), the fallback serialises to legacy formatted strings (same
    // adventure-api major version, no bridge needed) and sends via
    // CommandSender#spigot().sendMessage(BaseComponent...), which every
    // Bukkit implementation has always had.
    implementation("net.kyori:adventure-api:${project.findProperty("adventureVersion")}")
    implementation("net.kyori:adventure-text-minimessage:${project.findProperty("adventureVersion")}")
    implementation("net.kyori:adventure-text-serializer-legacy:${project.findProperty("adventureVersion")}")

    implementation("com.google.inject:guice:${project.findProperty("guiceVersion")}")
    implementation("org.yaml:snakeyaml:${project.findProperty("snakeyamlVersion")}")
    implementation("com.zaxxer:HikariCP:${project.findProperty("hikariVersion")}")
    implementation("com.h2database:h2:${project.findProperty("h2Version")}")
    implementation("com.mysql:mysql-connector-j:${project.findProperty("mysqlVersion")}")
    implementation("org.mariadb.jdbc:mariadb-java-client:${project.findProperty("mariadbVersion")}")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("com.google.inject", "org.misqzy.playergrowth.bukkit.libs.guice")
    relocate("com.zaxxer.hikari", "org.misqzy.playergrowth.bukkit.libs.hikari")
    relocate("org.yaml.snakeyaml", "org.misqzy.playergrowth.bukkit.libs.snakeyaml")
    relocate("org.incendo.cloud", "org.misqzy.playergrowth.bukkit.libs.cloud")
    relocate("net.kyori", "org.misqzy.playergrowth.bukkit.libs.kyori")
    //
    // minimize() is deliberately NOT used: it depends on org.vafer:jdependency,
    // which bundles an ASM version that cannot parse Java 25 class files
    // ("Unsupported class file major version 69") - a real failure hit
    // against this project's Java-25 toolchain, not a hypothetical. It only
    // trims shaded-jar size, not correctness, so it's dropped until that
    // tooling catches up.
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
}
