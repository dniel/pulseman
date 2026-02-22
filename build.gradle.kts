plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "no.dniel"
version = "2.6"

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

val mainClass = "pulseman.PulseManGameKt"

application {
    mainClass.set("pulseman.PulseManGameKt")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    manifest { attributes("Main-Class" to mainClass) }
}
