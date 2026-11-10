# Руководство по управлению сертификатами и подписью релизов

В данном документе описывается порядок генерации постоянного release-ключа и безопасной настройки подписи APK для публичных версий проекта **VisionVoiceOffline**.

---

## 1. Важное предупреждение о совместимости подписей

> [!WARNING]
> **Несовместимость сертификатов при обновлении (INSTALL_FAILED_UPDATE_INCOMPATIBLE):**
> Все сборки этапов разработки M1–M5 и первая бета-версия `v0.1.0-beta` собирались со стандартным Android Debug ключом (SHA-256: `ce14f552745c0ebc325061286c51debb35e96e31f2a90e197cb87d15aeff9464`).
>
> Если вы переподпишете APK новым production-ключом:
> 1. Android Package Manager **запретит обновление** поверх существующей установки (`adb install -r`).
> 2. Для установки новой сборки потребуется предварительно выполнить `adb uninstall com.huawei.vision.voiceoffline`.
> 3. При этом будут сброшены локальные настройки приложения и потребуется повторная выдача разрешений `RECORD_AUDIO` и `SYSTEM_ALERT_WINDOW`.
>
> **Правило безопасности:** Не удаляйте рабочую установку на тестовом ТВ и не меняйте используемый сертификат в публичных пакетах без явного согласования владельца проекта.

---

## 2. Генерация постоянного хранилища ключей (Release Keystore)

Для создания постоянного ключа выполните команду `keytool` (входит в состав любого JDK):

```powershell
keytool -genkey -v -keystore release.keystore -alias visionvoice -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=VisionVoice, OU=VisionVoiceOffline, O=Community, C=RU"
```

При генерации укажите надежный пароль хранилища и пароль ключа.

> [!IMPORTANT]
> **Безопасность секрета:**
> - Файл `release.keystore` никогда не должен загружаться в систему контроля версий Git.
> - Шаблоны `*.keystore`, `*.jks` и `keystore.properties` уже добавлены в `.gitignore`.
> - Создайте защищенную резервную копию хранилища `release.keystore`. Утеря ключа приведет к невозможности обновления опубликованных сборок у пользователей.

---

## 3. Настройка Gradle для автоматической подписи

1. В корне проекта `VisionVoiceOffline` создайте локальный файл `keystore.properties` (на основе шаблона `keystore.properties.example`):

```properties
storeFile=release.keystore
storePassword=ВАШ_ПАРОЛЬ_ХРАНИЛИЩА
keyAlias=visionvoice
keyPassword=ВАШ_ПАРОЛЬ_КЛЮЧА
```

2. При выполнении сборки `./gradlew assembleRelease`:
   - Если файл `keystore.properties` присутствует в корне проекта, Gradle автоматически использует указанный release-ключ.
   - Если файл `keystore.properties` отсутствует, Gradle автоматически переключается на debug-подпись для сохранения обратной совместимости сборки.

---

## 4. Проверка подписи собранного APK

После сборки проверьте сертификат утилитой `apksigner`:

```powershell
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```
