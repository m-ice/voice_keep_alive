# VoiceKeepAlive 插件

`VoiceKeepAlive` 是一个 Flutter 插件，用于在后台保持语音服务活跃。它支持在 Android 和 iOS 平台上，保证 App 退到后台或息屏时，仍能持续运行语音采集或推流任务。

---

## 📦 安装

在 `pubspec.yaml` 中添加依赖：

```yaml
dependencies:
  voice_keep_alive:
    git:
      url: https://github.com/m-ice/voice_keep_alive.git
      ref: main
```

---

## 🚀 使用方法

### 1. 导入插件

```dart
import 'package:voice_keep_alive/voice_keep_alive.dart';
```

### 2. 启动后台服务

```dart
await VoiceKeepAlive.startService();
```

启动后，插件会在后台保持语音服务活跃：

* **Android**：启动前台服务，显示通知，保持 WakeLock 和 AudioFocus。
* **iOS**：配置后台模式（Audio）后，可继续采集音频。

### 3. 停止后台服务

```dart
await VoiceKeepAlive.stopService();
```

停止服务会释放相关资源。

---

## 📘 API 说明

### `VoiceKeepAlive.startService()`

* **描述**：启动后台语音保活服务
* **返回值**：`Future<void>`
* **注意事项**：需要麦克风权限（Android: `RECORD_AUDIO`，iOS: `NSMicrophoneUsageDescription`）

### `VoiceKeepAlive.stopService()`

* **描述**：停止后台语音保活服务
* **返回值**：`Future<void>`

> ⚠️ 注意：你在 Dart 端调用 `stopService()` 时，请确保它调用的是 `VoiceKeepAlivePlatform.instance.stopService()`，而不是重复调用 `startService()`。

---

## ⚙️ 平台配置

### Android

1. 在 `AndroidManifest.xml` 中声明权限：

```xml
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="oppo.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_TYPE_MICROPHONE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Android 13+ -->


<service
android:name="com.mice.voice_keep_alive.services.VoiceKeepService"
android:enabled="true"
android:exported="false"
android:foregroundServiceType="microphone|mediaPlayback" />
        <!--        mediaPlayback|mediaProjection-->
```

2. 插件会自动创建前台服务通知，保持 WakeLock 和 AudioFocus。

### iOS

1. 在 `Info.plist` 中添加：

```xml
<key>NSMicrophoneUsageDescription</key>
<string>需要访问麦克风用于语音通话</string>

<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>

3️⃣ 可选：蓝牙音频支持
如果你需要支持蓝牙耳机或外接音频设备：
<key>NSBluetoothAlwaysUsageDescription</key>
<string>需要访问蓝牙以连接耳机进行语音房通话</string>
否则在蓝牙设备上可能无法收音或播放。
```

2. 在 Xcode 的 **Signing & Capabilities → Background Modes** 中勾选：

    * Audio, AirPlay, and Picture in Picture

3. 插件会在后台启动 `AVAudioEngine`，保持音频采集活跃。

---

## 📱 示例

```dart
import 'package:flutter/material.dart';
import 'package:voice_keep_alive/voice_keep_alive.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text("VoiceKeepAlive Example")),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              ElevatedButton(
                onPressed: () async {
                  await VoiceKeepAlive.startService();
                },
                child: const Text("启动语音保活服务"),
              ),
              ElevatedButton(
                onPressed: () async {
                  await VoiceKeepAlive.stopService();
                },
                child: const Text("停止语音保活服务"),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
```

---

## 🔮 TODO

* 支持自定义通知标题/内容（Android）
* 提供服务状态回调
* 完善 iOS 后台运行逻辑
* 支持多语言国际化
