/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.github.zly2006.zhihu.buildlogic.appVersionCode
import com.github.zly2006.zhihu.buildlogic.gitShortHash
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("zhihu.android.application")
    id("zhihu.module.graph")
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

aboutLibraries {
    collect {
        configPath = rootProject.file("aboutlibraries")
    }
}

android {
    namespace = "com.github.zly2006.zhihu"

    defaultConfig {
        applicationId = "com.eltavine.zhihuhyperion"
        versionCode = appVersionCode()
        versionName = property("app.versionName").toString()

        testInstrumentationRunner = "com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner"
    }

    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            buildConfigField("boolean", "IS_LITE", "false")
        }
        create("lite") {
            dimension = "version"
            isDefault = true
            buildConfigField("boolean", "IS_LITE", "true")
            applicationIdSuffix = ".lite"
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "zh")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
    // 四项齐全才用发布密钥；缺任何一项时 release 用 debug 密钥签名，这样的产物不能当作正式发布。
    val hasReleaseSigning =
        listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
            .all { !it.orNull.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("ciRelease") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        val gitHash = gitShortHash()
        debug {
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            manifestPlaceholders["zhihuBuildType"] = "debug"
            manifestPlaceholders["zhihuGitHash"] = gitHash
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            manifestPlaceholders["zhihuBuildType"] = "release"
            manifestPlaceholders["zhihuGitHash"] = gitHash
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "ciRelease" else "debug")
        }
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/**/LICENSE",
                    "META-INF/**/LICENSE.txt",
                    "META-INF/proguard/*",
                    "**.kotlin_module",
                    "kotlin-tooling-metadata.json",
                    "DebugProbesKt.bin",
                )
        }
    }

    androidComponents {
        beforeVariants(selector().all()) { variantBuilder ->
            if (variantBuilder.buildType == "release") {
                // The full flavor bundles HanLP and ONNX Runtime, which R8 cannot shrink safely.
                val minify = variantBuilder.flavorName == "lite"
                variantBuilder.isMinifyEnabled = minify
                variantBuilder.shrinkResources = minify
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.freeCompilerArgs.add("-Xdebug")
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

dependencies {
    implementation(projects.shared)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.coil.compose)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigationevent)
    implementation(libs.jetbrains.navigationevent.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.jetbrains.navigation.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(projects.core.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    // Compose Multiplatform's Android artifacts resolve to AndroidX Compose; the BOM keeps them on one release.
    implementation(platform(libs.androidx.compose.bom))
    "fullImplementation"(projects.sentenceEmbeddings)
    "fullImplementation"(libs.hanlp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(projects.markdownRenderer)
    androidTestImplementation(projects.feature.update)
}
