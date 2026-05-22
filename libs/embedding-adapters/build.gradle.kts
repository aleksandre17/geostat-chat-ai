plugins {
    `java-library`
}

group = "com.geostat.embedding"
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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
