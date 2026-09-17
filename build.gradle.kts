plugins {
    kotlin("jvm") version "2.0.20"
    application
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
    implementation("org.telegram:telegrambots-longpolling:9.0.0")
    implementation("org.telegram:telegrambots-client:9.0.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.dairn.storyteller.telegram.TelegramBotMainKt")
}
