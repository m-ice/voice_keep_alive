
import 'voice_keep_alive_platform_interface.dart';

class VoiceKeepAlive {

  static Future<void> startService({bool isAnchor=true}) {
    return VoiceKeepAlivePlatform.instance.startService(isAnchor: isAnchor);
  }

  static Future<void> stopService() {
    return VoiceKeepAlivePlatform.instance.stopService();
  }
}
