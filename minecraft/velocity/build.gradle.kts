repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:${project.findProperty("velocityVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${project.findProperty("velocityVersion")}")
}

// minecraft:velocity intentionally does not depend on core
// (see docs/ARCHITECTURE.md) - it only relays opaque bytes on the
// flectonegrowth:sync channel between backend servers and never decodes a
// SyncMessage, so it stays a plain jar with no runtime dependencies to
// shade and no shadow plugin applied.
//
// No velocity-plugin.json is checked in: the velocity-api annotation processor
// (registered above) generates it from the @Plugin annotation at compile time.
