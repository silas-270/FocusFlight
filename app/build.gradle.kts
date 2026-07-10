import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        val absoluteRustPath = "C:/Users/kamme/Desktop/CesiumRS"
        val targets = mapOf(
            "aarch64-linux-android" to "arm64-v8a",
            "x86_64-linux-android" to "x86_64"
        )

        targets.forEach { (rustTarget, androidAbi) ->
            println("Building Rust library for target: $rustTarget (ABI: $androidAbi)...")
            
            val builder = ProcessBuilder("cmd", "/c", "cargo ndk --target $rustTarget build --lib --release")
            builder.directory(File(absoluteRustPath))
            
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

// tasks.named("preBuild") {
//     dependsOn("cargoNdkBuild")
// }

dependencies {
    implementation("androidx.games:games-activity:3.0.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation(libs.gson)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}