plugins {
    kotlin("jvm") version "2.2.20"
    application
}

group = "no.dniel"
version = "1.3"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.repsy.io/mvn/njoh/public")
    }
}

dependencies {
    implementation("no.njoh:pulse-engine:0.13.0")
}

kotlin {
    jvmToolchain(23)
}

val mainClass = "pacman.PacmanGameKt"

application {
    mainClass.set("pacman.PacmanGameKt")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    manifest { attributes("Main-Class" to mainClass) }
}
