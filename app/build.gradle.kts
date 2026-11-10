import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.huawei.vision.voiceoffline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.huawei.vision.voiceoffline"
        minSdk = 26
        targetSdk = 29
        versionCode = 100
        versionName = "0.1.0-beta"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    val keystoreConfigFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystoreConfigFile.exists()) {
            val props = Properties()
            FileInputStream(keystoreConfigFile).use { stream ->
                props.load(stream)
            }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    androidResources {
        noCompress += listOf("onnx", "model")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
