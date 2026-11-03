plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionCodeProp = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val versionPrefix = project.findProperty("cloudlyVersionPrefix") as String? ?: "0.1"

// Release signing comes from environment variables (set by the release workflow from GitHub Secrets).
val keystorePath: String? = System.getenv("CLOUDLY_KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "io.github.tobijeh.cloudly"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tobijeh.cloudly"
        minSdk = 29
        targetSdk = 35
        versionCode = versionCodeProp
        versionName = "$versionPrefix.$versionCodeProp"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("CLOUDLY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CLOUDLY_KEY_ALIAS")
                keyPassword = System.getenv("CLOUDLY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
