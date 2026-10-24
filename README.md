# FlashDock for Android

FlashDock is a native Android firmware-preparation utility with guarded root-only boot/recovery writes and explicit no-root system-power controls. The GitHub Actions workflow builds a debug APK and source archive on every push to `main`/`master`; version tags also create a GitHub Release.

## What it does

- Reads a user-selected file locally and calculates SHA-256.
- For classic `ANDROID!` boot images, offers a guarded `boot` or separate `recovery` partition write **only when root (`su`) is granted**. The writer checks the target, compares partition/image sizes, backs up the full partition, and reads back the written bytes.
- Stages a selected `system.img`, other `.img`, ROM `.zip`, or OEM package through Android's document picker. Staging is not flashing.
- Provides no-root Restart/Shutdown *menu* controls using an optional accessibility service that must be explicitly enabled by the device owner. Android opens its own power dialog; the user chooses the action.
- Attempts a root-based reboot to recovery; without root it shows OPPO's hardware-key path.
- Includes `tools/fastboot-flash-boot.sh` for a separate Linux/macOS computer running Android platform-tools. It targets only `boot`, requires a detected Fastboot device, checks the classic boot image magic, and asks for typed confirmation. It does not make a Fastboot interface appear on a phone that lacks one.

## Critical limits

### UBL is not root

An unlocked bootloader allows a compatible bootloader/fastboot interface to accept certain images. It does **not** give an ordinary APK permission to write raw partitions from inside the running Android OS. This app cannot self-flash arbitrary `.img` files on a non-rooted phone merely because UBL is enabled. Use the included computer helper only if the device genuinely exposes compatible Fastboot, or use a device-specific recovery/OEM workflow.

### Power controls without root

A normal third-party Android app cannot silently reboot or shut down the system. FlashDock can invoke the system power dialog after the owner enables **FlashDock Power Menu** in Android Settings → Accessibility. The owner must then choose Restart or Power off in the Android UI. Recovery is a separate boot mode; without root or a connected ADB host, use the hardware-key procedure.

### OPPO `.ofp` / `.ozip` / stock `.zip`

- `.ofp` and `.ozip` are OEM-specific packages. FlashDock only stages them and calculates their SHA-256; it does not decrypt, unpack, authenticate, or flash them. Use the matching supported OPPO service/update tool for the exact model and package. Some service workflows require authorization.
- A `.zip` is not necessarily a recovery-installable ROM. It must be made for the exact model and supported by the installed recovery. FlashDock stages the file; it does not claim that copying it installed it.
- Full-system images need an exact partition map and a matching bootloader/fastbootd or OEM service path. FlashDock intentionally does not `dd` arbitrary files to guessed partitions.

### OPPO CPH2185 (A15) note

Do not assume this model exposes standard Fastboot just because the bootloader may be unlocked. Verify it from a connected host with `fastboot devices` and check the reported product before writing anything. OPPO's recovery instructions for supported models say to power off and hold **Power + Volume Down** until the OPPO logo appears. This enters recovery; it does not itself flash firmware.

## Build locally

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

```sh
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Open the project root in Android Studio instead if preferred.

## Build with GitHub, `git`, and `gh`

After extracting this project to `~/flashdock`, run:

```sh
cd ~/flashdock
git init -b main
git add .
git commit -m "Initial FlashDock Android source"
gh auth login
gh repo create flashdock --public --source=. --remote=origin --push
```

Use `--private` instead of `--public` to make a private repository. If the repository already exists on GitHub, add the remote and push instead:

```sh
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/flashdock.git
git push -u origin main
```

Every push triggers **Actions → Build FlashDock Android APK**. Open the latest successful workflow run and download the `FlashDock-Android-build` artifact; it contains `FlashDock-debug.apk` and `FlashDock-source.zip`. To publish a downloadable GitHub Release, push a version tag:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The release workflow attaches both files. The APK is a debug build; it is suitable for testing/sideloading, not a Play Store release.

## Computer-side boot image workflow

The separate helper script is intentionally limited to a classic `boot.img` and the `boot` partition. It requires a supported device already in Fastboot mode. Do not use it for `.ofp`, `.ozip`, recovery, vendor, vbmeta, or system images.

```sh
chmod +x tools/fastboot-flash-boot.sh
./tools/fastboot-flash-boot.sh /path/to/boot.img
```

A helper script cannot bypass a locked bootloader, OEM authentication, or missing Fastboot support. For other image types, use the exact device's documented flashing workflow.

## Project layout

- `app/src/main/java/com/aiwolfie/flashdock/MainActivity.java` — UI, local hashing, root-only image writer orchestration, staging, power/recovery flows.
- `app/src/main/java/com/aiwolfie/flashdock/PowerMenuAccessibilityService.java` — optional service that requests Android's system power dialog; it does not read screen content.
- `app/src/main/assets/flash_raw_image.sh` — conservative root-only boot/recovery writer.
- `.github/workflows/android-apk.yml` — builds the APK and source ZIP, and creates releases on `v*` tags.
- `tools/fastboot-flash-boot.sh` — optional host-computer Fastboot boot-image workflow.
