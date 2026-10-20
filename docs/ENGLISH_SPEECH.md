# 英语语音与音标

保留 ONNX Runtime、Kitten TTS Nano 0.8 INT8 与 Bella 声线，固定来源及 SHA-256 仍由 tools/english_resources/source-lock.json 管理。不更换模型／声线，不新增 Android 依赖。运行时本地合成，缓存和预加载继续由现有应用级协调器管理。

新版资源从当前 12 列英语 CSV 提取英语词头、英语例句及英文语块，生成实际模型所需的固定发音数据并校验覆盖。保留 eSpeak en-US 前处理，因此音频沿用现有口音。英文 IPA 直接来自每条 CSV，不再用旧 en_US 词典查询覆盖，短语和大小写不同词条也保留原音标。

当前 CSV 的 en_UK 音标来源声明与 GPL-3.0 全文保留在 `english-speech/ipa-data-sources.json`、`licenses/IPA_EN_UK_GPL-3.0.txt` 及应用可读的 `LICENSES.txt`，与 Kitten／ORT 许可分别说明。`tools/build_english_resources.py --refresh-licenses` 从锁定的文本更新声明和资源字节数，不重生成发音 token；`--check` 同时核对这些记录，不恢复旧音标查询表。

read／close 等多读音及例句可选 wordIpa 均原样保留。词头和例句分别请求发音；音标是教学数据，不能直接作为朗读原文或视为已验证的音频发音结果。

默认场景以英语为主。答题题干和上下文只允许英语请求，不传中文 counterpart；即便朗读偏好选双语，也不能提前读出中文答案。手动中文按钮播放对应中文，答后解析允许按偏好播放双语。

未被发音资源覆盖的自定义文本沿用设备离线英语 TTS；不可用则提示并继续学习。资源前端覆盖不等于逐条音质、语境读音或 Android 设备试听验收。

准备、重建与只读校验使用 `python tools/build_english_resources.py --help` 所列入口；检查为 `--check`。模型／声线、源码及构建工具各自许可保留，不将新教材词表重新标为模型许可。
