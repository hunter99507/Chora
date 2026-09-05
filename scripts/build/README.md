# Chora Build & Deployment System

This folder contains the automated build, deployment, and Git automation tool for the Chora Android music player.

---

## Quick Start

You can run the script from anywhere:

### From the Chora Project Root:
```bash
./build.sh
```

### Or From This Folder:
```bash
./build.sh
```

---

## Menu Options Explained

When you launch `./build.sh`, you will see an interactive menu:

### **1) Build Release APK (assembleRelease + Deploy to Server)** — *Recommended for everyday builds*
* **What it does**:
  1. Compiles the optimized production release build (`./gradlew assembleRelease`).
  2. Shrinks and minifies code via R8/ProGuard to keep the app fast and small (~4.9 MB).
  3. Signs the APK with release keys.
  4. Automatically copies the finished APK to your network transfer folder:
     ```
     /media/smb-ubuntu/Server Downloads/transfer/Chora-release.apk
     ```
* **When to use**: Whenever you want to test or update the app on your Phone, Android TV, Tablet, or Car.

---

### **2) Build Debug APK (assembleDebug)**
* **What it does**:
  1. Compiles an un-minified debug version (`./gradlew assembleDebug`).
  2. Keeps developer debugging and verbose logging enabled.
  3. Saves the APK locally to `app/build/outputs/apk/debug/app-debug.apk`.
  4. Does **not** copy to the server transfer folder.
* **When to use**: When developing new features or testing quick changes without waiting for R8 release optimization.

---

### **3) Clean & Rebuild (clean + assembleRelease + Deploy)**
* **What it does**:
  1. Wipes all cached build files and previous binaries (`./gradlew clean`).
  2. Recompiles the entire project completely from scratch (`./gradlew assembleRelease`).
  3. Copies the fresh `Chora-release.apk` to your server transfer folder.
* **When to use**: If you ever encounter strange build errors, stale cached classes, or compiler glitches after editing many files.

---

### **4) Install to Device (assembleDebug + adb install -r)**
* **What it does**:
  1. Compiles the debug APK.
  2. Automatically installs or updates the app directly onto an Android device connected to your PC via USB or Wi-Fi using ADB (`adb install -r`).
* **When to use**: If your phone or Android TV is plugged into your PC with USB Debugging enabled. Skips manual file transfers.

---

### **5) Push to GitHub (git add . + commit + push)**
* **What it does**:
  1. Shows you all modified and untracked files.
  2. Prompts you for a commit message (or press Enter for a clean default).
  3. Runs `git add .` to stage everything.
  4. Commits your changes.
  5. Pushes directly to your GitHub repository (`git push origin <branch>`).
* **When to use**: Whenever you want to backup or sync your changes to GitHub without having to type git commands manually!

---

### **6) Build & Push Both (Release Build + Deploy + Push to GitHub)**
* **What it does**:
  1. Performs a complete **Release Build** (`assembleRelease`).
  2. Copies the updated APK to the server transfer folder.
  3. Stages, commits, and pushes your changes to GitHub in one seamless step!
* **When to use**: When you have completed a feature and want to both deploy the new APK and commit your code to GitHub at the same time.

---

### **7) Set Deploy Folder**
* **What it does**:
  1. Shows the currently active deployment folder (default: `/media/smb-ubuntu/Server Downloads/transfer`).
  2. Prompts you to enter a new target directory (or pass via command line).
  3. Automatically creates the directory if it doesn't exist yet (upon confirmation).
  4. Persists the setting in `.build_config` so all future release builds deploy to your chosen location automatically!
* **When to use**: Whenever you want to redirect APK deployments to a different local folder, shared drive, or staging directory.

---

### **8) Multi-App Flavors (Build Sonora, Lyra, Aria with library lock)**
* **What it does**:
  1. Opens an interactive wizard to build one, several, or all standalone music app editions (**Sonora**, **Lyra**, **Aria**, or **Chora**).
  2. Lets you choose what library source each edition is locked to:
     - **Default for flavor** (Sonora = Local, Lyra = Navidrome, Aria = Emby)
     - **Local Storage only**
     - **Navidrome / Subsonic only**
     - **Emby / Jellyfin only**
     - **All Sources** (unlocked / switchable)
  3. Lets you pick your build target:
     - **Release APK** (Signed release build + copies to server transfer folder)
     - **Debug APK** (Fast development build)
     - **Install to Device** (Build debug & install directly via ADB)
  4. Saves organized outputs in `build_output/flavors/` (e.g. `Aria-Local-release.apk`, `Lyra-Navidrome-release.apk`).
* **When to use**: Whenever you want to compile standalone single-source versions (e.g. Aria locked to Local, Lyra locked to Navidrome) for installation alongside Chora!

---

### **9) Exit**
* Safely cancels and exits the script without doing anything.

---

## Non-Interactive Command Shortcuts

If you don't want to use the menu, you can pass arguments directly:

| Command | Action |
|---|---|
| `./build.sh release` | Builds release APK & deploys to transfer folder |
| `./build.sh debug` | Builds debug APK |
| `./build.sh clean` | Wipes build cache & rebuilds release APK |
| `./build.sh install` | Builds debug APK and installs via ADB |
| `./build.sh push` | Prompts for message & pushes all changes to GitHub |
| `./build.sh push "My commit message"` | Pushes to GitHub with the specified commit message |
| `./build.sh all` | Builds release APK, deploys, and pushes to GitHub |
| `./build.sh config` | Interactively changes the default deployment directory |
| `./build.sh config "/path/to/dir"` | Sets the default deployment directory to the specified path |
| `./build.sh flavors` | Launches the Multi-App Flavors wizard directly |
| `./scripts/build/build_flavors.sh` | Shortcut to launch the Flavors wizard |

---

## File Locations & Logs

* **Release APK Output**: `app/build/outputs/apk/release/app-release.apk`
* **Default Transfer Path**: `/media/smb-ubuntu/Server Downloads/transfer/Chora-release.apk` (configurable via `./build.sh config` or `TRANSFER_DIR` env var)
* **Build Logs**: Stored with timestamps under `logs/` (e.g. `logs/build_release_20260903_194608.log`)
