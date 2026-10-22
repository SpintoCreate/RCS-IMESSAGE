import java.io.File

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val rustpushRevision = "a7fab473e7a33325a760635285db2860de8e1cb0"
val upstreamDir = layout.buildDirectory.dir("rustpush")
val cargoTargetDir = layout.buildDirectory.dir("cargo-target")
val generatedJniDir = layout.buildDirectory.dir("generated/jniLibs")

android {
    namespace = "com.unifiedmessages.imessage"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main").jniLibs.srcDir(generatedJniDir)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

val prepareRustpush by tasks.registering {
    group = "build"
    description = "Fetch pinned SSPL rustpush and prepare upstream's compile-only FairPlay placeholders."
    doLast {
        val checkout = upstreamDir.get().asFile
        val gitEnvironment = mapOf(
            "GIT_CONFIG_COUNT" to "1",
            "GIT_CONFIG_KEY_0" to "url.https://github.com/.insteadOf",
            "GIT_CONFIG_VALUE_0" to "git@github.com:",
        )
        fun git(vararg arguments: String) {
            project.exec {
                commandLine("git", *arguments)
                gitEnvironment.forEach { (key, value) -> environment(key, value) }
            }
        }
        if (!File(checkout, ".git").exists()) {
            check(!checkout.exists()) { "Partial rustpush checkout at $checkout; inspect it before rebuilding." }
            git("clone", "--no-checkout", "https://github.com/OpenBubbles/rustpush.git", checkout.absolutePath)
        }
        git("-C", checkout.absolutePath, "fetch", "--depth", "1", "origin", rustpushRevision)
        git("-C", checkout.absolutePath, "checkout", "--detach", rustpushRevision)
        git("-C", checkout.absolutePath, "submodule", "update", "--init", "--recursive", "--depth", "1")
        val actualRevision = providers.exec {
            commandLine("git", "-C", checkout.absolutePath, "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        check(actualRevision == rustpushRevision) { "rustpush revision changed unexpectedly." }

        // Exactly the compile-only placeholders in upstream's own CI workflow.
        // They cannot establish a working Apple activation and are never advertised as such.
        val legacy = File(checkout, "certs/legacy-fairplay")
        val placeholders = File(checkout, "certs/fairplay").also { it.mkdirs() }
        val names = listOf(
            "4056631661436364584235346952193", "4056631661436364584235346952194",
            "4056631661436364584235346952195", "4056631661436364584235346952196",
            "4056631661436364584235346952197", "4056631661436364584235346952198",
            "4056631661436364584235346952199", "4056631661436364584235346952200",
            "4056631661436364584235346952201", "4056631661436364584235346952208",
        )
        names.forEach { name ->
            listOf("crt", "pem").forEach { extension ->
                val source = File(legacy, "fairplay.$extension")
                val target = File(placeholders, "$name.$extension")
                check(source.exists()) { "Missing upstream compile placeholder: $source" }
                if (target.exists()) {
                    check(target.readBytes().contentEquals(source.readBytes())) {
                        "Refusing to overwrite an unexpected FairPlay file: $target"
                    }
                } else {
                    source.copyTo(target)
                }
            }
        }
    }
}

val buildRustArm64 by tasks.registering(Exec::class) {
    group = "build"
    description = "Cross-compile the probe ABI for Android arm64."
    dependsOn(prepareRustpush)
    val hostTag = when {
        System.getProperty("os.name").startsWith("Windows") -> "windows-x86_64"
        System.getProperty("os.name").startsWith("Mac") -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    val commandSuffix = if (hostTag.startsWith("windows")) ".cmd" else ""
    val ndkRoot = providers.environmentVariable("ANDROID_NDK_HOME").orNull
        ?: File(android.sdkDirectory, "ndk/27.2.12479018").absolutePath
    val linker = File(ndkRoot, "toolchains/llvm/prebuilt/$hostTag/bin/aarch64-linux-android29-clang$commandSuffix")
    val archiver = File(ndkRoot, "toolchains/llvm/prebuilt/$hostTag/bin/llvm-ar")
    val ranlib = File(ndkRoot, "toolchains/llvm/prebuilt/$hostTag/bin/llvm-ranlib")
    doFirst {
        check(linker.exists()) { "Android NDK 27.2.12479018 is required: $linker" }
        check(archiver.exists() && ranlib.exists()) { "Android NDK archive tools are required." }
        check(providers.environmentVariable("PROTOC").orNull?.let(::File)?.exists() == true) {
            "Set PROTOC to a trusted protoc executable before building rustpush."
        }
    }
    commandLine("cargo", "build", "--release", "--target", "aarch64-linux-android", "--manifest-path", "${projectDir}/native/Cargo.toml")
    environment("CARGO_TARGET_DIR", cargoTargetDir.get().asFile.absolutePath)
    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", linker.absolutePath)
    environment("CC_aarch64_linux_android", linker.absolutePath)
    environment("AR_aarch64_linux_android", archiver.absolutePath)
    environment("RANLIB_aarch64_linux_android", ranlib.absolutePath)
    environment("RANLIB", ranlib.absolutePath)
    environment("CARGO_NET_GIT_FETCH_WITH_CLI", "true")
    environment("GIT_CONFIG_COUNT", "1")
    environment("GIT_CONFIG_KEY_0", "url.https://github.com/.insteadOf")
    environment("GIT_CONFIG_VALUE_0", "git@github.com:")
}

val stageRustArm64 by tasks.registering(Copy::class) {
    dependsOn(buildRustArm64)
    from(cargoTargetDir.map { it.file("aarch64-linux-android/release/libunified_imessage_native.so") })
    into(generatedJniDir.map { it.dir("arm64-v8a") })
}

tasks.named("preBuild").configure { dependsOn(stageRustArm64) }
