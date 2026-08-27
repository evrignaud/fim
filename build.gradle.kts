import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    application
    jacoco
    id("checkstyle")
    id("com.gradleup.shadow") version "8.3.11"
    id("com.github.spotbugs") version "6.5.11"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.guava:guava:33.7.1-jre")
    implementation("commons-cli:commons-cli:1.11.0")
    implementation("commons-io:commons-io:2.22.0")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("org.atteo:evo-inflector:2.0")
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // The JUnit BOM keeps junit-jupiter and junit-platform (engine, commons, launcher) on aligned
    // versions. Declaring the launcher explicitly avoids the older launcher Gradle would otherwise
    // supply, which is missing APIs the 1.14.x engine expects (OutputDirectoryCreator).
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.23.0")
}

group = "org.fim"
version = "2.0.0-SNAPSHOT"
description = "Fim"

// A release is driven by the tag that was pushed, so the release workflow passes the version it is
// building rather than relying on this file agreeing with the tag. Without that, a tag pushed by hand
// would produce archives named after whatever the line above happens to say.
//
// Kept as a separate statement so that the assignment above stays a plain `version = "..."` line, which
// is what release.py reads and rewrites when preparing a release locally.
(findProperty("fimVersion") as String?)?.takeIf { it.isNotBlank() }?.let { version = it }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass.set("org.fim.Fim")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

// The tests come in two tiers, so that the thorough scenarios do not have to be traded away to keep the
// ordinary build quick. See org.fim.tooling.TestTiers for what each tag means; the tags are literals here
// because a build script cannot reference the classes it is about to compile.
//
//   ./gradlew test            everything except the slow tier
//   ./gradlew slowTest        only the slow tier
//   ./gradlew test slowTest   all of it
//
// The slow tier is where the large-file and full hash-mode-matrix scenarios live. It is deliberately not
// wired into `check`: a suite everybody waits for on every build is a suite that ends up trimmed.
// A failing test has to say what it was expecting, in the log, where whoever is looking will see it.
// By default Gradle reports only that a test failed and leaves the detail in an HTML report, which on a
// runner means downloading an artifact to find out what happened.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("slow")
    }
}

tasks.register<Test>("slowTest") {
    group = "verification"
    description = "Runs the slow tier: large files and the whole hash mode matrix."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("slow")
    }

    shouldRunAfter(tasks.named("test"))
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

// Static analysis.
//
// This replaces the Coverity Scan that used to be triggered by pushing to the coverity_scan branch
// from Travis. SpotBugs looks for the same class of defect and needs no account and no token, so it
// runs the same way on this machine as it does on a runner. The workflow additionally hands the sarif
// report below to GitHub code scanning, which is where the findings are read.
//
// It reports instead of failing the build. The findings on an existing codebase are a backlog rather
// than a regression, and blocking every build until that backlog is empty would only lead to the
// check being switched off. Set ignoreFailures to false once the count reaches zero to keep it there.
spotbugs {
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.DEFAULT
    ignoreFailures = true
    excludeFilter = rootProject.file("spotbugs-exclude.xml")
}

// The sarif report is what carries the findings into the Security tab of the GitHub project, which is where
// Coverity used to put them. It sits alongside the html one so that a local run still has something readable.
//
// The locale is pinned because SpotBugs translates its descriptions using the default one. Left alone, the
// same finding reads differently depending on who ran the analysis, and a report uploaded from a French
// machine arrives in French. Findings should not depend on the machine that produced them.
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    jvmArgs = listOf("-Duser.language=en", "-Duser.country=US")

    reports.register("html") {
        required = true
    }
    reports.register("sarif") {
        required = true
    }
    reports.register("xml") {
        required = false
    }
}

tasks.named("check") {
    dependsOn(tasks.named("checkstyleMain"))
    dependsOn(tasks.named("checkstyleTest"))

    // The coverage floor is part of the gate rather than something only the workflow looks at, so that a
    // drop is caught by `./gradlew check` and by the pre-push hook, before it ever reaches GitHub.
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// Coverage.
//
// The xml report is the one anything other than a human reads: the workflow derives the percentage and the
// badge from it. It is off by default in Gradle, so it has to be asked for.
//
// Execution data is collected from every test task rather than only from `test`, so that running the slow
// tier adds to the picture instead of being invisible. The file tree is resolved when the report runs, so a
// tier that was not run simply contributes nothing.
//
// The one class under com/blackducksoftware is third-party code vendored into the tree, carrying its own
// Apache licence and its own history. Counting it would measure how well Fim tests somebody else's code, so
// it is left out of both the coverage figure and the static analysis.
val vendoredClasses = "com/blackducksoftware/**"

// Both the report and the threshold check read the same data over the same classes.
tasks.withType<JacocoReportBase>().configureEach {
    dependsOn(tasks.named("test"))

    // Reading the whole jacoco directory means this task consumes the output of every test task, including
    // the slow tier when it was asked for. Gradle rejects that unless the ordering is stated, since
    // otherwise the report could be written while a tier is still running.
    mustRunAfter(tasks.withType<Test>())

    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))

    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude(vendoredClasses) }
    }))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
}

// A floor rather than a target.
//
// Coverage sits at about 93% of instructions and 85% of branches. The limits below are a few points under
// that, which is enough room for ordinary work and still catches a change that drops a chunk of the code out
// of the tests. Setting them at the current figure would fail on the first commit that adds a line; setting
// them far below would never fail at all.
//
// Raise them when the real numbers move up. They are meant to follow the code, not to be aspirational.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.82".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

tasks.withType<AsciidoctorTask>() {
    setSourceDir(file("src/main/asciidoc"))
    setBaseDir(file("src/main/asciidoc/docs"))

    // The rendered pages ask for `css/style.css` and `images/...`, so those have to travel with them.
    // Without this the task produced only HTML, and a site assembled from its output came out with no
    // styling and every image broken. What is published now needs nothing added by hand.
    resources {
        from(file("src/main/asciidoc")) {
            include("docs/css/**", "docs/images/**", "slides/css/**", "slides/images/**")
        }
    }

    jvm {
        jvmArgs = listOf(
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED"
        )
    }
}
