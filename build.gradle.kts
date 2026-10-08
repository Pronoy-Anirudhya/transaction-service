plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bracits"
version = "0.0.1-SNAPSHOT"
description = "transaction-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Optional isolated build directory, so several Gradle runs can work in one checkout in parallel:
//   ./gradlew --project-cache-dir .gradle-x -PagentBuildDir=build-x test
providers.gradleProperty("agentBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }

repositories {
    mavenCentral()
}

val springdocVersion = "3.1.0"
val wiremockVersion = "3.13.1"
val jqwikVersion = "1.9.3"
val swaggerParserVersion = "2.1.35"
val jsonSchemaValidatorVersion = "1.5.9"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("net.jqwik:jqwik:$jqwikVersion")
    testImplementation("org.awaitility:awaitility")
    testImplementation("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Contract files (OpenAPI + event JSON Schema) live at the repo root and are served from the classpath.
tasks.processResources {
    from("openapi") {
        into("openapi")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation,unchecked"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    systemProperty("user.timezone", "UTC")
}
