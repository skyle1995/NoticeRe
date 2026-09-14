import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ———— 发布签名凭据外置 ————
// 优先读仓库根目录 signing.properties(不入 git),缺失时回退环境变量 KS_PATH/KS_STORE_PASSWORD/KS_KEY_PASSWORD。
// 两者都没有:打警告,release 产出未签名包(构建不失败)。口令不再写进本文件。
val signingPropsFile = rootProject.file("signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) {
        signingPropsFile.inputStream().use { load(it) }
    }
}
val ksPath = signingProps.getProperty("KS_PATH")?.takeIf { it.isNotBlank() }
    ?: System.getenv("KS_PATH")?.takeIf { it.isNotBlank() }
    ?: ""
val ksStorePwd = signingProps.getProperty("KS_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: System.getenv("KS_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: ""
val ksKeyPwd = signingProps.getProperty("KS_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: System.getenv("KS_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: ""
val hasReleaseSign = ksPath.isNotBlank() && ksStorePwd.isNotBlank() && ksKeyPwd.isNotBlank()
if (!hasReleaseSign) {
    logger.warn("签名凭据缺失(signing.properties / 环境变量 KS_* 均未配置) → release 将产出未签名 APK")
}

android {
    namespace = "com.noticere.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noticere.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("localDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (hasReleaseSign) {
                storeFile = file(ksPath)
                storePassword = ksStorePwd
                keyAlias = "ifunx"
                keyPassword = ksKeyPwd
                // 发布包同时启用 v1(JAR)/v2/v3 三种签名方案,兼容更老的校验工具
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("localDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSign) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
