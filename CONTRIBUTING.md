# 贡献指南 Contributing

欢迎提交 Issue 和 Pull Request！

## 开发环境

- JDK 17、Android SDK Platform 37
- 构建：`./gradlew :app:assembleDebug`
- 测试：`./gradlew :app:testDebugUnitTest`
- 国内网络可在 `~/.gradle/gradle.properties` 加 `opentraduko.cnMirrors=true` 启用阿里云镜像

## 提交 PR

- 一个 PR 解决一件事，标题可用 `feat:` / `fix:` / `docs:` 前缀
- 提交前确保构建与单元测试通过，并在真机上验证
- 界面改动请附截图，用户可见改动请在 `CHANGELOG.md` 添加条目
- 不要提交密钥、签名文件、`local.properties` 或模型文件

## 版权头

所有源码文件（Kotlin、Gradle 脚本、tools 下的脚本）都必须带有 GPL-3.0-or-later 版权头，格式见任意现有文件开头。提交前校验：

```bash
tools/check-license-headers.sh
```

## 代码风格

- Kotlin 官方风格，Compose 惯用写法
- 字符串走资源文件，至少维护中英两份（`values/`、`values-en/`）
- 纯逻辑（分句、调度、历史存储、设置）应放在可单元测试的类中，不依赖 Android 框架
