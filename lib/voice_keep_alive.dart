
import 'voice_keep_alive_platform_interface.dart';

class VoiceKeepAlive {

  static Future<void> startService() {
    return VoiceKeepAlivePlatform.instance.startService();
  }

  static Future<void> stopService() {
    return VoiceKeepAlivePlatform.instance.stopService();
  }
}
