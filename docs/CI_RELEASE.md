# GitHub Actions 与 Release

工作流 `.github/workflows/android.yml` 使用现有 Wrapper、JDK 21 与固定依赖。日常 CI 检查新版英语词库及中英语音资源，运行 lint 和 Debug 编译／打包；不再准备或检查汉字笔顺与旧演示数据。远端 CI 行为与本次仅在本机执行的静态及编译检查分开记录。

应用 ID 为 `com.example.flashcardforteens`。由旧应用身份切换至新应用的 `0.0.1 / 1` 是独立基线，不触发自动 Release；其后的同应用版本仍要求 versionName 和 versionCode 同时递增。首次公开发布需维护者明确选择现有 manual dispatch 的 publish_release，默认不发布。

后续正式发布沿用现有 Secrets、草稿 Release、签名与 ZIP 对齐校验、版本及 applicationId 核对；产物名称为 `Flashcard_for_Teens_v<versionName>.apk`。标签已指向其他提交、同版本已公开或门禁失败时，不覆盖既有发布。

签名材料仅由发布 job 在临时目录使用，密码不写入源码或日志。默认 contents:read，只有发布 job 使用所需写权限。PR 不读取发布签名材料。

本次不提交、推送、执行远端 Actions、创建标签或发布。签名、CI、打包与设备验收分别报告，不能互相替代。
