pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin") {
            name = "aliyun-gradle-plugin"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "KaLogin"

// include("KaMenu")  // KaMenu 是独立项目，不作为子模块参与构建，避免 Paper API 版本冲突
// include("KaLoginListenerTest")
