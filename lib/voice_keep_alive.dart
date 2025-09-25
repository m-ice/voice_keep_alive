
import 'voice_keep_alive_platform_interface.dart';

class VoiceKeepAlive {
  static Future<void> startService({bool isAnchor=true,String title='',String content=''}) {
    return VoiceKeepAlivePlatform.instance.startService(isAnchor: isAnchor,title: title,content: content);
  }

  static Future<void> stopService() {
    return VoiceKeepAlivePlatform.instance.stopService();
  }
}
