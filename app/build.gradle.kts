  plugins {
      id("com.android.application")
      id("org.jetbrains.kotlin.android")
  }
  val admobAppId = (project.findProperty("admobAppId") as String?)?.takeIf { it.isNotBlank() }
      ?: "ca-app-pub-3940256099942544~3347511713"
  val idDoApp = (project.findProperty("appId") as String?)?.takeIf { it.isNotBlank() }
      ?: "com.exemplo.limpador"

  android {
      namespace = "com.exemplo.limpador"
      compileSdk = 34
      defaultConfig {
          applicationId = idDoApp
          manifestPlaceholders["admobAppId"] = admobAppId
          minSdk = 29
          targetSdk = 34
          versionCode = 1
          versionName = "1.0"
      }
      compileOptions {
          sourceCompatibility = JavaVersion.VERSION_17
          targetCompatibility = JavaVersion.VERSION_17
      }
      kotlinOptions { jvmTarget = "17" }
  }
  dependencies {
      implementation("androidx.core:core-ktx:1.13.1")
      implementation("androidx.activity:activity-ktx:1.9.0")
      implementation("androidx.media3:media3-common:1.4.1")
      implementation("androidx.media3:media3-transformer:1.4.1")
      implementation("androidx.media3:media3-effect:1.4.1")
      implementation("com.google.guava:guava:33.0.0-android")
      implementation("com.google.android.gms:play-services-ads:22.6.0")
  }
  FIM_ARQUIVO
  mkdir -p app/src/main
  cat > app/src/main/AndroidManifest.xml <<'FIM_ARQUIVO'
  <?xml version="1.0" encoding="utf-8"?>
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">

      <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
      <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
      <uses-permission android:name="android.permission.WAKE_LOCK" />
      <uses-permission android:name="android.permission.INTERNET" />
      <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

      <application
          android:icon="@mipmap/ic_launcher"
          android:roundIcon="@mipmap/ic_launcher_round"
          android:label="@string/app_name"
          android:theme="@android:style/Theme.Material.NoActionBar">

          <meta-data
              android:name="com.google.android.gms.ads.APPLICATION_ID"
              android:value="${admobAppId}" />

          <activity
              android:name=".MainActivity"
              android:exported="true"
              android:launchMode="singleTop"
              android:windowSoftInputMode="adjustResize"
              android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize">
              <intent-filter>
                  <action android:name="android.intent.action.MAIN" />
                  <category android:name="android.intent.category.LAUNCHER" />
              </intent-filter>
              <intent-filter>
                  <action android:name="android.intent.action.SEND" />
                  <category android:name="android.intent.category.DEFAULT" />
                  <data android:mimeType="video/*" />
              </intent-filter>
              <intent-filter>
                  <action android:name="android.intent.action.SEND_MULTIPLE" />
                  <category android:name="android.intent.category.DEFAULT" />
                  <data android:mimeType="video/*" />
              </intent-filter>
          </activity>

          <service
              android:name=".ProcessamentoService"
              android:exported="false"
              android:foregroundServiceType="dataSync" />
      </application>
  </manifest>
  FIM_ARQUIVO
  # ... (outros arquivos conforme necessidade)
