import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
	// The root buildSrc already provides the catalog's Kotlin JVM plugin.
	id("org.jetbrains.kotlin.jvm")
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.compose)
}

version = rootProject.extra["jadxVersion"] as String
group = "io.github.skylot"

kotlin {
	compilerOptions {
		optIn.addAll("androidx.compose.foundation.ExperimentalFoundationApi", "androidx.compose.ui.text.ExperimentalTextApi", "org.jetbrains.jewel.foundation.ExperimentalJewelApi")
	}
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
		vendor.set(JvmVendorSpec.JETBRAINS)
	}
}

configurations.configureEach {
	// Standalone apps need one coroutine implementation, not the IDE's fork as well.
	exclude(group = "org.jetbrains.intellij.deps.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

dependencies {
	implementation(project(":jadx-core"))
	implementation(project(":jadx-plugins:jadx-rename-mappings"))
	runtimeOnly(project(":jadx-plugins:jadx-dex-input"))
	runtimeOnly(project(":jadx-plugins:jadx-java-input"))
	runtimeOnly(project(":jadx-plugins:jadx-smali-input"))
	implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
	implementation(libs.jewel.standalone)
	implementation(libs.jewel.decorated.window)
	implementation(libs.intellij.icons)
	implementation(libs.kotlinx.coroutines.swing)
	implementation(libs.flatlaf.fonts.jetbrains.mono)
	runtimeOnly(libs.slf4j.simple)
	testImplementation(kotlin("test-junit"))
	testImplementation(libs.compose.ui.test)
}

val jbr = javaToolchains.launcherFor {
	languageVersion.set(JavaLanguageVersion.of(25))
	vendor.set(JvmVendorSpec.JETBRAINS)
}

compose.desktop {
	application {
		mainClass = "jadx.compose.MainKt"
		javaHome = providers.environmentVariable("JBR_HOME")
			.orElse(jbr.map { it.metadata.installationPath.asFile.absolutePath }).get()
		jvmArgs("-Xmx4g", "--enable-native-access=ALL-UNNAMED", "-Dapple.awt.application.appearance=system", "-Dapple.awt.application.name=Jadx Compose", "-Dcom.apple.mrj.application.apple.menu.about.name=Jadx Compose")
		if (System.getProperty("os.name").startsWith("Mac")) jvmArgs("--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED", "-Xdock:name=Jadx Compose", "-Dapple.laf.useScreenMenuBar=true")
		nativeDistributions {
			targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
			packageName = "Jadx Compose"
			// Native installers require a numeric version, including for dev builds.
			packageVersion = project.version.toString().takeIf { it.matches(Regex("\\d+(\\.\\d+){0,2}")) } ?: "1.0.0"
			modules("java.desktop", "java.logging", "java.prefs", "java.xml", "jdk.unsupported")
			macOS {
				bundleID = "io.github.flyfishxu.jadx.compose"
				iconFile.set(rootProject.file("jadx-gui/dist/macos/jadx-logo.icns"))
			}
			windows { iconFile.set(rootProject.file("jadx-gui/dist/windows/jadx-logo.ico")) }
		}
	}
}

tasks.test {
	maxHeapSize = "4g"
	listOf("testApk", "testMapping", "testFixture").forEach { key ->
		providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
	}
	javaLauncher.set(jbr)
	jvmArgs("--enable-native-access=ALL-UNNAMED")
	testLogging { events("passed", "skipped", "failed") }
}
