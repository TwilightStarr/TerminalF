import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Sürüm adı: -PversionName=2.1.0
val appVersionName = (project.findProperty("versionName") as String?) ?: "3.0.0"

// Sürüm numarası (versionCode): Android, yalnızca ÖNCEKİNDEN BÜYÜK numaralı APK'yı mevcut kurulumun
// üzerine günceller. Bu yüzden numara, "1970'ten beri geçen dakika" olarak üretilir: her derlemede
// kendiliğinden büyür, elle artırmak gerekmez ve eski numaralı hiçbir derleme bunu geçemez.
// İstenirse -PversionCode=NN ile elle verilebilir.
val appVersionCode = (project.findProperty("versionCode") as String?)?.toInt()
    ?: (System.currentTimeMillis() / 60_000L).toInt()

// İmza: güncellemenin çalışması için her sürüm AYNI anahtarla imzalanmalıdır.
// Anahtar depoda tutulmaz; CI'da GitHub Secrets'tan kök dizine key.properties olarak yazılır.
// key.properties yoksa (yerel deneme) debug anahtarı kullanılır; bu APK güncelleme için uygun değildir.
val keystoreProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "com.terminalf.app"
    compileSdk = 35

    defaultConfig {
        // DEĞİŞTİRMEYİN: güncelleme, kurulu uygulamayla aynı uygulama kimliği + aynı imza ister.
        applicationId = "com.example.terminal_flutter"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        compose = true
        aidl = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // Tam kontrol: root yoksa Shizuku (UserService) üzerinden sistem komutları.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
