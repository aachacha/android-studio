plugins {
  id("dumpAndroidTarget")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("com.android.lint")
}

kotlin {
  androidLibrary {
    withJava()
    withAndroidTestOnJvmBuilder {
        compilationName = "unitTest"
        defaultSourceSetName = "androidUnitTest"
    }.configure {
      isIncludeAndroidResources = true
    }

    withAndroidTestOnDeviceBuilder {
        compilationName = "instrumentedTest"
        defaultSourceSetName = "androidInstrumentedTest"
    }

    compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDeviceCompilation::class.java) {
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("androidMain") {
      dependencies {
        api(project(":androidLib"))
        implementation(project(":kmpSecondLib"))
        implementation(project(":kmpJvmOnly"))
      }
    }

    sourceSets.getByName("androidInstrumentedTest") {
      dependencies {
        implementation("androidx.test:runner:1.4.0-alpha06", {
            exclude(group="com.google.guava", module="listenablefuture")
        })
        implementation("androidx.test:core:1.4.0-alpha06", {
            exclude(group="com.google.guava", module="listenablefuture")
        })
        implementation("androidx.test.ext:junit:1.1.5", {
            exclude(group="com.google.guava", module="listenablefuture")
        })
      }
    }

    compilations.getByName("instrumentedTest") {
        kotlinOptions.languageVersion = "1.8"
    }

    dependencyVariantSelection {
      productFlavors.put("type", mutableListOf("typeone"))
      productFlavors.put("mode", mutableListOf("modetwo"))
    }

    aarMetadata.minAgpVersion = "7.2.0"
  }

   sourceSets.getByName("commonTest") {
     dependencies {
       implementation(kotlin("test"))
     }
   }

    sourceSets.getByName("commonMain") {
        dependencies {
            implementation("com.google.guava:guava:19.0", {
                exclude(group="com.google.guava", module="listenablefuture")
            })
        }
    }
}

androidComponents {
    finalizeDsl { extension ->
        extension.namespace = "com.example.kmpfirstlib"
        extension.compileSdk = property("latestCompileSdk") as Int
        extension.minSdk = 22
    }
    onVariant { variant ->
        if (variant.name == null || variant.name.isEmpty()) {
            throw IllegalArgumentException("must have variant name")
        }
    }
}
