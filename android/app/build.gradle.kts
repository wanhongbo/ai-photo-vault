import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// RevenueCat Android Public API Key：写在 android/local.properties（勿提交），与 CI 环境变量注入二选一。
private val localPropertiesFile = rootProject.file("local.properties")
private val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
private val revenueCatAndroidApiKey: String =
    localProperties.getProperty("revenuecat.apiKey.android")?.trim().orEmpty()

private fun escapeForBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private val revenueCatBuildConfigField: String =
    "\"${escapeForBuildConfigString(revenueCatAndroidApiKey)}\""

android {
    namespace = "com.xpx.vault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xpx.vault"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storePassword = keystoreProperties.getProperty("storePassword")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile")!!)
            }
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("boolean", "DEV_TOOLS", "true")
            buildConfigField("String", "REVENUECAT_API_KEY", revenueCatBuildConfigField)
        }
        create("prod") {
            dimension = "env"
            buildConfigField("boolean", "DEV_TOOLS", "false")
            buildConfigField("String", "REVENUECAT_API_KEY", revenueCatBuildConfigField)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 避免未配置 keystore.properties 时打出可误传到 Play 的未签名/调试签名 AAB
tasks.matching { task ->
    val n = task.name
    n.startsWith("bundle") && n.endsWith("Release")
}.configureEach {
    doFirst {
        if (!rootProject.file("keystore.properties").exists()) {
            throw GradleException(
                "缺少 ${rootProject.projectDir}/keystore.properties，无法为 Release 打 AAB。\n" +
                    "请复制 keystore.properties.example 为 keystore.properties 并填写上传密钥信息；\n" +
                    "上传到 Google Play 请使用 Play 应用签名（上传密钥仅用于向商店提交）。",
            )
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
    implementation(project(":core:ai-mlkit"))
    implementation(project(":feature:ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.revenuecat.purchases)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
