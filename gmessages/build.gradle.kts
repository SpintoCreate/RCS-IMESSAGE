plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val enableGMessagesPrototype = providers.gradleProperty("enableGMessagesPrototype")
    .map(String::toBoolean).getOrElse(false)
val libgmAar = file("libs/libgm.aar")

if (enableGMessagesPrototype) {
    require(libgmAar.isFile) {
        "Build libs/libgm.aar with the pinned Go and Android NDK toolchain before enabling the prototype."
    }
}

android {
    namespace = "com.unifiedmessages.gmessages"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    if (enableGMessagesPrototype) {
        sourceSets.getByName("debug").java.srcDir("src/prototype/kotlin")
        sourceSets.getByName("debug").manifest.srcFile("src/prototype/AndroidManifest.xml")
        sourceSets.getByName("test").java.srcDir("src/prototypeTest/kotlin")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    if (enableGMessagesPrototype) {
        add("debugCompileOnly", files(libgmAar))
        add("debugImplementation", "androidx.activity:activity-ktx:1.10.1")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
