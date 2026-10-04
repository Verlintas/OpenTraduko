# OpenTraduko

[![Build](https://github.com/Verlintas/OpenTraduko/actions/workflows/build.yml/badge.svg)](https://github.com/Verlintas/OpenTraduko/actions/workflows/build.yml)

开源、离线优先的 Android 同声传译应用。装上就能用，不需要账号、不需要后端，语音与文本默认不离开设备。

An open-source, offline-first simultaneous interpretation app for Android. No account, no backend, and by default your voice and text never leave the device.

## 功能 / Features

- **对话模式**：手机放中间，上下双栏面对面互译，对侧文字自动旋转 180°
- **聆听模式**：持续识别外语内容，滚动显示原文与译文，可语音播报
- **免费优先**：Vosk 离线语音识别 + ML Kit 离线翻译 + 系统 TTS，全程零费用
- **无 GMS 依赖**：识别与语音合成不依赖 Google 服务，大陆设备可用
- **半双工防回声**：未戴耳机时自动暂停采集，戴耳机自动切换全双工同传
- **模型按需下载**：识别模型不打包进 APK，支持自定义镜像地址
- **历史记录**：会话自动保存，可回看、导出为文本
- **中英优先**：默认打磨中文↔英文，架构支持任意语言对

## 工作原理 / How it works

```
麦克风 16kHz PCM ──> Vosk 流式识别 ──> 分句调度 ──> ML Kit 翻译 ──> 字幕 + TTS
     AudioRecord        partial/final      clause         offline
```

- 语音识别（ASR）：[Vosk](https://alphacephei.com/vosk/) 小模型，完全离线、流式输出
- 翻译（MT）：Google ML Kit 端侧翻译模型，离线、低延迟
- 语音合成（TTS）：Android 系统 TTS 引擎

## 系统要求 / Requirements

- Android 8.0 (API 26) 及以上
- 首次使用需联网下载识别/翻译模型（之后可完全离线）
- 建议 4GB 以上内存；arm64 设备体验最佳

## 构建 / Build

```bash
# JDK 17 + Android SDK Platform 37
./gradlew :app:assembleDebug
```

国内网络可在 `~/.gradle/gradle.properties` 中启用阿里云镜像：

```properties
opentraduko.cnMirrors=true
```

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

## 模型说明 / Models

| 模型 | 用途 | 大小 | 来源 |
| --- | --- | --- | --- |
| `vosk-model-small-cn-0.22` | 中文识别 | ~42MB | alphacephei.com |
| `vosk-model-small-en-us-0.15` | 英文识别 | ~40MB | alphacephei.com |
| ML Kit zh / en | 翻译 | ~30MB | Google |

- Vosk 模型可在设置中自定义下载地址（镜像/离线包）。
- ML Kit 翻译模型由 Google 分发，中国大陆网络可能需要代理才能下载；下载一次后即可离线使用。
- 云端翻译引擎（自带 API Key）在路线图 v0.2 中，用于需要更低延迟或更高准确率的场景。

## 已知限制 / Known limitations

- Vosk 小模型中文准确率一般（安静环境、清晰发音效果最好）
- 未连接耳机时采用半双工：播报期间会暂停识别，可能漏听下一句；连接耳机后自动切换全双工
- 说话人切换目前为手动（点击对应栏），自动语种识别在规划中

## 隐私 / Privacy

- 无账号、无统计、无广告
- 音频仅在设备本地处理，默认不上传任何服务器
- 历史记录保存在应用私有目录，可随时删除

## 许可证 / License

[GPL-3.0-or-later](LICENSE)。第三方组件许可见 [NOTICE](NOTICE)。
