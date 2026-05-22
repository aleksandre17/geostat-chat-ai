plugins {
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
}

group = "com.geostat"
version = "2.0.0-SNAPSHOT"

java {
    toolchain {
        val systemJavaVersion = System.getProperty("java.version").split(".")[0].toIntOrNull() ?: 17
        val targetVersion = if (systemJavaVersion >= 21) 21 else systemJavaVersion
        languageVersion.set(JavaLanguageVersion.of(targetVersion))
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
