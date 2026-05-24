plugins {
    `java-library`
}

group = "com.geostat.platform"
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
