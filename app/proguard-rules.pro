# Правила R8 для release-сборки.
# Большинство библиотек (Media3, Room, Hilt, Coil, kotlinx.serialization) поставляют свои consumer-правила.

# Маршруты навигации сериализуются kotlinx.serialization
-keepclassmembers @kotlinx.serialization.Serializable class com.localplayer.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
