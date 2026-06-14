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

  Future<void> startServer(bool isFront) async {
    try {
      final String url = await platform.invokeMethod('startServer', {'isFront': isFront});
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

  Future<void> showCameraSelectionDialog() async {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Select Camera'),
          content: const Text('Which camera would you like to use for streaming?'),
          actions: <Widget>[
            TextButton(
              child: const Text('Back Camera'),
              onPressed: () {
                Navigator.of(context).pop();
                startServer(false);
              },
            ),
            TextButton(
              child: const Text('Front Camera'),
              onPressed: () {
                Navigator.of(context).pop();
                startServer(true);
              },
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Easy RTSP"),
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
                onPressed: isStreaming ? _stopServer : showCameraSelectionDialog,
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