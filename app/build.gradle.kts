plugins { id("com.android.application") }
android {
 namespace = "dev.siva.toolbox"
 compileSdk = 37
 defaultConfig { applicationId = "dev.siva.toolbox"; minSdk = 34; targetSdk = 37; versionCode = 4; versionName = "0.3.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
