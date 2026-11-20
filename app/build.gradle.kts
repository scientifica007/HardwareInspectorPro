plugins {
    id("com.android.application")
}

// Signing credentials are supplied only by GitHub Actions encrypted secrets.
// No keystore, private key, or password belongs in this public repository.
val stableKeystorePath = System.getenv("HIP_KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("HIP_KEYSTORE_PASSWORD")
val stableSigningConfigured = !stableKeystorePath.isNullOrBlank() &&
        !stableKeystorePassword.isNullOrBlank()

android {
    namespace = "dz.scientifica.hardwareinspectorpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "dz.scientifica.hardwareinspectorpro"
        minSdk = 26
        targetSdk = 33
        versionCode = 5
        versionName = "0.2.3"
    }

    signingConfigs {
        if (stableSigningConfigured) {
            create("stableRelease") {
                storeFile = file(stableKeystorePath!!)
                storePassword = stableKeystorePassword
                keyAlias = "hardware-inspector-prod"
                keyPassword = stableKeystorePassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (stableSigningConfigured) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
