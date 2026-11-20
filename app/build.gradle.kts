plugins {
    id("com.android.application")
}

android {
    namespace = "dz.scientifica.hardwareinspectorpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "dz.scientifica.hardwareinspectorpro"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
