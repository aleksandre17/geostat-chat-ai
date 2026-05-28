plugins {
    `java-library`
}

group = "com.geostat.qdrant"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("io.qdrant:client:1.13.0")
    implementation("io.grpc:grpc-netty-shaded:1.68.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
