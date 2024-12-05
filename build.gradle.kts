import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import java.util.*

plugins {
    idea
    alias(libs.plugins.kotlin)
    alias(libs.plugins.paperweight)
    alias(libs.plugins.shadow)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.oraxen.com/releases")
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    paperweight.paperDevBundle(libs.versions.paper)
    compileOnly("dev.jorel:commandapi-bukkit-core:9.5.0")
    compileOnly("io.th0rgal:oraxen:1.184.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.10.17")
}

extra.apply {
    set("pluginName", project.name.split('-').joinToString("") {
        it.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    })
    set("packageName", project.name.replace("-", "").lowercase())
    set("kotlinVersion", libs.versions.kotlin)
    set("paperVersion", libs.versions.paper.get().split('.').take(2).joinToString("."))

    val pluginLibraries = LinkedHashSet<String>()

    configurations.findByName("implementation")?.allDependencies?.forEach { dependency ->
        val group = dependency.group ?: error("group is null")
        var name = dependency.name ?: error("name is null")
        var version = dependency.version

        if (group == "org.jetbrains.kotlin" && version == null) {
            version = getKotlinPluginVersion()
        } else if (group == "io.github.monun.tap" && name.endsWith("-api")) {
            name = name.removeSuffix("api") + "core"
        }

        requireNotNull(version) { "version is null" }
        require(version != "latest.release") { "version is latest.release" }

        pluginLibraries += "$group:$name:$version"
        set("pluginLibraries", pluginLibraries.joinToString("\n  ") { "- $it" })
    }
}

tasks {
    processResources {
        filesMatching("*.yml") {
            expand(project.properties)
            expand(extra.properties)
        }
    }
    fun registerJar(name: String, bundle: Boolean) {
        val taskName = name + "Jar"

        register<ShadowJar>(taskName) {
            outputs.upToDateWhen { false }

            from(sourceSets["main"].output)

            if (bundle) {
                configurations = listOf(
                    project.configurations.runtimeClasspath.get()
                )
                exclude("clip-plugin.yml")
                rename("bundle-plugin.yml", "plugin.yml")
            } else {
                exclude("bundle-plugin.yml")
                rename("clip-plugin.yml", "plugin.yml")
            }

            doLast {
                // 플러그인 폴더 및 업데이트 폴더 경로 설정
                val plugins = file("G:\\minecaft\\plugins")
                val update = plugins.resolve("update")

                // 복사 작업
                copy {
                    from(archiveFile)

                    if (plugins.resolve(archiveFileName.get()).exists())
                        into(update)
                    else
                        into(plugins)
                }

                // 업데이트 폴더의 UPDATE 파일 삭제 예약
                update.resolve("UPDATE").deleteOnExit()
            }
        }
    }
    registerJar("clip", false)
    registerJar("bundle", true)
}