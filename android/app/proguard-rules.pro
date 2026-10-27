# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class bot.afiliados.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class bot.afiliados.**$$serializer { *; }

# Proteção contra cópia: nomes embaralhados e tudo num pacote só.
-repackageclasses 'a'
-allowaccessmodification
-renamesourcefileattribute ''
-keepattributes SourceFile,LineNumberTable
# Room, WorkManager e Compose trazem as próprias regras. Enums lidos pelo nome no banco:
-keepclassmembers enum bot.afiliados.** { *; }
# Remove logs do APK de venda.
-assumenosideeffects class android.util.Log { public static *** d(...); public static *** v(...); public static *** i(...); }
# WorkManager guarda o nome da classe da tarefa: o nome não pode mudar entre versões.
-keep class bot.afiliados.work.** extends androidx.work.ListenableWorker { *; }
