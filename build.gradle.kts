import org.jetbrains.kotlin.builtins.StandardNames.FqNames.target
import org.jetbrains.kotlin.js.inline.clean.removeUnusedImports

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
    id("com.diffplug.spotless") version "6.22.0"
//    id("com.github.sherter.google-java-format") version "0.9"
}
//googleJavaFormat {
//    toolVersion = "1.25.0"
//}

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
//    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
//    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21") // 添加反射支持
    implementation("cn.hutool:hutool-all:5.8.16")
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("com.fifesoft:rsyntaxtextarea:3.3.0") //  语法高亮
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.google.googlejavaformat:google-java-format:1.17.0")
    implementation("com.github.javaparser:javaparser-core:3.26.3")
    implementation("com.github.javaparser:javaparser-core-serialization:3.26.3")
//    implementation("org.projectlombok:lombok:1.18.30")
//    annotationProcessor("org.projectlombok:lombok:1.18.30") // 必须加上这个，否则注解不会生效
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
// Spotless 配置
spotless {
    java {
        target("src/**/*.java")  // 格式化所有 Java 文件

        // 使用 Google Java Format
        googleJavaFormat("1.17.0").aosp()  // 使用 AOSP 风格 (4空格缩进)
        // 或者使用默认 Google 风格 (2空格缩进)
        // googleJavaFormat("1.17.0")

        // 可选: 移除未使用的 imports
//        removeUnusedImports()

        // 可选: 格式化 imports 顺序
//        importOrder()
    }
}
tasks.withType<JavaExec> {
    jvmArgs = listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "-Xmx512m",
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8"
    )
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
