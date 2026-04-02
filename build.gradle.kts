plugins {
    kotlin("jvm") version "2.3.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.katacr"
version = "1.3.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }

    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
    maven("https://repo.alessiodp.com/releases/"){
        name = "libby"
    }
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")  // PAPI 可选依赖
    compileOnly("fr.xephi:authme:5.6.1-SNAPSHOT")  // AuthMe 可选依赖

    // Libby - 用于运行时下载依赖
    implementation("net.byteflux:libby-bukkit:1.3.0")

    // 以下依赖在运行时通过 Libby 下载
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.mindrot:jbcrypt:0.4")
    compileOnly("org.xerial:sqlite-jdbc:3.46.1.0")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    // 让默认的 build 任务执行 shadowJar
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        relocate("net.byteflux", project.group.toString() + ".libby")
        archiveClassifier.set("")
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
