# Уведомления о сторонних компонентах и лицензиях (Third-Party Notices)

Проект **VisionVoiceOffline** использует следующие библиотеки и компоненты с открытым исходным кодом:

---

## 1. sherpa-onnx

- **Разработчик:** Next-gen Kaldi project (Daniel Povey, Fangjun Kuang and contributors)
- **Лицензия:** Apache License 2.0
- **Репозиторий:** https://github.com/k2-fsa/sherpa-onnx
- **Назначение:** Автономный движок распознавания речи (ASR) на базе ONNX Runtime для мобильных и встраиваемых платформ.

---

## 2. ONNX Runtime

- **Разработчик:** Microsoft Corporation
- **Лицензия:** MIT License
- **Репозиторий:** https://github.com/microsoft/onnxruntime
- **Назначение:** Высокопроизводительный кроссплатформенный движок инференса нейросетевых моделей.

---

## 3. Модель распознавания русской речи (Zipformer RU INT8)

- **Архитектура:** Zipformer transducer (sherpa-onnx-zipformer-ru-2024-09-18)
- **Квантование:** INT8 (encoder, decoder, joiner)
- **Лицензия:** Apache License 2.0
- **Разработчик/Обучение:** Сообщество Next-gen Kaldi (k2-fsa)
- **Назначение:** Локальная акустическая и языковая модель русского языка.

---

## 4. Библиотеки AndroidX и Kotlin

- **AndroidX Core KTX, AppCompat, RecyclerView:**
  - **Разработчик:** The Android Open Source Project (Google LLC)
  - **Лицензия:** Apache License 2.0
- **Kotlin Standard Library & Coroutines:**
  - **Разработчик:** JetBrains s.r.o.
  - **Лицензия:** Apache License 2.0

---

## Лицензия собственного кода проекта VisionVoiceOffline

Исходный код проекта **VisionVoiceOffline** распространяется под условиями свободной лицензии **Apache License 2.0**.
Полный текст условий приведен в файле [LICENSE](LICENSE).

```text
Copyright 2026 VisionVoiceOffline Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
