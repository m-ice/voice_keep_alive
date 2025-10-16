
import 'dart:io';

import 'voice_keep_alive_platform_interface.dart';

class VoiceKeepAlive {

  /// 开启服务
  static Future<void> startService({bool isAnchor=true,String title='',String content=''}) {
    return VoiceKeepAlivePlatform.instance.startService(isAnchor: isAnchor,title: title,content: content);
  }

  /// 关闭服务
  static Future<void> stopService() {
    return VoiceKeepAlivePlatform.instance.stopService();
  }

  /// 最小化返回到桌面 only supports Android
  static Future<bool> moveAppToBackground() {
    if(Platform.isAndroid){
      return VoiceKeepAlivePlatform.instance.moveAppToBackground();
    }else{
      return Future.value(false);
    }
  }
}
