import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.paperweight.tasks.RemapJar
import org.gradle.configurationcache.extensions.capitalized
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
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    paperweight.paperDevBundle(libs.versions.paper)
    compileOnly("dev.jorel:commandapi-bukkit-core:9.5.0")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.10.17")
}

extra.apply {
    set("pluginName", project.name.split('-').joinToString("") {
        it.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    })
    set("packageName", project.name.replace("-", ""))
    set("kotlinVersion", libs.versions.kotlin)
    set("paperVersion", libs.versions.paper.get().split('.').take(2).joinToString("."))

    val pluginLibraries = LinkedHashSet<String>()

    configurations.findByName("implementation")?.allDependencies?.forEach { dependency ->
        val group = dependency.group ?: error("group is null")
        var name = dependency.name ?: error("name is null")
        var version = dependency.version

        if (group == "org.jetbrains.kotlin" && version == null) {
            version = getKotlinPluginVersion()
        }

        requireNotNull(version) { "version is null" }
        require(version != "latest.release") { "version is latest.release" }

        pluginLibraries += "$group:$name:$version"
        set("pluginLibraries", pluginLibraries.joinToString("\n  ") { "- $it" })
    }
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}

tasks.reobfJar {
    outputs.upToDateWhen { false }
    doLast {
        // 플러그인 폴더 및 업데이트 폴더 경로 설정
        val plugins = file("Y:\\minecaft\\plugins")
        val update = plugins.resolve("update")

        // 빌드된 JAR 파일
        val builtJar = layout.buildDirectory.file("libs/${rootProject.name}-${project.version}.jar").get().asFile

        // 복사 작업
        copy {
            from(builtJar)
            if (plugins.resolve(builtJar.name).exists()) {
                into(update)  // update 폴더로 복사
            } else {
                into(plugins)  // plugins 폴더로 복사
            }
        }

        // 업데이트 폴더의 UPDATE 파일 삭제 예약
        update.resolve("UPDATE").deleteOnExit()
    }
}