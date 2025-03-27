pluginManagement {
    repositories {
//        mavenCentral()
//        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.gradle.org/gradle/libs-releases")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://maven.aliyun.com/nexus/content/groups/public/")
        maven("https://maven.aliyun.com/nexus/content/repositories/central/")
    }
}

rootProject.name = "code-diff-plugin"