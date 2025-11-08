# Changelog

## [1.0.0] - 2025-09-21
- Initial release of VoiceKeepAlive plugin
- Supports Android & iOS background microphone keep-alive
- Provides startService() and stopService() API
## [1.0.1] - 2025-09-22
## [1.0.2] - 2025-09-23
- update README.md
## [1.0.3] - 2025-09-24
- Support audio streaming after screen off
## [1.0.4] - 2025-09-25
- Switch between microphone and listening icon
- ## [1.0.5-dev.1] - 2025-09-25
Adapt【ar 、en 、fil 、hi 、id 、th 、tr 、ur 、vi 、zh】language
- ## [1.0.5] - 2025-10-09
Improve functionality and reduce power consumption.
- ## [1.0.6] - 2025-11-06
fix(crash):
ANR com.mice.voice_keep_alive.services.VoiceKeepService.startSilentPlayback (SourceFile)
Crash Context.startForegroundService() did not then call Service.startForeground() ServiceRecord{e335bac u0 com.levende.rinacom.mice.voice_keep_alive.services.VoiceKeepService}