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


  Future<void> startService() {
    throw UnimplementedError('startService() has not been implemented.');
  }

  Future<void> stopService() {
    throw UnimplementedError('stopService() has not been implemented.');
  }
}
