// Updated for Jan 2026 standards
val springAiVersion = "2.0.0-M2"

plugins {
    id("org.springframework.boot") version "4.0.2" // Spring Boot 4 is the new stable baseline
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
}

group = "com.geostat"
version = "2.0.0-SNAPSHOT"

java {
    toolchain {
        // Spring Boot 4 + Spring AI 2.0 prefers Java 21, but IDE/Gradle can fail when the machine
        // doesn't have Java 21 and toolchain downloads aren't configured. Use a safe fallback:
        // If the system JDK is already >= 21 we'll request 21, otherwise use the system major version
        // so Gradle uses the local JDK instead of attempting a download.
        val systemJavaVersion = System.getProperty("java.version").split(".")[0].toIntOrNull() ?: 17
        val targetVersion = if (systemJavaVersion >= 21) 21 else systemJavaVersion
        languageVersion.set(JavaLanguageVersion.of(targetVersion))
    }
}

repositories {
    mavenCentral()
    // Required for Milestone (M2) releases of Spring AI
    maven { url = uri("https://repo.spring.io/milestone") }
}

subprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

extra["spring-ai.version"] = springAiVersion

val activeModules: List<String>? = (findProperty("activeModules") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }

gradle.taskGraph.whenReady {
    if (activeModules.isNullOrEmpty()) return@whenReady
    val allowed = activeModules.toSet()
    (listOf(rootProject) + rootProject.subprojects).forEach { p ->
        val key = if (p == rootProject) "root" else p.name
        if (key !in allowed) {
            p.tasks.configureEach { enabled = false }
        }
    }
    if ("root" !in allowed) {
        rootProject.tasks.matching { it.name == "bootJar" }.configureEach { enabled = false }
    }
    logger.lifecycle("Gradle activeModules filter applied: {}", allowed)
}

dependencies {
    implementation("com.geostat.platform:platform-contracts")
    // 1. The BOMs (Bill of Materials) - These manage all versioning for you
    implementation(platform("org.springframework.ai:spring-ai-bom:${property("spring-ai.version")}"))

    // Ensure gRPC libs are consistent to avoid runtime NoClassDefFoundError
    implementation(platform("io.grpc:grpc-bom:1.70.0"))

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // 2. Core Web Stack
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // 3. THE FIX: Corrected Spring AI & Dotenv Artifacts
    // Use the consolidated starter for Google GenAI
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")

    // 4. Google Cloud Speech (Jan 2026 Stable)
    implementation("com.google.cloud:google-cloud-speech:4.27.0")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<Test> {
    useJUnitPlatform()
}