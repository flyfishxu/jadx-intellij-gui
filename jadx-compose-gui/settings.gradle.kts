pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		google()
		maven("https://www.jetbrains.com/intellij-repository/snapshots") {
			content { includeGroupByRegex("com\\.jetbrains\\..*") }
		}
	}
}

rootProject.name = "jadx-compose-gui"

// Keep this frontend out of upstream's settings, plugins and version catalog.
// Composite substitution always builds the core/plugins from this checkout.
includeBuild("..") {
	name = "jadx-upstream"
	dependencySubstitution {
		substitute(module("io.github.skylot:jadx-rename-mappings")).using(project(":jadx-plugins:jadx-rename-mappings"))
		substitute(module("io.github.skylot:jadx-core")).using(project(":jadx-core"))
		listOf("dex", "java", "smali").forEach {
			substitute(module("io.github.skylot:jadx-$it-input"))
				.using(project(":jadx-plugins:jadx-$it-input"))
		}
	}
}
