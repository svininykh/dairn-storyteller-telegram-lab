plugins {
    kotlin("jvm") version "2.0.20"
}

group = "org.dairn.storyteller"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.svininykh.dairn-gm:dairn-gm-great-steppe:v0.1.0-preview.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
