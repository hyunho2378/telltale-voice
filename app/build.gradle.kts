import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.telltalevoice.app"
    compileSdk = 35

    // CarPropertyManager(기어, 시동, 주차 브레이크)를 쓰기 위한 안드로이드 차량 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.telltalevoice.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "VEHICLE_SDK_VERSION", "\"${libs.versions.pleosVehicle.get()}\"")
    }

    // release 키는 저장소 밖(../keystore/keystore.properties)에 두고 여기서 읽는다. 키 파일과 비밀번호는 커밋하지 않는다.
    // 다른 위치를 쓰려면 -PreleaseKeystoreProperties=<경로>. 파일이 없으면 release 는 서명하지 않은 채로 빌드된다.
    val releaseKeyFile = rootProject.file(
        (project.findProperty("releaseKeystoreProperties") as String?) ?: "../keystore/keystore.properties"
    )
    val releaseKey = Properties().apply { if (releaseKeyFile.exists()) releaseKeyFile.reader(Charsets.UTF_8).use { load(it) } }

    signingConfigs {
        if (releaseKeyFile.exists()) {
            create("release") {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.kotlin.reflect)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
}
