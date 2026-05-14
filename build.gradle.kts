plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("info.solidsoft.pitest") version "1.19.0-rc.2"
    jacoco
}

group = "com.kraft"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

val snippetsDir = layout.buildDirectory.dir("generated-snippets")
val useExternalSnippets = project.findProperty("useExternalSnippets") == "true"
val docsSourceDir = layout.projectDirectory.dir("src/docs/asciidoc")
val docsOutputDir = layout.buildDirectory.dir("docs/asciidoc")

val asciidoctorCli by configurations.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:6.10.0")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.webjars:webjars-locator-core")
    implementation("org.webjars:bootstrap:5.3.3")
    implementation("org.webjars.npm:bootstrap-icons:1.11.3")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:mariadb:1.21.4")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    asciidoctorCli("org.asciidoctor:asciidoctorj-cli:3.0.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    outputs.dir(snippetsDir)
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("it")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("asciidoctor") {
    group = "documentation"
    description = "Generates HTML docs from AsciiDoc sources."
    classpath = asciidoctorCli
    mainClass.set("org.asciidoctor.cli.jruby.AsciidoctorInvoker")
    workingDir = projectDir

    if (!useExternalSnippets) {
        dependsOn(tasks.test)
        dependsOn("integrationTest")
    }

    inputs.dir(docsSourceDir)
    inputs.dir(snippetsDir)
    outputs.dir(docsOutputDir)

    doFirst {
        docsOutputDir.get().asFile.mkdirs()
    }

    args(
        "-b", "html5",
        "-D", docsOutputDir.get().asFile.absolutePath,
        "-a", "snippets=${snippetsDir.get().asFile.absolutePath}",
        docsSourceDir.file("index.adoc").asFile.absolutePath
    )
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests tagged with @Tag(\"it\")"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("it") }
    shouldRunAfter("test")
}

tasks.register<Test>("performanceSmokeTest") {
    group = "verification"
    description = "Runs lightweight performance smoke tests tagged with @Tag(\"perf\")"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("perf") }
    shouldRunAfter("integrationTest")
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(
        setOf(
            "com.kraft.lotto.feature.recommend.domain.*",
            "com.kraft.lotto.feature.winningnumber.domain.*"
        )
    )
    targetTests.set(
        setOf(
            "com.kraft.lotto.feature.recommend.domain.*",
            "com.kraft.lotto.feature.winningnumber.domain.*"
        )
    )
    excludedClasses.set(
        setOf(
            "com.kraft.lotto.feature.recommend.domain.ExclusionRule",
            "com.kraft.lotto.feature.recommend.domain.PastWinningCache"
        )
    )
    excludedMethods.set(setOf("reason"))
    mutators.set(setOf("STRONGER"))
    threads.set(2)
    useClasspathFile.set(true)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(false)
    mutationThreshold.set(80)
}

tasks.named("check") {
    dependsOn("integrationTest")
    dependsOn("jacocoTestCoverageVerification")
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJarWithDocs") {
    group = "build"
    description = "Builds bootJar with REST Docs included."
    dependsOn("asciidoctor")
    mainClass.set("com.kraft.lotto.KraftLottoApplication")
    targetJavaVersion.set(JavaVersion.VERSION_25)
    archiveFileName.set("app-with-docs.jar")
    from(layout.buildDirectory.dir("docs/asciidoc")) {
        into("BOOT-INF/classes/static/docs")
    }
    with(tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get())
}

tasks.jar {
    enabled = false
}

tasks.register("printRequiredEnvVars") {
    group = "help"
    description = "Print environment variable names required for production deploy checks."
    doLast {
        val validatorFile = file("src/main/java/com/kraft/lotto/infra/config/RequiredConfigValidator.java")
        val content = validatorFile.readText()
        val block = Regex("""REQUIRED_DEPLOY_ENV_VARS\s*=\s*List\.of\(([\s\S]*?)\);""")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?: error("REQUIRED_DEPLOY_ENV_VARS block not found in RequiredConfigValidator.java")
        Regex(""""([A-Z0-9_]+)"""")
            .findAll(block)
            .map { it.groupValues[1] }
            .toList()
            .distinct()
            .forEach { println(it) }
    }
}
