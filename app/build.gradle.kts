import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.englishcoach.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.englishcoach.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.0"
    }

    // Ключ релиза и пароли лежат вне репозитория; путь можно переопределить свойством englishSigning
    val signingFile = file(providers.gradleProperty("englishSigning").getOrElse("F:/AndroidDev/keys/english-coach-signing.properties"))
    signingConfigs {
        if (signingFile.exists()) {
            val p = Properties().apply { signingFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Без файла с ключом (другой компьютер) релиз подписывается отладочным ключом — только для проверки
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Служебные файлы лицензий из зависимостей Anthropic SDK (Jackson, OkHttp и др.) совпадают по имени
        resources.excludes += listOf("/META-INF/DEPENDENCIES", "/META-INF/LICENSE*", "/META-INF/NOTICE*", "/META-INF/INDEX.LIST")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
}
