plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.dainon.skep"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dainon.skep"   // google-services.json 매칭 (skep-30bc5)
        minSdk = 30       // Wear OS 3.0+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // skep-v2 백엔드 dev 주소 (배포 시 변경). 워치 직접 호출(옵션)용.
        // 기본 안전알림 경로는 폰(Wearable Data Layer) 중계.
        buildConfigField("String", "SERVER_URL", "\"https://skep.on1.kr\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Wear OS
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Health Services (심박수, SpO₂)
    implementation("androidx.health:health-services-client:1.1.0-alpha03")
    implementation("com.google.guava:guava:32.1.3-android")

    // 네트워크
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 코루틴
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Firebase FCM
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // 유닛테스트 (FallDetector 합성 트레이스)
    testImplementation("junit:junit:4.13.2")
}
