import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
	kotlin("jvm") version "2.4.20"
	id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
	id("org.jetbrains.compose") version "1.12.0"
}

version = "1.0.0"
group = "jadx.compose"

val jewelVersion = "0.41.0-262.10968.63"

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
	implementation("io.github.skylot:jadx-core:local")
	implementation("io.github.skylot:jadx-rename-mappings:local")
	runtimeOnly("io.github.skylot:jadx-dex-input:local")
	runtimeOnly("io.github.skylot:jadx-java-input:local")
	runtimeOnly("io.github.skylot:jadx-smali-input:local")
	implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
	implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")
	implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:$jewelVersion")
	implementation("com.jetbrains.intellij.platform:icons:262.10315.125")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
	implementation("com.formdev:flatlaf-fonts-jetbrains-mono:2.304")
	runtimeOnly("org.slf4j:slf4j-simple:2.0.20")
	testImplementation(kotlin("test-junit"))
	testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.0")
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
			packageVersion = project.version.toString()
			modules("java.desktop", "java.logging", "java.prefs", "java.xml", "jdk.unsupported")
			macOS {
				bundleID = "io.github.flyfishxu.jadx.compose"
				iconFile.set(file("../jadx-gui/dist/macos/jadx-logo.icns"))
			}
			windows { iconFile.set(file("../jadx-gui/dist/windows/jadx-logo.ico")) }
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
