# 开发与维护

当前工程与应用名为 Flashcard for Teens，版本 `0.1.0 / versionCode 6`，应用 ID／包前缀 `com.example.flashcardforteens`。Room schema v2，库名仍为 `flashcard-for-teens-v1.db`；通过 1→2 迁移保留本应用学习记录。迁移只增加独立学习音效设置并统一讲解显示偏好，不使用降级清库。旧中文应用数据不属于此应用。

## 环境与模块

沿用 Wrapper、JDK 21、版本目录、Kotlin／Compose／Room／ONNX 依赖。八个运行模块为 app、domain、data、ui、media、study、profile、wordlist；build-logic 仍只提供 Android／Compose 约定插件。SDK 配置只写本机忽略的 local.properties，不提交 IDE、缓存、模型源、签名材料或 APK。

使用 Android Studio 自带 JBR。环境路径含空格时，JAVA_TOOL_OPTIONS 中属性值须保留引号；先检查根因，不清理全局缓存。中文模型仍使用 openFd 映射，`chinese-speech/model-duration.onnx` 必须保持不压缩；英语模型保留原流读取方式。

## 词库与状态

根 CSV 为唯一当前英语词库入口，详见 [契约](CSV_IMPORT.md)。当前为 5,970 条，按八学期重新评估建议归属；教材原始年级与定位保留在来源 JSON 中。保留词条的 ID 与英语身份稳定，删除的纯语气词 ID 不复用。同形词合并教学义、词性和对应例句，大小写产生不同含义时独立保留。详见 [2026-10-20 逐条复核记录](wordlist-review.json)。

八学期精确筛选新学，每学期每日计划独立；复习、续学、收藏和错题跨学期。新学按 CSV 次序排列，取消拼音排序。保留 Room 事务、操作锁、上下文版本、取消与旧回调防护，不用移除校验换取通过。

## 资源与检查

模型及构建期工具继续由固定版本和 SHA-256 锁定。准备源放本仓忽略缓存，可以校验后复用本机原项目缓存，但不得改动原项目。先用本仓现有脚本帮助查看 prepare/build/check 入口；运行时不联网下载。

```powershell
python tools/rebuild_wordlist.py --check
python tools/build_english_resources.py --check
python tools/build_chinese_resources.py --check
.\gradlew.bat :core:data:kspDebugKotlin :app:compileDebugKotlin :app:lintDebug --console=plain
```

构建时通过 KSP 生成并核对 Room schema。词库结构、AI 语言复核、发音前端覆盖、编译、lint、打包与设备试听分别记录；按当前任务授权执行相应层级，实际结果以 VERSIONLOG.md 为准，不主动新增测试代码。

## 小学生界面与反馈

全局主题、渐变背景、半透明卡片、字号和操作尺寸由 core/ui 统一管理，三至六年级分别使用一套深浅配色，上下册一致。主操作使用 LearningPrimaryButton / LearningSecondaryButton，触摸高度至少 56dp；页面边距和区块留白采用 20dp，窄屏或大字号首页入口切为单列。装饰书本和星星为原创 Compose 矢量图形。

选择只保存同卡片、同学习上下文的本地草稿；“检查答案”才调用原 Submit 链路。旋转可以恢复草稿，换题或上下文变化后清空；未检查不记学习进度。原操作锁和提交版本校验必须保留。

初学、正确答案揭示后及详情固定展示英中例句；作答前仍仅英语。分步内容默认以英语分段、中文对应分段、完整译文三行展示，无展开按钮；无 chunks 时回退原句和已有顺序直译。旧 explanationMask 字段仅保留数据兼容。INTRO 集中展示讲解，FEEDBACK 的下一步直接结束当前卡片并按原队列选下一张，不再新建答后 EXPLANATION；已保存的 EXPLANATION 仍可继续。轮次、作答提交、学习完成和复习排期仍由原事务维护。

进入讲解页和答对后的继续页通过同一 speakCard 序列，按英语单词、中文释义、当前展示的例句英语、例句译文的顺序朗读。讲解页使用第一条例句，答对后使用当前上下文例句，无上下文则使用第一条。STUDY_EXPLANATION_WORD 和 STUDY_EXPLANATION_EXAMPLE 复用 speechModesJson，分别存储单词和例句的双语偏好，在设置页表现为四个独立开关；未单独设置时继承旧 STUDY_EXPLANATION 显式偏好，否则默认全部开启，不增加 Room 字段。其余例句仍可切换并手动朗读；问题页始终不携带中文答案。单项音频失败不阻断后续项目，离页和生命周期取消保持原逻辑。答错反馈仍沿用 ANSWER_FEEDBACK 设置；答对后是否等待全部朗读结束才能继续仍由 waitForCorrectAnswerAudio 控制。

英语构建前处理对称谓 Ms／Ms. 使用 miz 发音输入，再经过现有 Kitten 归一化与音素生成，避免被小写化为 ms 后按字母读出；显示词头、CSV 字节与语音查询键保持原样。

learningSoundsEnabled 单独控制答对、答错和完成音效。新安装默认开启；1→2 迁移按旧 ANSWER_FEEDBACK 是否为 NONE 继承初值。朗读设置保持独立。音效使用媒体通道，与朗读共用媒体音量，不按铃声模式拦截。提交成功立即展示视觉反馈，反馈朗读跟随短音效；失败、静音和冷加载未就绪均跳过音效并继续，生命周期停止取消音效。

当前反馈使用 tools/generate_learning_cues.py 离线生成的原创正弦音符：答对 400ms、答错 320ms、完成 720ms，带平滑起落和较低增益。答对播放 10 粒、完成播放 24 粒随机彩纸；仅成功提交后触发一次，尊重系统关闭动画及生命周期取消。旧 Kenney CC0 资源和许可保留，来源见模块 assets/learning-sounds/NOTICE.txt。许可全文在应用资源许可页展示。资源、编译与 lint 校验不替代设备排版和真人试听。

学习音效由专用 MediaPlayer 播放，三个本地短音效进入学习页后 prepareAsync 预准备。创建、控制、回调和释放均在同一 HandlerThread，不在主线程查询音量、加载文件或操作播放器。正常播放只在 OnCompletion 回调后继续反馈朗读，不用“文件时长 + 固定余量”截停或推测结束；正常结束保留播放器，下次直接从头播放。取消只释放当前音效并重新准备，离页关闭全部播放器与线程；每次播放设 2 秒异常回调上限，避免反馈永久等待。Debug 日志只记录播放器类型、音效类别和开始／完成／取消／失败，不记录学习文本。

用户确认关闭学习音效时原朗读没有杂音，因此撤回此前针对朗读新增的非阻塞写入、20ms 淡出、串行锁、交接等待及延迟释放焦点，恢复修改前的 OfflineSpeech 实现。四段自动朗读、答对继续页朗读及相应设置保持；音效 WAV、词库、语音缓存和模型均未改变。SoundPool 更换为 MediaPlayer 后，用户在本次手机上开启音效、朗读中检查答案，已确认“杂音消失，切换正常”；该试听结果不外推到其他设备。
