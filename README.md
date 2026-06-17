# RTSP Server App (Real-time Streaming Protocol with Edge AI)

![Version](https://img.shields.io/badge/version-2.0.0-green.svg)

A powerful, high-performance Flutter application that transforms your Android device into a live RTSP streaming server integrated with **OpenCV Motion Detection** and **YOLOv8 Edge AI**. By leveraging native Android Camera2 APIs, the robust RootEncoder library, and on-device machine learning, this app broadcasts real-time video feeds while exposing intelligent monitoring APIs over the local network.

## What's New in v2.0.0 (AI & Motion Intelligence)

- **OpenCV Motion Detection:** Implements a native computer vision pipeline utilizing frame differencing and thresholding to detect movement instantly with minimal CPU overhead.
- **On-Device YOLOv8 Object Detection:** Runs an optimized quantized TensorFlow Lite model (`yolov8n_int8.tflite`) to detect and classify objects in real-time (People, Vehicles, Pets).
- **Zero-Conflict Frame Pipeline:** Completely decoupled camera session infrastructure. Frames are extracted directly from the live GL rendering pipeline (`takePhoto`), eliminating camera resource conflicts and preventing skipped frames.
- **Built-in HTTP API Server (NanoHTTPD):** Hosts a lightweight web server on port `8080`. External clients, NVRs, or Home Automation systems can poll `/api.cgi?cmd=GetMdState` or `GetAiState` to fetch immediate security alerts.

## Core Features

- **Real-time Streaming:** Broadcast high-quality video and audio directly from your device's camera and microphone over RTSP (`port 8554`).
- **Dual Camera Support:** Prompts a selection dialog upon starting the server, allowing you to choose between the **Front** or **Back** camera seamlessly.
- **Dynamic Background Processing:** AI inference and motion calculations run seamlessly on dedicated background handler threads (`HandlerThread`) to keep the UI perfectly smooth.
- **Auto IP Detection:** Automatically filters and displays the device's local IPv4 address for easy client connections.
- **Dynamic Permissions:** Handles Android runtime permissions (Camera, Microphone, and Internet) gracefully.

## Tech Stack & Libraries

- **Frontend UI Framework:** [Flutter](https://flutter.dev/)
- **Native Logic Layer:** Kotlin (Android Architecture)
- **Streaming Core:** [RootEncoder (PedroSG94)](https://github.com/pedroSG94/RootEncoder) for RTSP server pipeline.
- **Computer Vision:** [OpenCV Android SDK](https://opencv.org/) for digital image processing (Grayscale conversion, Absolute differencing).
- **Machine Learning Engine:** [TensorFlow Lite Task Vision](https://www.tensorflow.org/lite) for embedded object detection.
- **Embedded Web Server:** [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) for serving HTTP/CGI API state queries.

## Prerequisites

Before running this project, ensure you have the following:

- Flutter SDK installed and configured.
- Android Studio / Android SDK with NDK support (required for OpenCV native components).
- An Android device (Physical device highly recommended). Minimum Android Version: **Android 5.0 (Lollipop) - API Level 21**.
- Both the streaming device and the viewing/API clients must be connected to the **same local Wi-Fi network**.

### Essential Model Assets
You must place your YOLOv8 model and COCO labels inside the Android assets folder:
`android/app/src/main/assets/`
- `yolov8n_int8.tflite` (Quantized YOLOv8 object detection model)
- `coco_labels.txt` (Class names text file aligned with your model output)

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

*(To build a highly optimized release APK for production, use: `flutter build apk --release --split-per-abi --obfuscate --split-debug-info=build/debug-info`)*

## How to Use & Integrate

1. Open the application on your Android device and grant the required **Camera** and **Microphone** permissions.
2. Tap the **Start Server** button and select either the **Front Camera** or **Back Camera**.
3. The app will initiate the RTSP broadcast and launch the AI pipeline alongside the HTTP service.
4. **Watch Video:** Input the generated RTSP URL (e.g., `rtsp://192.168.1.5:8554`) into [VLC Media Player](https://www.videolan.org/) or any NVR software.
5. **Fetch Security Alarms via HTTP API:**
    * **Motion Status Query:** `http://192.168.1.5:8080/api.cgi?cmd=GetMdState`
    * *Returns:* `[{"cmd":"GetMdState","code":0,"value":{"state":1}}]` (1 = Motion Detected, 0 = Quiet)
    * **AI Object Status Query:** `http://192.168.1.5:8080/api.cgi?cmd=GetAiState`
    * *Returns live classification states:* People, Vehicles, and Pets alarms.

## Architecture Note

The application delegates intensive computer vision and server hosting tasks entirely to the native Android ecosystem via Flutter's `MethodChannel`.

* **Flutter UI:** Orchestrates user interactions, manages view states, and requests current states using asynchronous invocations.
* **MainActivity.kt:** Handles method calls, initiates the camera stream, and schedules the non-blocking rendering pipeline snapshots loop at regular intervals (~10 FPS).
* **DetectionManager.kt:** The mathematical core. It converts incoming Bitmaps directly to OpenCV Mat structures for lightning-fast structural analysis, monitors cooldown limits, downsamples targets, and pushes buffers into the TFLite interpreter for neural network analysis.
* **HttpServer.kt:** Maps the real-time state flags calculated by `DetectionManager` directly onto clean JSON payloads over standard NanoHTTPD socket connections.

```