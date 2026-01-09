
import 'dart:io';

import 'voice_keep_alive_platform_interface.dart';

class VoiceKeepAlive {

  /// 开启服务
  static Future<void> startService({bool isAnchor=true,String title='',String content='',String roomParams=''}) {
    return VoiceKeepAlivePlatform.instance.startService(isAnchor: isAnchor,title: title,content: content,roomParams:roomParams);
  }

  /// 关闭服务
  static Future<void> stopService() {
    return VoiceKeepAlivePlatform.instance.stopService();
  }

  /// 告诉原生音频是否活跃
  static Future<void> setAudioActive(bool active) {
    return VoiceKeepAlivePlatform.instance.setAudioActive(active);
  }

  /// 最小化返回到桌面 only supports Android
  static Future<bool> moveAppToBackground() {
    if(Platform.isAndroid){
      return VoiceKeepAlivePlatform.instance.moveAppToBackground();
    }else{
      return Future.value(false);
    }
  }

  static Future<void> initKeepAliveHandler(Function(String roomParams)? callBack) {
    if(Platform.isAndroid){
      return VoiceKeepAlivePlatform.instance.initKeepAliveHandler(callBack);
    }
    return Future.value();
  }
}
