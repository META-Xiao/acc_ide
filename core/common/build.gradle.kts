plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.acc_ide.core.common"
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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.blankj:utilcodex:1.31.1")
    
    // SLF4J for logging
    api("org.slf4j:slf4j-api:2.0.12")
    
    // EventBus
    api("org.greenrobot:eventbus:3.3.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // FlashBar - using a simpler approach with snackbar
    implementation("com.google.android.material:material:1.9.0")
}