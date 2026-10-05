plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val lingOpenCl = (findProperty("ling.opencl") as String?)?.toBoolean() ?: true
val lingKleidiAi = (findProperty("ling.kleidiai") as String?)?.toBoolean() ?: false

android {
    namespace = "io.github.ssebanom.ling.engine"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // S25 Ultra 전용
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_STL=c++_shared",
                    "-DLING_OPENCL=${if (lingOpenCl) "ON" else "OFF"}",
                    "-DLING_KLEIDIAI=${if (lingKleidiAi) "ON" else "OFF"}",
                )
                targets += listOf("ling_jni")
            }
        }
    }

    buildTypes {
        // 디버그 빌드도 네이티브는 최적화(추론 속도 확인용)
        debug {
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    sourceSets {
        getByName("main") {
            // tools/build_hexagon_backend.sh 결과물(libggml-hexagon.so, libggml-htp-v*.so)이 있으면 포함
            jniLibs.srcDirs("src/main/hexagonLibs")
        }
    }

    packaging {
        jniLibs {
            // 링크용으로만 빌드한 OpenCL ICD 로더는 넣지 않는다(런타임은 /vendor 의 libOpenCL.so)
            excludes += "**/libOpenCL.so"
            // 백엔드 동적 로딩이 nativeLibraryDir 를 스캔하므로 .so 를 APK에서 추출해야 한다
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
