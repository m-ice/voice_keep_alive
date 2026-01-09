import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'voice_keep_alive_method_channel.dart';

abstract class VoiceKeepAlivePlatform extends PlatformInterface {
  /// Constructs a VoiceKeepAlivePlatform.
  VoiceKeepAlivePlatform() : super(token: _token);

  static final Object _token = Object();

  static VoiceKeepAlivePlatform _instance = MethodChannelVoiceKeepAlive();

  /// The default instance of [VoiceKeepAlivePlatform] to use.
  ///
  /// Defaults to [MethodChannelVoiceKeepAlive].
  static VoiceKeepAlivePlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [VoiceKeepAlivePlatform] when
  /// they register themselves.
  static set instance(VoiceKeepAlivePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }


  Future<void> startService({bool isAnchor=true,String title='',String content='',String roomParams=''}) {
    throw UnimplementedError('startService() has not been implemented.');
  }

  Future<void> stopService() {
    throw UnimplementedError('stopService() has not been implemented.');
  }

  Future<void> setAudioActive(bool active) {
    throw UnimplementedError('setAudioActive() has not been implemented.');
  }

  Future<bool> moveAppToBackground() {
    throw UnimplementedError('moveAppToBackground() has not been implemented.');
  }

  Future<void> initKeepAliveHandler(Function(String roomParams)? callBack) {
    throw UnimplementedError('initKeepAliveHandler() has not been implemented.');
  }
}
