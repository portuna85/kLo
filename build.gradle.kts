plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.4"
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
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    outputs.dir(snippetsDir)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named("asciidoctor") {
    if (!useExternalSnippets) {
        dependsOn(tasks.test)
        dependsOn("integrationTest")
    }
    // inputs.dir(snippetsDir) 제거:
    //   asciidoctor 플러그인이 이를 필수 입력($4 property)으로 등록하여
    //   -x test 시 디렉토리가 없으면 구성 시점 검증 실패가 발생함.
    //   Dockerfile에서 mkdir -p로 디렉토리를 보장하므로 여기서는 등록 불필요.
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests tagged with @Tag(\"it\")"
    useJUnitPlatform { includeTags("it") }
    shouldRunAfter("test")
}

tasks.named("check") {
    dependsOn("integrationTest")
}

tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJarWithDocs") {
    group = "build"
    description = "Builds bootJar with REST Docs included."
    dependsOn("asciidoctor")
    // mainClass를 직접 지정 (lazy provider / SpringBootExtension은 구성 시점에 값 없음)
    // find src/main/java -name "*Application.java" 로 실제 경로 확인 후 수정
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
