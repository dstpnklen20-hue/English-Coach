# Правила R8 для release-сборки.
# Свои правила поставляют: Room, kotlinx.serialization, OkHttp и SDK Anthropic (anthropic-java-core.pro — Jackson).

# Наши @Serializable-классы (материалы курса, синхронизация, ответы ИИ) разбираются kotlinx.serialization
-keepclassmembers @kotlinx.serialization.Serializable class com.englishcoach.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.englishcoach.app.**$$serializer { *; }

# SDK Anthropic: его собственных правил мало — R8 вырезал фильтры Jackson (JsonInclude с JsonMissing),
# и сборка запроса падала с JsonMappingException «JsonMissing cannot be serialized». Сохраняем SDK целиком.
-keep class com.anthropic.** { *; }

# Необязательные зависимости SDK/OkHttp, которых нет на Android
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.beans.**
# Генератор JSON-схем из SDK (victools) ссылается на API, которого нет на Android; приложение его не вызывает
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
