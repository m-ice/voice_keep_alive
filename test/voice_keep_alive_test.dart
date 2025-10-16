import 'package:flutter_test/flutter_test.dart';
import 'package:voice_keep_alive/voice_keep_alive.dart';
import 'package:voice_keep_alive/voice_keep_alive_platform_interface.dart';
import 'package:voice_keep_alive/voice_keep_alive_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockVoiceKeepAlivePlatform
    with MockPlatformInterfaceMixin
    implements VoiceKeepAlivePlatform {


  @override
  Future<void> startService({bool isAnchor = true, String title = '', String content = ''}) {
    // TODO: implement startService
    throw UnimplementedError();
  }

  @override
  Future<void> stopService() {
    // TODO: implement stopService
    throw UnimplementedError();
  }

  @override
  Future<bool> moveAppToBackground() {
    // TODO: implement moveAppToBackground
    throw UnimplementedError();
  }


}

void main() {
  final VoiceKeepAlivePlatform initialPlatform = VoiceKeepAlivePlatform.instance;

  test('$MethodChannelVoiceKeepAlive is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelVoiceKeepAlive>());
  });

  test('getPlatformVersion', () async {
    VoiceKeepAlive voiceKeepAlivePlugin = VoiceKeepAlive();
    MockVoiceKeepAlivePlatform fakePlatform = MockVoiceKeepAlivePlatform();
    VoiceKeepAlivePlatform.instance = fakePlatform;

    // expect(await voiceKeepAlivePlugin.getPlatformVersion(), '42');
  });
}
