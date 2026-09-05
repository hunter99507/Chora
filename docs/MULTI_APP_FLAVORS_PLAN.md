# Multi-App Flavors Architecture Plan (Sonora, Lyra, Aria)

This document outlines the complete implementation blueprint to build and install **Sonora**, **Lyra**, and **Aria** alongside **Chora** on the same Android device simultaneously, without any conflict or need to manually switch music libraries.

---

## 1. Overview & Flavor Concept

| App Name | Dedicated Source | Primary Color / Theme | App ID Suffix | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Sonora** | **Local Storage** | Warm Amber & Copper Acoustic | `.sonora` | High-performance local device music player. |
| **Lyra** | **Navidrome / Subsonic** | Electric Cyan & Sapphire Harp | `.lyra` | Dedicated Navidrome cloud streaming client. |
| **Aria** | **Emby / Jellyfin** | Magenta & Obsidian Wave | `.aria` | Dedicated Emby & Jellyfin media server client. |
| **Chora** | **All / Switchable** | Classic Dynamic / User Pick | *(none)* | Original multi-source library client. |

---

## 2. Generated App Icons

High-resolution app icons have been created and saved to [`assets/flavor_icons/`](file:///home/matt/Documents/Scripts/Chora/assets/flavor_icons):

1. **Sonora Icon**: `assets/flavor_icons/sonora_icon.png`
   - *Design*: Warm peach-to-copper iOS-style gradient with a white beamed double music note and soft glass sheen.
2. **Lyra Icon**: `assets/flavor_icons/lyra_icon.png`
   - *Design*: Soft ice-blue-to-sapphire gradient with a white lyre/harp silhouette and strings.
3. **Aria Icon**: `assets/flavor_icons/aria_icon.png`
   - *Design*: Soft rose-to-deep plum (obsidian) gradient with a white infinity streaming-wave symbol.

---

## 3. Code Modifications (Ready to Merge)

### 3.1 `app/build.gradle.kts`

Add flavor dimensions, the 3 new product flavors, and unique `applicationIdSuffix`:

```kotlin
android {
    ...
    flavorDimensions += "edition"

    productFlavors {
        create("chora") {
            dimension = "edition"
            isDefault = true
        }
        create("sonora") {
            dimension = "edition"
            applicationIdSuffix = ".sonora"
            versionNameSuffix = "-sonora"
            manifestPlaceholders["appLabel"] = "Sonora"
            buildConfigField("String", "DEDICATED_SOURCE", "\"LOCAL\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Sonora\"")
        }
        create("lyra") {
            dimension = "edition"
            applicationIdSuffix = ".lyra"
            versionNameSuffix = "-lyra"
            manifestPlaceholders["appLabel"] = "Lyra"
            buildConfigField("String", "DEDICATED_SOURCE", "\"NAVIDROME\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Lyra\"")
        }
        create("aria") {
            dimension = "edition"
            applicationIdSuffix = ".aria"
            versionNameSuffix = "-aria"
            manifestPlaceholders["appLabel"] = "Aria"
            buildConfigField("String", "DEDICATED_SOURCE", "\"EMBY\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Aria\"")
        }
    }
}
```

---

### 3.2 `app/src/main/AndroidManifest.xml`

To prevent provider authority conflicts when installing all apps on the same phone:

```xml
<!-- In AndroidManifest.xml: replace hardcoded authorities with ${applicationId} -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/data_extraction_rules" />
</provider>

<service
    android:name=".player.ChoraMediaLibraryService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

---

### 3.3 Dynamic Home Page Name & Source Locking

#### String Resources per Flavor:
- `app/src/sonora/res/values/strings.xml`:
  ```xml
  <resources>
      <string name="app_name">Sonora</string>
      <string name="home_greeting">Sonora</string>
  </resources>
  ```
- `app/src/lyra/res/values/strings.xml`:
  ```xml
  <resources>
      <string name="app_name">Lyra</string>
      <string name="home_greeting">Lyra</string>
  </resources>
  ```
- `app/src/aria/res/values/strings.xml`:
  ```xml
  <resources>
      <string name="app_name">Aria</string>
      <string name="home_greeting">Aria</string>
  </resources>
  ```

#### In `HomeScreen.kt`:
Update the greeting header to display the localized app name:
```kotlin
Text(
    text = stringResource(R.string.app_name),
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground
)
```

#### In `MediaSourceManager.kt`:
Enforce the dedicated source at startup if configured:
```kotlin
fun initializeFlavorSource() {
    when (BuildConfig.FLAVOR) {
        "sonora" -> setSelectedSource(MediaSource.LOCAL)
        "lyra" -> setSelectedSource(MediaSource.NAVIDROME)
        "aria" -> setSelectedSource(MediaSource.EMBY)
        else -> { /* retain default user selection in Chora */ }
    }
}
```

---

## 4. Multi-App Build Script (`scripts/build/build_flavors.sh`)

The standalone script to compile and organize all 3 standalone APKs:

```bash
#!/usr/bin/env bash
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${PROJECT_ROOT}/build_output/flavors"

mkdir -p "${OUTPUT_DIR}"
cd "${PROJECT_ROOT}"

echo "=========================================="
echo " Building Sonora, Lyra, and Aria APKs"
echo "=========================================="

# 1. Sonora (Local)
echo "--> Building Sonora (Local)..."
./gradlew assembleSonoraDebug
cp app/build/outputs/apk/sonora/debug/app-sonora-debug.apk "${OUTPUT_DIR}/Sonora-Local-debug.apk"

# 2. Lyra (Navidrome)
echo "--> Building Lyra (Navidrome)..."
./gradlew assembleLyraDebug
cp app/build/outputs/apk/lyra/debug/app-lyra-debug.apk "${OUTPUT_DIR}/Lyra-Navidrome-debug.apk"

# 3. Aria (Emby)
echo "--> Building Aria (Emby)..."
./gradlew assembleAriaDebug
cp app/build/outputs/apk/aria/debug/app-aria-debug.apk "${OUTPUT_DIR}/Aria-Emby-debug.apk"

echo "=========================================="
echo " All 3 Flavors Built Successfully!"
echo " Location: ${OUTPUT_DIR}"
echo " 1. Sonora-Local-debug.apk"
echo " 2. Lyra-Navidrome-debug.apk"
echo " 3. Aria-Emby-debug.apk"
echo "=========================================="
```

---

## 5. How to Merge When Ready

When you are ready to activate this feature in the main codebase:
1. Open `app/build.gradle.kts` and add the `productFlavors` block from Section 3.1.
2. Run the icon asset generation script to generate density mipmaps into `app/src/sonora/res`, `app/src/lyra/res`, and `app/src/aria/res`.
3. Save `scripts/build/build_flavors.sh` and make executable (`chmod +x`).
4. Run `./scripts/build/build_flavors.sh` to produce all three independent APKs.
