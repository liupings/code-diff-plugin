plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
    id("com.github.sherter.google-java-format") version "0.9"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
//intellij {
//    version.set("2023.3.8")
//    type.set("IC") // Target IDE Platform
//
//    plugins.set(listOf(/* Plugin Dependencies */))
//}


intellij {
    updateSinceUntilBuild.set(false) // 关闭构建范围检查
    localPath.set("C:\\Users\\Administrator\\.gradle\\caches\\modules-2\\files-2.1\\com.jetbrains.intellij.idea\\ideaIC\\2023.3.8\\bfbaf3e3e0fded07b36765dd8ac98c3901d3e106")
    plugins.set(listOf("java", "org.jetbrains.kotlin"))  // 加载依赖插件
    plugins = listOf("java")

}
dependencies {
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21") // 添加反射支持
    implementation("cn.hutool:hutool-all:5.8.16") // 添加反射支持
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("com.fifesoft:rsyntaxtextarea:3.3.0") // 可选，用于更好的语法高亮
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.google.googlejavaformat:google-java-format:1.11.0")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
