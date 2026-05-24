plugins {
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
}

group = "com.geostat"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        val major = System.getProperty("java.version").split(".")[0].toIntOrNull() ?: 17
        languageVersion.set(JavaLanguageVersion.of(if (major >= 21) 21 else major))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.geostat.platform:platform-contracts")
    implementation("com.geostat.embedding:embedding-adapters")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.geostat.qdrant:qdrant-client")
    implementation("io.grpc:grpc-netty-shaded:1.68.2")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
