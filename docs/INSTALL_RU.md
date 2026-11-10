# Руководство по установке VisionVoiceOffline

## Требования

1. **Телевизор:** Huawei Vision S (KANT-359, 55"/65"/75") с прошивкой HarmonyOS 2.0 / Android 10 (API 29).
2. **Пульт:** Оригинальный Bluetooth-пульт Huawei HDRC-BV1 с микрофоном.
3. **Компьютер:** Windows 10 или Windows 11, подключённый к той же локальной сети (Wi-Fi или Ethernet), что и телевизор.
4. **Утилита ADB:** Из официального набора [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).

---

## Быстрая автоматическая установка (PowerShell)

1. Скачайте архив релиза `VisionVoiceOffline-v0.1.0-beta.zip` и распакуйте его на компьютере.
2. Откройте терминал PowerShell в папке `Installer`.
3. Запустите скрипт установки, указав локальный IP-адрес телевизора:
   ```powershell
   .\Install-VisionVoice.ps1 -DeviceIp 192.168.1.100
   ```
4. Если на экране телевизора появится запрос «Разрешить отладку по USB?», выберите **«Всегда разрешать с этого компьютера»** и нажмите **ОК**.
5. Установщик выполнит все шаги автоматически:
   - Проверит целостность APK по контрольной сумме SHA-256.
   - Установит приложение `VisionVoiceOffline`.
   - Предоставит разрешение на использование микрофона (`RECORD_AUDIO`).
   - Настроит разрешение отображения голосовой панели поверх всех окон (`SYSTEM_ALERT_WINDOW`).
   - Сохранит резервную копию и включит службу системной навигации (`VisionVoiceAccessibilityService`) без сброса других активных служб.
   - Запустит фоновый сервис ассистента.

---

## Ручная установка через стандартный ADB

Если вы предпочитаете выполнять команды вручную:

1. **Подключение к телевизору:**
   ```bash
   adb connect 192.168.1.100:5555
   ```
2. **Установка приложения:**
   ```bash
   adb -s 192.168.1.100:5555 install -r VisionVoiceOffline-v0.1.0-beta.apk
   ```
3. **Выдача разрешения на микрофон:**
   ```bash
   adb -s 192.168.1.100:5555 shell pm grant com.huawei.vision.voiceoffline android.permission.RECORD_AUDIO
   ```
4. **Включение оверлея:**
   ```bash
   adb -s 192.168.1.100:5555 shell appops set com.huawei.vision.voiceoffline SYSTEM_ALERT_WINDOW allow
   ```
5. **Включение службы навигации (Accessibility):**
   ```bash
   # Получите текущий список:
   adb -s 192.168.1.100:5555 shell settings get secure enabled_accessibility_services

   # Добавьте компонент VisionVoice к списку (через двоеточие):
   adb -s 192.168.1.100:5555 shell settings put secure enabled_accessibility_services com.huawei.vision.voiceoffline/com.huawei.vision.voiceoffline.VisionVoiceAccessibilityService
   adb -s 192.168.1.100:5555 shell settings put secure accessibility_enabled 1
   ```
6. **Запуск службы ассистента:**
   ```bash
   adb -s 192.168.1.100:5555 shell am start-foreground-service -n com.huawei.vision.voiceoffline/.VoiceOverlayService
   ```

---

## Проверка работы

Зажмите кнопку с иконкой микрофона на пульте **HDRC-BV1**, произнесите:
> **«Громкость двадцать»** или **«Найди на Ютубе Варламова»**

и отпустите кнопку. На экране появится полупрозрачный голосовой оверлей и выполнится запрошенное действие.
