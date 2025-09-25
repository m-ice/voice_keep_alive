import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'voice_keep_alive_platform_interface.dart';

/// An implementation of [VoiceKeepAlivePlatform] that uses method channels.
class MethodChannelVoiceKeepAlive extends VoiceKeepAlivePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('voice_keep_alive');

  @override
  Future<void> startService({bool isAnchor=true,String title='',String content=''}) async {

    return await methodChannel.invokeMethod<dynamic>('startService'
    ,{
          "mode": isAnchor ? 1 : 0, // 1 = 主播, 0 = 观众
          "title": title,
          "content": content
        }
    );
  }

  @override
  Future<void> stopService() async {
    return await methodChannel.invokeMethod<dynamic>('stopService');
  }
}
