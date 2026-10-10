plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes these from the git tag (v1.2.3 -> 1.2.3) and the run number.
val storeVersionName = (findProperty("versionName") as String?) ?: "0.1.0"
val storeVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "com.amandhakar.appstore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amandhakar.appstore"
        minSdk = 26
        targetSdk = 35
        versionCode = storeVersionCode
        versionName = storeVersionName

        // Where the store reads its list of apps from.
        buildConfigField(
            "String",
            "CATALOG_URL",
            "\"https://raw.githubusercontent.com/aman-dhakar-191/private-app-store/main/catalog.json\"",
        )
    }

    signingConfigs {
        // Set by CI from repository secrets. Every release must be signed with the
        // same key, otherwise Android refuses to install it as an update.
        val keystore = System.getenv("KEYSTORE_FILE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
