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
        versionCode = 3
        versionName = "0.2.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
