plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

repositories { mavenCentral() }

val ktorVersion = "3.2.3"
val exposedVersion = "0.61.0"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-di-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.flywaydb:flyway-core:11.3.4")
    implementation("org.flywaydb:flyway-database-postgresql:11.3.4")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}

kotlin { jvmToolchain(21) }
application { mainClass.set("ru.fsp.career.ApplicationKt") }
tasks.test { useJUnitPlatform() }

// Synthetic offline validation of the matching engine (see docs/validation/matching-results.md).
tasks.register<JavaExec>("validateMatching") {
    group = "verification"
    description =
        "Generates the synthetic vacancy–candidate set, evaluates ranking vs baselines and writes docs/validation/matching-*"
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ru.fsp.career.matching.validation.ValidateMatchingKt")
    args(layout.projectDirectory.dir("../../docs/validation").asFile.absolutePath)
}

// Synthetic validation study of the adaptive assessment → docs/validation/assessment-results.{md,json}
tasks.register<JavaExec>("validateAssessment") {
    group = "verification"
    description = "Simulates candidates through the production assessment engine and writes the validation report"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.fsp.career.assessment.validation.AssessmentValidationKt")
    args(layout.projectDirectory.dir("../../docs/validation").asFile.path)
}
