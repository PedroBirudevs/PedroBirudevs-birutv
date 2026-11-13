plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vc = (System.getenv("VERSION_CODE") ?: "1").toInt()
val keystoreFile = file("release.keystore")

android {
    namespace = "dev.pedrobirudevs.birutv"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pedrobirudevs.birutv"
        minSdk = 26
        targetSdk = 34
        versionCode = vc
        versionName = "0.2.$vc"
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "birutv"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sem a chave fixa o APK sai com a chave de debug (o login do Google não funciona com ela).
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
