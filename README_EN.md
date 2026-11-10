# VisionVoiceOffline

Autonomous offline voice assistant for **Huawei Vision S** smart TVs (KANT-359 series, HarmonyOS 2.0 / Android 10 API 29).

Operates completely on-device without third-party cloud services (Google, Yandex, etc.).

---

## Highlights

- **100% Offline Speech Recognition:** Powered by Sherpa-ONNX and a quantized Russian Zipformer INT8 neural network model.
- **Hardware Push-to-Talk (PTT):** Integrates with the official Huawei HDRC-BV1 Bluetooth remote; audio capture is active strictly while the microphone button is pressed.
- **Global Voice Overlay:** Non-focusable transparent overlay window (`SYSTEM_ALERT_WINDOW`) that never interrupts video playback or captures D-pad focus.
- **Universal Voice Search:** Deep link and exported intent integration with Android TV media apps (YouTube, LazyMedia Deluxe) with contextual dialogue and query refinement memory.
- **Safe System Navigation:** Executes HOME, BACK, and RECENTS global actions via an isolated AccessibilityService without reading screen content or passwords.
- **Zero Root / Zero System Modifications:** Fully compliant with standard Android architecture.

---

## Quick Start

1. Ensure the TV and PC are on the same local network.
2. Enable Network Debugging in the TV Developer Options.
3. Run the automated installer:
   ```powershell
   .\Installer\Install-VisionVoice.ps1 -DeviceIp 192.168.1.xxx
   ```

For detailed instructions in Russian, see [INSTALL_RU.md](docs/INSTALL_RU.md) and [ADB_SETUP_RU.md](docs/ADB_SETUP_RU.md).

---

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.

Third-party component licenses and neural network model attributions are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

