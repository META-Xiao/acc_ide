/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id ("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.acc_ide.termux.shared"
    compileSdk = 33
    
    defaultConfig {
        minSdk = 24
        targetSdk = 33
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
    
    buildFeatures {
        buildConfig = true
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.window:window:1.0.0-alpha09")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.guava:guava:31.1-android")
    
    // Markdown and text processing
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:recycler:4.6.2")
    
    // Apache Commons IO
    implementation("commons-io:commons-io:2.11.0")
    
    // Hidden API bypass
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    
    // Termux AM library - commented out as it's not available in public repositories
    // implementation("com.termux:termux-am-library:v2.0.0")
    
    // Project dependencies
    implementation(project(":core:common"))
    api(project(":termux:emulator"))
    api(project(":termux:view"))
}
