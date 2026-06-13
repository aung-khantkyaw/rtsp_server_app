import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'EasyRTSP',
      theme: ThemeData.dark(),
      home: const RtspServerScreen(),
    );
  }
}

class RtspServerScreen extends StatefulWidget {
  const RtspServerScreen({super.key});

  @override
  State<RtspServerScreen> createState() => _RtspServerScreenState();
}

class _RtspServerScreenState extends State<RtspServerScreen> {
  static const platform = MethodChannel('com.example/rtsp_server');

  bool isStreaming = false;
  String rtspUrl = "URL will appear here";

  Future<void> _startServer() async {
    try {
      final String url = await platform.invokeMethod('startServer');
      setState(() {
        rtspUrl = url;
        isStreaming = true;
      });
    } on PlatformException catch (e) {
      setState(() {
        rtspUrl = "Failed to start: ${e.message}";
      });
    }
  }

  Future<void> _stopServer() async {
    try {
      await platform.invokeMethod('stopServer');
      setState(() {
        rtspUrl = "Server Stopped";
        isStreaming = false;
      });
    } on PlatformException catch (e) {
      print("Error stopping server: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Flutter RTSP Server"),
        centerTitle: true,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.cast_connected, size: 80, color: Colors.blueAccent),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(15),
                decoration: BoxDecoration(
                  color: Colors.black45,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  rtspUrl,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 40),
              ElevatedButton.icon(
                onPressed: isStreaming ? _stopServer : _startServer,
                icon: Icon(isStreaming ? Icons.stop : Icons.play_arrow),
                label: Text(isStreaming ? "Stop Server" : "Start Server"),
                style: ElevatedButton.styleFrom(
                  backgroundColor: isStreaming ? Colors.red : Colors.green,
                  padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
                  textStyle: const TextStyle(fontSize: 20),
                ),
              )
            ],
          ),
        ),
      ),
    );
  }
}