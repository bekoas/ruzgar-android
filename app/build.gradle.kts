plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.beko.ruzgar"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.beko.ruzgar"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("surumKodu") as String?)?.toInt() ?: 1
        versionName = "1.0." + ((project.findProperty("surumKodu") as String?) ?: "1")
    }
    signingConfigs {
        create("beko") {
            storeFile = file(System.getenv("IMZA_DOSYA") ?: "imza.p12")
            storePassword = System.getenv("IMZA_SIFRE")
            keyAlias = "ruzgar"
            keyPassword = System.getenv("IMZA_SIFRE")
            storeType = "pkcs12"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("beko")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
