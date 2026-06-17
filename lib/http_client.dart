import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

class RTSPServerAPI {
  static const platform = MethodChannel('com.example/rtsp_server');
  static String _baseUrl = '';
  static bool _isInitialized = false;

  static Future<String> _getDeviceIp() async {
    try {
      final String ip = await platform.invokeMethod('getIpAddress');
      return ip;
    } on PlatformException catch (e) {
      print("Failed to get IP: ${e.message}");
      return '127.0.0.1';
    }
  }

  static Future<void> initialize() async {
    if (_isInitialized) return;

    final ip = await _getDeviceIp();
    _baseUrl = 'http://$ip:8080';
    _isInitialized = true;
    print('RTSPServerAPI initialized with baseUrl: $_baseUrl');
  }

  static String get baseUrl {
    if (!_isInitialized) {
      print('Warning: RTSPServerAPI not initialized. Call initialize() first.');
      return 'http://127.0.0.1:8080';
    }
    return _baseUrl;
  }

  static Future<Map<String, dynamic>> getMotionState() async {
    try {
      final response = await http.get(
        Uri.parse('$baseUrl/api.cgi?cmd=GetMdState'),
      ).timeout(Duration(seconds: 2)); // ၂ စက္ကန့် timeout

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as List;
        if (data.isNotEmpty) {
          return data[0]['value'] as Map<String, dynamic>;
        }
      }
    } catch (e) {
      print('Error getting motion state: $e');
    }
    return {'state': 0};
  }

  static Future<Map<String, dynamic>> getAIState() async {
    try {
      final response = await http.get(
        Uri.parse('$baseUrl/api.cgi?cmd=GetAiState'),
      ).timeout(Duration(seconds: 2));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as List;
        if (data.isNotEmpty) {
          return data[0]['value'] as Map<String, dynamic>;
        }
      }
    } catch (e) {
      print('Error getting AI state: $e');
    }
    return {};
  }
}