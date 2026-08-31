pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Treadless"

// :stepcore — 步數引擎（Health Connect 寫入、自動/手動模式）
// :glassui  — Liquid Glass 共用視覺元件（來自 compose-liquid-glass 同款配方）
include(":app")
include(":stepcore")
include(":glassui")
