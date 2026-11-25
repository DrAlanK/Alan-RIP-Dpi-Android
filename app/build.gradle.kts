import java.util.Properties
import com.android.build.api.variant.FilterConfiguration

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

base {
    archivesName.set("Alan-RIP-Dpi-v1.8.1-beta")
}

android {
    namespace = "com.alan.ripdpi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alan.ripdpi"
        minSdk = 24
        targetSdk = 35
        versionCode = 125
        versionName = "1.8.1-beta"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val lp = Properties()
            val lpFile = rootProject.file("local.properties")
            if (lpFile.exists()) lpFile.inputStream().use { lp.load(it) }
            val ksPath = lp.getProperty("release.storeFile") ?: System.getenv("ALAN_KEYSTORE_PATH") ?: ""
            if (ksPath.isNotBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = lp.getProperty("release.storePassword") ?: System.getenv("ALAN_KEYSTORE_PASS") ?: ""
                keyAlias = lp.getProperty("release.keyAlias") ?: System.getenv("ALAN_KEY_ALIAS") ?: "alan_release"
                keyPassword = lp.getProperty("release.keyPassword") ?: System.getenv("ALAN_KEY_PASS") ?: ""
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val relCfg = signingConfigs.findByName("release")
            signingConfig = if (relCfg?.storeFile != null) relCfg else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val abiVersionMultipliers = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86_64" to 3
)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier
            val mult = abiVersionMultipliers[abiName] ?: 4
            output.versionCode.set(mult * 10000 + 125)
        }
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
