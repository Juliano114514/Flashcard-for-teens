# GitHub Actions 与 Release

工作流 `.github/workflows/android.yml` 使用现有 Wrapper、JDK 21 与固定依赖。push 到 main 和 PR 均检查冻结英语词库、中英语音资源，执行 `:app:lintDebug :app:assembleDebug`，上传 Debug APK 和 lint 报告；不新增或运行测试。

## 版本与发布

`app/build.gradle.kts` 是版本事实来源。main 上同应用版本的 versionName 和 versionCode 同时增加时，CI 成功后自动构建并发布正式 Release。版本不变的文档提交只运行 CI。也可在 Actions 手动运行，勾选 `publish_release` 重试当前 main 的发布，默认不发布。

旧中文包 `com.example.chinese_flashcard` 到新包 `com.example.flashcardforteens` 只允许已记录的 `0.0.1 / 1` 独立基线或用户指定的首次正式版 `1.0.0 / 9`；前者不自动发布，后者触发正式发布。该例外只适用于包名切换，后续不允许版本倒退。

Release 标签为 `v<versionName>`，附件为 `Dancibao_v<versionName>.apk` 与 `SHA256SUMS.txt`。标签指向其他提交时失败，已有公开版本不覆盖；失败留下的同提交草稿可重试。正式 APK 经过 R8／资源压缩、独立签名、ZIP 对齐、包名／版本／非 debuggable 校验，再由草稿转为公开。

## 签名与安全

仓库需配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 四个 Actions Secrets。密钥与密码不进入源码或日志。签名 job 只接收可信 main 的 unsigned Release，临时密钥文件以私有权限创建并在退出时删除。PR 不使用签名 Secrets。默认 contents:read，只有发布 job 使用 contents:write。

单词宝使用独立正式签名；本机备份放在用户目录下受 ACL 保护的 `.android/keys/dancibao-release`，密码使用当前 Windows 账号的 DPAPI 加密保存。不得删除或丢失密钥，否则无法给已安装正式版提供签名一致的升级；迁移电脑前需另行安全导出备份。

正式包与此前 Debug 测试包签名不同，无法直接覆盖。保留测试包及学习数据，不建议通过卸载测试包换装来处理签名冲突；数据迁移不属于本次发布改动。

CI 成功、签名／发布成功与设备安装／运行／真人试听分别记录，线上构建不代替真机验收。
