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

@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
}

val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // Default: "apt-android-7"

android {
    namespace = "com.termux"
    compileSdk = 33
    
    defaultConfig {
        minSdk = 24
        
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"" + packageVariant + "\"") // Used by TermuxApplication class

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.acc_ide"
        manifestPlaceholders["TERMUX_APP_NAME"] = "AccIDE"

        // 禁用NDK构建，因为缺少termux-bootstrap-zip.S文件
        // externalNativeBuild {
        //     ndkBuild {
        //         cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
        //     }
        // }
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    // 禁用NDK构建
    // externalNativeBuild {
    //     ndkBuild {
    //         path = file("src/main/cpp/Android.mk")
    //     }
    // }

    lint.disable += "ProtectedPermissions"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging.jniLibs.useLegacyPackaging = true
}

dependencies {
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.drawerlayout:drawerlayout:1.1.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.guava:guava:31.1-android")

    api(project(":termux:view"))
    api(project(":termux:shared"))
    implementation(project(":core:common"))
    
    testImplementation("junit:junit:4.13.2")
}