val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

val exposed_version: String by project
val h2_version: String by project
plugins {
	kotlin("jvm") version "1.9.10"
	id("io.ktor.plugin") version "2.3.5"
	id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
}

group = "org.ecorous"
version = "0.0.1"

application {
	mainClass.set("org.ecorous.ApplicationKt")
	
	val isDevelopment: Boolean = project.ext.has("development")
	applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}

repositories {
	mavenCentral()
	maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
}

dependencies {
	/*implementation("io.ktor:ktor-server-core-jvm")
	implementation("io.ktor:ktor-server-host-common-jvm")
	implementation("io.ktor:ktor-server-status-pages-jvm")
	implementation("io.ktor:ktor-server-content-negotiation-jvm")
	implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
	implementation("io.ktor:ktor-server-html-builder-jvm")
	implementation("org.jetbrains:kotlin-css-jvm:1.0.0-pre.129-kotlin-1.4.20")
	implementation("io.ktor:ktor-server-mustache-jvm")
	implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
	implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
	implementation("org.xerial:sqlite-jdbc:3.43.2.1")
	implementation("io.ktor:ktor-server-websockets-jvm")
	implementation("io.ktor:ktor-server-netty-jvm")
	implementation("ch.qos.logback:logback-classic:$logback_version")
	testImplementation("io.ktor:ktor-server-tests-jvm")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")*/
	
	implementation(libs.bundles.ktor)
	implementation(libs.bundles.exposed)
	implementation(libs.kotlin.css)
	implementation(libs.logback)
	//implementation(libs.sqlite)
	//implementation(libs.h2)
	implementation(libs.postgresql)
	implementation(libs.nbvcxz)
	implementation(libs.password4j)
	implementation(libs.catppuccin)
	implementation(libs.kotlinx.datetime)
	testImplementation(libs.ktor.server.tests)
	testImplementation(libs.kotlin.test.junit)
}
