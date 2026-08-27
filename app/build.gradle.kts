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
    packaging {
        jniLibs {
            // Debug/profiling builds (`-Pcesium.profile=profiling`) embed native debug
            // symbols so on-device simpleperf/Perfetto captures can be symbolicated;
            // without this AGP strips them from the packaged .so even in debug builds.
            keepDebugSymbols += "**/*.so"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

tasks.register("cargoNdkBuild") {
    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val userHome = System.getProperty("user.home")
        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")

        // -Pcesium.profile=profiling builds CesiumRS's `profiling` Cargo profile (release
        // codegen, kept debug symbols) with the `perf_trace` feature on, for on-device
        // CPU/RAM profiling (see tools/run_perf_scenario.sh). Defaults to the normal
        // shipped `release` profile so day-to-day builds are unaffected.
        val cesiumProfile = (project.findProperty("cesium.profile") as String?) ?: "release"

        // CESIUM_RS_HOME lets each dev machine point at its own CesiumRS checkout;
        // falls back to the historical per-machine defaults if unset.
        val absoluteRustPath = System.getenv("CESIUM_RS_HOME") ?: if (isWindows) {
            "c:/Users/kamme/Desktop/CesiumRS"
        } else {
            "$userHome/CesiumRS"
        }
        val targets = mapOf(
            "aarch64-linux-android" to "arm64-v8a"
        )

        // rustup installs put cargo in ~/.cargo/bin; a system package (e.g. pacman,
        // apt) puts it on PATH instead - prefer whichever actually exists.
        val rustupCargo = File("$userHome/.cargo/bin/cargo")
        val cargoBin = if (isWindows) "cargo" else if (rustupCargo.exists()) rustupCargo.absolutePath else "cargo"
        // ANDROID_NDK_HOME wins if set; otherwise derive from the SDK root env var
        // (ANDROID_HOME/ANDROID_SDK_ROOT), falling back to the historical
        // per-machine hardcoded paths as a last resort.
        val ndkDir = System.getenv("ANDROID_NDK_HOME")
            ?: sdkRoot?.let { "$it/ndk/27.1.12297006" }
            ?: if (isWindows) {
                "C:/Users/kamme/AppData/Local/Android/Sdk/ndk/30.0.14904198"
            } else {
                "$userHome/android-sdk/ndk/27.1.12297006"
            }

        targets.forEach { (rustTarget, androidAbi) ->
            println("Building Rust library for target: $rustTarget (ABI: $androidAbi)...")
            
            // debug_panel (not the full "testing" default) pulls in egui just far enough to
            // draw the city-label pills; app.rs skips the actual debug-sliders window on
            // Android, so this doesn't put any dev UI in front of the real app.
            //
            // cesium.profile=profiling swaps in the `profiling` Cargo profile (release
            // codegen, debug symbols kept) plus the `perf_trace` feature (ATrace spans +
            // finer per-subsystem timings); output then lands under a `profiling/` (not
            // `release/`) target directory, matching Cargo's `--profile` naming.
            val cargoFeatures = if (cesiumProfile == "profiling") "debug_panel,perf_trace" else "debug_panel"
            val cargoArgs = if (cesiumProfile == "release") {
                listOf("--release")
            } else {
                listOf("--profile", cesiumProfile)
            }
            val builder = ProcessBuilder(
                listOf(cargoBin, "ndk", "--target", rustTarget, "build", "--lib")
                    + cargoArgs
                    + listOf("--no-default-features", "--features", cargoFeatures)
            )
            builder.directory(File(absoluteRustPath))

            builder.environment()["ANDROID_NDK_HOME"] = ndkDir
            if (!isWindows) {
                builder.environment()["PATH"] = "$userHome/.cargo/bin:" + System.getenv("PATH")
            }

            val logFile = File(absoluteRustPath, "cargo_build.log")
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            builder.redirectError(ProcessBuilder.Redirect.appendTo(logFile))

            val process = builder.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException("cargo ndk build failed with exit code $exitCode. See cargo_build.log in CesiumRS for details.")
            }

            val soFile = File("$absoluteRustPath/target/$rustTarget/$cesiumProfile/libcesium_rs.so")
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
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}