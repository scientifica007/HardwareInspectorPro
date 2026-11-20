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
        versionCode = 4
        versionName = "0.2.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
