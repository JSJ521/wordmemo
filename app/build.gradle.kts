plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${System.getProperty("user.home")}/android-release-key.jks"
val keyPass = System.getenv("KEYPASS") ?: ""

android {
    namespace = "com.wordmemo.app"
    compileSdk = 34

    signingConfigs {
        create("wordmemoRelease") {
            storeFile = file(keystorePath)
            storePassword = keyPass
            keyAlias = "myapp"
            keyPassword = keyPass
        }
    }

    defaultConfig {
        applicationId = "com.wordmemo.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 100000
        versionName = "10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("wordmemoRelease")
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Network (AI only)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Tesseract OCR (离线, 无需 Google Play Services, 兼容国产手机)
    implementation("com.rmtheis:tess-two:9.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Encryption
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Media3 - ExoPlayer (M1 影子跟读)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.0")
    implementation("androidx.media3:media3-extractor:1.2.0")

    // yt-dlp Android wrapper (M1 B站视频下载)
    val youtubedlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")

    // ffmpeg Android wrapper (M1 视频处理)
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")

    // Vosk 离线语音识别 (M1 视频→SRT字幕生成)
    implementation(files("libs/vosk-android-0.3.45.aar"))
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Core KTX
    implementation("androidx.core:core-ktx:1.12.0")

    // EPUB 解析
    implementation("org.jsoup:jsoup:1.17.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.11.1")
}

// 绕过 Windows 文件锁：每运行用唯一目录，不清理旧结果
tasks.withType<Test> {
    useJUnit()
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    // UUID目录使每次运行都是新目录，无需删除旧output.bin
    binaryResultsDirectory.set(file("${layout.buildDirectory.get()}/binary-results/${name}-${System.currentTimeMillis()}"))
    outputs.doNotCacheIf("win-lock-workaround") { true }
}
