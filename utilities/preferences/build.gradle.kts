plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.acc_ide.utilities.preferences"
    compileSdk = 33
    
    defaultConfig {
        minSdk = 24
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
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}