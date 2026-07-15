import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.focusflight"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.focusflight"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        prefab = true
    }
}

tasks.register("cargoNdkBuild") {
    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val absoluteRustPath = if (isWindows) {
            "c:/Users/kamme/Desktop/CesiumRS"
        } else {
            "/home/silas270/CesiumRS"
        }
        val targets = mapOf(
            "aarch64-linux-android" to "arm64-v8a"
        )

        val cargoBin = if (isWindows) "cargo" else "/home/silas270/.cargo/bin/cargo"
        val ndkDir = if (isWindows) {
            System.getenv("ANDROID_NDK_HOME") ?: "C:/Users/kamme/AppData/Local/Android/Sdk/ndk/30.0.14904198"
        } else {
            "/home/silas270/android-sdk/ndk/27.1.12297006"
        }

        targets.forEach { (rustTarget, androidAbi) ->
            println("Building Rust library for target: $rustTarget (ABI: $androidAbi)...")
            
            val builder = ProcessBuilder(cargoBin, "ndk", "--target", rustTarget, "build", "--lib", "--release", "--no-default-features")
            builder.directory(File(absoluteRustPath))
            
            builder.environment()["ANDROID_NDK_HOME"] = ndkDir
            if (!isWindows) {
                builder.environment()["PATH"] = "/home/silas270/.cargo/bin:" + System.getenv("PATH")
            }
            
            val logFile = File(absoluteRustPath, "cargo_build.log")
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            builder.redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            
            val process = builder.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException("cargo ndk build failed with exit code $exitCode. See cargo_build.log in CesiumRS for details.")
            }
            
            val soFile = File("$absoluteRustPath/target/$rustTarget/release/libcesium_rs.so")
            val destDir = File(projectDir, "src/main/jniLibs/$androidAbi")
            destDir.mkdirs()
            soFile.copyTo(File(destDir, "libcesium_rs.so"), overwrite = true)
            println("Successfully copied libcesium_rs.so for ABI: $androidAbi")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("cargoNdkBuild")
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("androidx.games:games-activity:3.0.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation(libs.gson)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}