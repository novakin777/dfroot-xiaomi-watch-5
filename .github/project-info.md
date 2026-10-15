<p align="center">
  <a href="../README.md">Русский</a>
</p>

<p align="center">
  <img src="icon.png" width="96" />
</p>

# DFRoot — Xiaomi Watch 5 ARM32 build

This fork is based on DFRoot and adapted for the Xiaomi Watch 5 (`blenny`). The tested app build targets the watch's 32-bit `armeabi-v7a` Android userspace; the watch kernel is 64-bit. The included kernel module is a separate component and must match the device kernel/KMI.

> **Status:** r17 is the base. This repository build changes only the text color of the **Clear** and **Cancel** buttons in the clear-log confirmation dialog to match the app's green accent. Adapting DFRoot to other devices is up to their owners and requires device-specific work; this build targets the Watch 5.

## Download

- [Releases](https://github.com/novakin777/dfroot-xiaomi-watch-5/releases)
- [Latest release](https://github.com/novakin777/dfroot-xiaomi-watch-5/releases/latest)

Install the APK from a release. The release notes identify the version and changes.

## Device profile

- Device: Xiaomi Watch 5 (`blenny`)
- Android: 16, API 36
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

See [CHANGELOG.md](../CHANGELOG.md) for this fork's release notes.

## License

This project is distributed under the [GNU General Public License v3.0](../LICENSE).

## AI assistance

AI tools were used to assist with code changes and documentation.
