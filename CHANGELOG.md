# Releases / Релизы

## 4.1-blenny-arm32-r17-color (versionCode 420; based on r17)

### English

- Xiaomi Watch 5 (`blenny`) build for the 32-bit `armeabi-v7a` userspace.
- Includes the ARM32 trampoline and bootstrap handoff fixes from r16/r17.
- This fork's UI-only change: the **Clear** and **Cancel** button text in the clear-log confirmation dialog uses the existing green app accent.
- DFRoot's own run log is saved by the app. Shizuku is required for the exploit race and is also used to capture system logcat.

### Русский

- Сборка для 32-битного userspace Xiaomi Watch 5 (`blenny`), ABI `armeabi-v7a`.
- Содержит исправления ARM32 trampoline и передачи управления bootstrap из r16/r17.
- Единственное UI-изменение этого форка: текст кнопок **Clear** и **Cancel** в диалоге очистки логов окрашен в штатный зелёный акцент приложения.
- Лог запусков DFRoot сохраняется приложением. Shizuku обязателен для корректной работы гонки эксплойта и также используется для записи системного logcat.
