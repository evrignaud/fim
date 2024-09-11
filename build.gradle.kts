import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    application
    jacoco
    id("checkstyle")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.kt3k.coveralls") version "2.12.2"
    id("org.sonarqube") version "5.1.0.4882"
    id("org.asciidoctor.jvm.convert") version "4.0.2"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.guava:guava:33.3.0-jre")
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.atteo:evo-inflector:1.3")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.0")
    testImplementation("org.mockito:mockito-core:5.13.0")
}

group = "org.fim"
version = "2.0.0-SNAPSHOT"
description = "Fim"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("org.fim.Fim")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("fim-fat")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("jar") {
    dependsOn(tasks.named("startShadowScripts"))
    dependsOn(tasks.named("shadowDistTar"))
    dependsOn(tasks.named("shadowDistZip"))
}

tasks.withType<Checkstyle>().configureEach {
    configFile = rootProject.file("checkstyle.xml")
}

tasks.named("check") {
    dependsOn(tasks.named("checkstyleMain"))
    dependsOn(tasks.named("checkstyleTest"))
}

tasks.named("jacocoTestReport") {
    dependsOn(tasks.test)
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

tasks.withType<AsciidoctorTask>() {
    setSourceDir(file("src/main/asciidoc"))
    setBaseDir(file("src/main/asciidoc/docs"))

    jvm {
        jvmArgs = listOf(
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED"
        )
    }
}
