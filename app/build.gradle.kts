plugins { id("com.android.application") }
android {
 namespace = "dev.siva.toolbox"
 compileSdk = 37
 defaultConfig { applicationId = "dev.siva.toolbox"; minSdk = 34; targetSdk = 37; versionCode = 3; versionName = "0.2.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
