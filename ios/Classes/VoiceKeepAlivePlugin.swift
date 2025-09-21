import Flutter
import UIKit
import AVFoundation

public class VoiceKeepAlivePlugin: NSObject, FlutterPlugin {

    private var audioEngine: AVAudioEngine?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "voice_keep_alive", binaryMessenger: registrar.messenger())
        let instance = VoiceKeepAlivePlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "startService":
            startService()
            result(nil)
        case "stopService":
            stopService()
            result(nil)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func startService() {
        do {
            // 配置后台音频
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .defaultToSpeaker])
            try session.setActive(true)

            // 保活用的音频引擎
            audioEngine = AVAudioEngine()
            let input = audioEngine?.inputNode
            let format = input?.inputFormat(forBus: 0)

            input?.installTap(onBus: 0, bufferSize: 1024, format: format) { (buffer, time) in
                // 这里可以处理音频数据，例如传给服务器
            }

            audioEngine?.prepare()
            try audioEngine?.start()

            print("iOS VoiceKeepAlive: 服务已启动")
        } catch {
            print("iOS VoiceKeepAlive 启动失败: \(error)")
        }
    }

    private func stopService() {
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine = nil
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            print("iOS VoiceKeepAlive 停止失败: \(error)")
        }
        print("iOS VoiceKeepAlive: 服务已停止")
    }
}
