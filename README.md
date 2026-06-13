# RTSP Server App (Real-time Streaming Protocol)

A powerful Flutter application that transforms your Android device into a live RTSP streaming server. By leveraging native Android Camera2 APIs and the robust RootEncoder library, this app broadcasts real-time video and audio feeds across your local network.

## Features

- **Real-time Streaming:** Broadcast high-quality video and audio directly from your device's camera and microphone.
- **Native Performance:** Built using Flutter's `MethodChannel` to communicate seamlessly with native Kotlin code for optimized media processing.
- **Camera2 API Integration:** Utilizes Android's modern Camera2 API for better control over hardware components.
- **Auto IP Detection:** Automatically detects and displays the device's IPv4 address for easy client connection.
- **Dynamic Permissions:** Handles Android runtime permissions (Camera & Microphone) gracefully.

## Tech Stack & Libraries

- **Framework:** [Flutter](https://flutter.dev/)
- **Native Language:** Kotlin
- **Core Library:** [RootEncoder (PedroSG94)](https://github.com/pedroSG94/RootEncoder) for RTSP server implementation.

## Prerequisites

Before running this project, ensure you have the following:

- Flutter SDK installed and configured.
- An Android device (Physical device recommended for camera testing).
- Minimum Android Version: **Android 5.0 (Lollipop) - API Level 21**.
- Both the streaming device and the viewing client must be on the **same local Wi-Fi network**.

## Getting Started

1. **Clone the repository**

```bash
git clone <your-repository-url>
cd rtsp_server_app
```

2. **Install dependencies**

```bash
flutter pub get
```

3. **Build and Run**

```bash
flutter run
```

_(To build a release APK for 64-bit devices, use: `flutter build apk --release --split-per-abi --obfuscate --split-debug-info=build/debug-info`)_

## How to Use

1. Open the application on your Android device.
2. Grant the required **Camera** and **Microphone** permissions when prompted.
3. Tap the **Start Server** button.
4. The app will generate an RTSP URL (e.g., `rtsp://192.168.1.5:8554/live.sdp`).
5. Open a media player that supports RTSP (like [VLC Media Player](https://www.videolan.org/)) on another device connected to the same network.
6. Go to `Media` > `Open Network Stream` and enter the provided RTSP URL to view the live feed.

## Architecture Note

This project utilizes Flutter as the UI layer while delegating heavy multimedia encoding and server hosting to the Native Android side via `MethodChannel`.

- **Flutter Side:** Manages UI, state, and triggers native functions.
- **Native Side (`MainActivity.kt`):** Implements `ConnectChecker`, configures audio/video bitrates, handles camera lifecycle, and manages the RTSP server port (`8554`).

---
