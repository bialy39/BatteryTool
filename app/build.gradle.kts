import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val readAndUnderstoodLicense = true

plugins {
    id("com.android.application")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "io.github.domi04151309.batterytool"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.domi04151309.batterytool"
        minSdk = 23
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 120
        versionName = "1.2.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    detekt {
        config.setFrom(file("detekt-config.yml"))
        buildUponDefaultConfig = true
        basePath = rootProject.projectDir.absolutePath
    }
    lint {
        disable += "MissingTranslation"
    }
    project.tasks.named("preBuild") { dependsOn("license") }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.register("license") {
    doFirst {
        val data =
            file("./src/main/res/xml/pref_about.xml")
                .readText()
                .contains("app:key=\"license\"")
        if (!data) {
            throw Exception(
                "Please note that removing the license from the about page is not allowed if you " +
                    "plan to publish your modified version of this app. " +
                    "Please read the project's LICENSE.",
            )
        }
        if (!(
                android.defaultConfig.applicationId?.contains("domi04151309") == true ||
                    readAndUnderstoodLicense
            )
        ) {
            throw Exception(
                "Please make sure you have read and understood the LICENSE!",
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.media2:media2-session:1.3.0")
}
