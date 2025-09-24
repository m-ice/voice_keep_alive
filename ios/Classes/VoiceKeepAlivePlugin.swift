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
            if let args = call.arguments as? [String: Any],
               let mode = args["mode"] as? Int {
                startService(mode: mode)
                result(nil)
            } else {
                result(FlutterError(code: "INVALID_ARGS", message: "缺少 mode 参数", details: nil))
            }
        case "stopService":
            stopService()
            result(nil)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func startService(mode: Int) {
        do {
            let session = AVAudioSession.sharedInstance()

            if mode == 1 {
                // 主播模式：录音 + 播放
                try session.setCategory(.playAndRecord,
                                        mode: .voiceChat,
                                        options: [.allowBluetooth, .defaultToSpeaker])
                try session.setActive(true)

                audioEngine = AVAudioEngine()
                let input = audioEngine?.inputNode
                let format = input?.inputFormat(forBus: 0)

                input?.installTap(onBus: 0, bufferSize: 1024, format: format) { (buffer, time) in
                    // TODO: 麦克风数据处理（推流）
                }

                audioEngine?.prepare()
                try audioEngine?.start()

                print("iOS VoiceKeepAlive: 主播模式已启动")
            } else {
                // 观众模式：只播放
                try session.setCategory(.playback,
                                        mode: .default,
                                        options: [.allowBluetooth])
                try session.setActive(true)

                print("iOS VoiceKeepAlive: 观众模式已启动")
            }

        } catch {
            print("iOS VoiceKeepAlive 启动失败: \(error)")
        }
    }

    private func stopService() {
        if let engine = audioEngine {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
            audioEngine = nil
        }
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            print("iOS VoiceKeepAlive 停止失败: \(error)")
        }
        print("iOS VoiceKeepAlive: 服务已停止")
    }
}
