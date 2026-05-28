plugins {
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
}

group = "com.geostat"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["spring-ai.version"] = "2.0.0-M2"

dependencies {
    implementation("com.geostat.platform:platform-contracts")
    implementation("com.geostat.embedding:embedding-adapters")
    implementation(platform("org.springframework.ai:spring-ai-bom:${property("spring-ai.version")}"))
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("com.github.haifengl:smile-core:3.1.1")
    implementation("edu.uci.ics:crawler4j:4.4.0") {
        exclude(group = "com.sleepycat", module = "je")
    }
    implementation("com.geostat.qdrant:qdrant-client")
    implementation("io.grpc:grpc-netty-shaded:1.68.2")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.microsoft.playwright:playwright:1.50.0")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation(platform("software.amazon.awssdk:bom:2.29.45"))
    implementation("software.amazon.awssdk:s3")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
