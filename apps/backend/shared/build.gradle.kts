plugins {
    id("java")
}

java {
    toolchain {
        val systemJavaVersion = System.getProperty("java.version").split(".")[0].toIntOrNull() ?: 17
        val targetVersion = if (systemJavaVersion >= 21) 21 else systemJavaVersion
        languageVersion.set(JavaLanguageVersion.of(targetVersion))
    }
}

dependencies {
    // Shared library — add APIs used by api, worker, etc.
}
