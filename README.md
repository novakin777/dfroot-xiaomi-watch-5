<p align="center">
  <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  <img src=".github/icon.png" width="96" />
</p>

# DFRoot — Xiaomi Watch 5 ARM32 build

This fork is based on DFRoot and adapted for the Xiaomi Watch 5 (`blenny`). The tested app build targets the watch's 32-bit `armeabi-v7a` Android userspace; the watch kernel is 64-bit. The included kernel module is a separate component and must match the device kernel/KMI.

> **Status:** The current release is based on r17 and targets the Xiaomi Watch 5. Adapting DFRoot to other devices is up to their owners and requires device-specific work.

## Download

- [Releases](https://github.com/novakin777/dfroot-xiaomi-watch-5/releases)
- [Latest release](https://github.com/novakin777/dfroot-xiaomi-watch-5/releases/latest)

Install the APK from a release. The release notes identify the version and changes.

## Device profile

- Device: Xiaomi Watch 5 (`blenny`)
- Android: 16, API 36
- DFRoot r17 confirmed working firmware: Xiaomi OS 3.0.3.70 (`OS3.0.3.70.WO61WMIXM`), build `xiaomi/blenny/blenny:16/BP2A.250805.014/OS3.0.3.70.WO61WMIXM:user/release-keys`.
- App/native userspace ABI: `armeabi-v7a` (32-bit)
- Kernel: `6.6.92-android15-8-ga352c6faabe2-ab14461313-4k`; KMI: `android15-6.6`
- Kernel and APK architectures are different: an APK build does not build or validate the kernel module.

## Usage

1. Install this fork's APK from [Releases](https://github.com/novakin777/dfroot-xiaomi-watch-5/releases).
2. Install and start Shizuku, then grant DFRoot Shizuku permission. Shizuku is required for the exploit race to work correctly, not just for log capture. This build also uses Shizuku to capture system `logcat`; the exploit will not start if the service is stopped or permission is missing.
3. Install a compatible KernelSU manager with `libksud.so` from the [Xiaomi Watch 5 ARM32 repository](https://github.com/novakin777/kernelsu-arm32_xiaomi_watch_5), then select it in DFRoot.
4. Review the device-specific risks and recovery options before starting the exploit. Keep a copy of diagnostic logs if a run fails.

DFRoot uses a kernel vulnerability and can crash or reboot a device. A successful app launch or an on-screen status alone is not proof that root is available to other apps; verify root independently.

## Credits

- [@diabl0w](https://github.com/diabl0w) — the only author attribution present in the application source (`MainActivity.java`).
- [novakin777](https://github.com/novakin777) — maintainer of this Xiaomi Watch 5 fork.

## Root access to the watch

[WearBridgeADB](https://github.com/novakin777/WearBridgeADB) can run commands on the watch with root privileges after KernelSU grants root to the WearBridge app on the watch. WearBridge does not obtain root by itself.

See [CHANGELOG.md](CHANGELOG.md) for this fork's release notes.

## License

Different parts of this repository may have different license terms:

- DFRoot fork code and the kernel module source are distributed under the [GNU General Public License v3](LICENSE). GPL-3.0 governs copying and distribution of these parts.
- [`app/src/main/jni/include.inc`](app/src/main/jni/include.inc) contains code by Odzhan under the [BSD 3-Clause License](app/src/main/jni/include.inc). Redistribution must retain its copyright notice, license conditions, and disclaimer; using the author's name to endorse or promote derived products requires prior written permission.
- The Gradle Wrapper scripts [`gradlew`](gradlew) and [`gradlew.bat`](gradlew.bat) contain material under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), as stated in their headers.
- Third-party libraries and other included materials retain their own licenses and notices. The repository's root license does not replace those terms.

Before redistributing a modified build, check the licenses of all included components and preserve their applicable notices.

## AI assistance

AI tools were used to assist with code changes and documentation.
