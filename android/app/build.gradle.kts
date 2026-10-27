import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Assinatura do APK de venda. A MESMA chave sempre: sem ela, o cliente não consegue atualizar por cima.
val assinatura = Properties().apply {
    rootProject.file("chave/assinatura.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "bot.afiliados"
    compileSdk = 35

    defaultConfig {
        applicationId = "bot.afiliados"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.0.0"
        // Servidor central de licenças e atualizações (o do dono).
        buildConfigField("String", "CENTRAL_URL", "\"https://achadinhos-flqb.onrender.com\"")
        // Chave PÚBLICA das licenças (a privada fica só no servidor central).
        buildConfigField("String", "LICENCA_PUBLICA", "\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESh+Hclbnt2Q4XYFTR0Gopmal4DygeKc8Ip+l9KXW0cwANrtQTR+il0KwdjLS6O9NAePo2IHVO+nhN2OrrtwElQ==\"")
        // Data desta versão: o plano precisa cobrir até aqui (scripts/publicar-versao.sh passa -PdataVersao).
        // Impressão digital (SHA-256) do certificado que assina o APK de venda. APK reassinado (clone) não abre.
        buildConfigField("String", "ASSINATURA", "\"7942EBCF12BE4A42BF394EA1A1E39530AECE0CE97C86F99179A16EF22CF95254\"")
        buildConfigField("long", "DATA_VERSAO", "${(project.findProperty("dataVersao") as String?) ?: "0"}L")
    }

    signingConfigs {
        if (assinatura.isNotEmpty()) create("venda") {
            storeFile = rootProject.file(assinatura.getProperty("storeFile"))
            storePassword = assinatura.getProperty("storePassword")
            keyAlias = assinatura.getProperty("keyAlias")
            keyPassword = assinatura.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Versão de desenvolvimento: abre sem licença.
            buildConfigField("boolean", "EXIGIR_LICENCA", "false")
        }
        release {
            buildConfigField("boolean", "EXIGIR_LICENCA", "true")
            signingConfigs.findByName("venda")?.let { signingConfig = it }
            // Código embaralhado (R8): dificulta copiar e modificar o app.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // Esquemas do banco vão no APK de teste (debug) para o teste de migração ler.
    sourceSets { getByName("debug").assets.srcDir("$projectDir/schemas") }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            System.getProperty("prints")?.let { pasta -> it.systemProperty("prints", pasta) }
        }
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.biometric)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
