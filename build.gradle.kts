// Корневой файл: только объявление плагинов, настройки — в app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 компилирует Kotlin сам; плагин объявлен, чтобы зафиксировать версию Kotlin из каталога
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
