plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.0"
}

group = "com.slyph"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.110-stable")

    implementation("net.dv8tion:JDA:6.5.0") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("com.mysql:mysql-connector-j:26.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testRuntimeOnly("io.papermc.paper:paper-api:26.2.build.110-stable")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveBaseName = "CloverDiscordLink"
        archiveClassifier = ""
        duplicatesStrategy = DuplicatesStrategy.WARN
        mergeServiceFiles()
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
    }
}
