import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';
import 'http_client.dart';

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
  int motionState = 0;
  Map<String, int> aiStates = {
    'people': 0,
    'vehicle': 0,
    'dog_cat': 0,
    'face': 0,
  };
  Timer? _timer;
  bool _isApiInitialized = false;

  // 💡 RTSP URL ထဲကနေ IP ကို ဆွဲထုတ်ပြီး HTTP API URL အဖြစ် ပြောင်းပေးမယ့် Getter လုပ်ဆောင်ချက်
  String get httpUrl {
    if (!isStreaming || rtspUrl.startsWith("Failed") || rtspUrl == "URL will appear here") {
      return "URL will appear here";
    }

    final RegExp regExp = RegExp(r'rtsp://([^:/]+)');
    final match = regExp.firstMatch(rtspUrl);
    if (match != null) {
      final String? ip = match.group(1);
      return 'http://$ip:8080'; // Native ဘက်က သတ်မှတ်ထားတဲ့ HTTP Port 8080 သို့ ချိတ်ဆက်ခြင်း
    }
    return "URL will appear here";
  }

  @override
  void initState() {
    super.initState();
    _initializeApiAndStartPolling();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _initializeApiAndStartPolling() async {
    await RTSPServerAPI.initialize();
    setState(() {
      _isApiInitialized = true;
    });
    _startPolling();
  }

  void _startPolling() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) async {
      if (isStreaming && _isApiInitialized) {
        await _updateStates();
      }
    });
  }

  Future<void> _updateStates() async {
    try {
      final motion = await RTSPServerAPI.getMotionState();
      final ai = await RTSPServerAPI.getAIState();

      setState(() {
        motionState = motion['state'] ?? 0;
        if (ai.isNotEmpty) {
          aiStates['people'] = ai['people']?['alarm_state'] ?? 0;
          aiStates['vehicle'] = ai['vehicle']?['alarm_state'] ?? 0;
          aiStates['dog_cat'] = ai['dog_cat']?['alarm_state'] ?? 0;
          aiStates['face'] = ai['face']?['alarm_state'] ?? 0;
        }
      });
    } catch (e) {
      print('Error updating states: $e');
    }
  }

  Future<void> startServer(bool isFront) async {
    try {
      final String url = await platform.invokeMethod('startServer', {'isFront': isFront});
      setState(() {
        rtspUrl = url;
        isStreaming = true;
      });

      await RTSPServerAPI.initialize();

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
        motionState = 0;
        aiStates = {
          'people': 0,
          'vehicle': 0,
          'dog_cat': 0,
          'face': 0,
        };
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

  // 💡 URL များကို Copy ကူးရလွယ်ကူအောင် ပြုလုပ်ပေးမယ့် Function
  void _copyToClipboard(String text, String title) {
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$title copied to clipboard!'),
        duration: const Duration(seconds: 2),
        backgroundColor: Colors.blueAccent,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Easy RTSP Server"),
        centerTitle: true,
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                  Icons.sensors,
                  size: 80,
                  color: isStreaming ? Colors.greenAccent : Colors.blueAccent
              ),
              const SizedBox(height: 25),

              if (!isStreaming) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: Colors.black38,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.grey[800]!),
                  ),
                  child: const Text(
                    "Tap 'Start Server' to host media broadcast",
                    style: TextStyle(fontSize: 16, color: Colors.grey),
                    textAlign: TextAlign.center,
                  ),
                ),
              ] else ...[
                // RTSP URL Card
                _buildUrlDisplayCard(
                  title: "RTSP STREAM ENDPOINT (VLC/NVR)",
                  urlText: rtspUrl,
                  icon: Icons.video_camera_back,
                  onCopy: () => _copyToClipboard(rtspUrl, "RTSP URL"),
                ),
                const SizedBox(height: 15),
                // HTTP API URL Card
                _buildUrlDisplayCard(
                  title: "HTTP STATE API SERVER",
                  urlText: httpUrl,
                  icon: Icons.http,
                  onCopy: () => _copyToClipboard(httpUrl, "HTTP API Base URL"),
                  subtitle: "Endpoints:\n• /api.cgi?cmd=GetMdState\n• /api.cgi?cmd=GetAiState",
                ),
              ],

              const SizedBox(height: 25),

              // Detection Status
              if (isStreaming) ...[
                Container(
                  padding: const EdgeInsets.all(15),
                  decoration: BoxDecoration(
                    color: Colors.grey[900],
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.grey[800]!),
                  ),
                  child: Column(
                    children: [
                      const Text(
                        "LIVE MONITORING ALARMS",
                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.grey),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceAround,
                        children: [
                          _buildStatusChip('Motion', motionState == 1),
                          _buildStatusChip('People', aiStates['people'] == 1),
                          _buildStatusChip('Vehicle', aiStates['vehicle'] == 1),
                          _buildStatusChip('Dog/Cat', aiStates['dog_cat'] == 1),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 25),
              ],

              ElevatedButton.icon(
                onPressed: isStreaming ? _stopServer : showCameraSelectionDialog,
                icon: Icon(isStreaming ? Icons.stop : Icons.play_arrow),
                label: Text(isStreaming ? "Stop Server" : "Start Server"),
                style: ElevatedButton.styleFrom(
                  backgroundColor: isStreaming ? Colors.redAccent : Colors.green,
                  padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 16),
                  textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // 💡 URL များကို သေသပ်လှပစွာ ထုပ်ပိုးပြသပေးမယ့် Component Widget
  Widget _buildUrlDisplayCard({
    required String title,
    required String urlText,
    required IconData icon,
    required VoidCallback onCopy,
    String? subtitle,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.blueAccent.withOpacity(0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.blueAccent),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(icon, size: 22, color: Colors.grey[400]),
              const SizedBox(width: 10),
              Expanded(
                child: SelectableText(
                  urlText,
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, fontFamily: 'monospace'),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.copy, size: 20, color: Colors.grey),
                onPressed: onCopy,
                tooltip: "Copy Link",
              )
            ],
          ),
          if (subtitle != null) ...[
            const Divider(height: 15, color: Colors.white10),
            Text(
              subtitle,
              style: TextStyle(fontSize: 12, color: Colors.grey[500], height: 1.4),
            ),
          ]
        ],
      ),
    );
  }

  Widget _buildStatusChip(String label, bool isActive) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: isActive ? Colors.red[900]!.withOpacity(0.7) : Colors.grey[800],
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isActive ? Colors.redAccent : Colors.grey[600]!,
          width: 1,
        ),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: isActive ? Colors.white : Colors.grey[400],
          fontSize: 12,
          fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
        ),
      ),
    );
  }
}