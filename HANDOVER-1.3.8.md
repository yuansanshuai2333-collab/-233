# 自然关系 1.3.8：重逢之愿复活展示

用户要求成功复活时播放其提供的九秒 MP3，音量很低，伴随白色光点及宝石升起、转一圈、变黯淡、回手动画。后补 Bilibili 参考链接无法加载，已告知按明确文字描述实现。

ReunionService 在 spawn 成功、账本和物品能量提交后调用 ReunionEffects，向同世界 32 格内且支持新频道的客户端发送 ReunionEffectPayload。只传使用者 UUID、绑定标识、使用手、维度和复活坐标，不传关系快照或真实物品。未手持的程序调用不播放展示，原复活 API 保留。

ReunionEffectClient 使用本地负 ID 的原版 ItemDisplayEntity 展示 gem；不生成服务端实体或真实掉落，不写入存档。80 tick 动画：16 tick 升起、至 60 tick 完成一次 360 度旋转并切换 dim 模型、64～80 tick 返回。位置跟随眼部位置、朝向、俯仰和惯用手。客户端两个 Mixin 只隐藏匹配使用者、手和绑定标识的手持渲染。匹配失败、死亡、离开世界/范围、断线清理；同玩家新效果替换旧效果，全局最多 16 个。正常完成展示后允许九秒声音自然结束。

音效：用户 H:/杂事/audio-clips/BV1yBWNzNEzc/clip-00m03s-00m12s.mp3 转 mono OGG，滤镜 -18 dB 加首尾短淡化，播放增益 0.35、PLAYERS 分类、12 格衰减距离。新增中英文字幕。END_ROD 点经 scale 和寿命调整，避免近距离大光斑遮挡宝石；原贴图不修改。

验证：552 单元、396 服务端回归、49 实景客户端/集成服务端断言、5 无 NoN 启动均通过，发布门通过。实景覆盖成功通知、主副手、第三人称手持清理、声效播放、dim 切换、正常回手、切换物品、错误维度/非有限位置忽略，以及活动效果断线清理。真实客户端截图已检查。未做多人实机同时观看、整合包长期测试。

测试夹具曾受新运行目录的 AccessibilityOnboardingScreen 和 NoN 免费说明屏幕暂停影响，另一次夹具在创建物品同 tick 立即发送效果，早于客户端收到手持同步；最终夹具等待手持同步后发起服务端复活，49/49 通过。这些中间日志已保留，未修改正式游戏实例或 NoN jar。

证据：H:/杂事/重逢之愿-复活动画-1.3.8。交付：其下交付-1.3.8/naturalrelationship-1.3.8+1.21.11.jar。SHA256：A03A28CEBC6B1134C02FBC9D3AF9A4E9282D00976460421310BEEC1B8D385BBE。

正式源码 I:/DSH/work/noncreatureaffinity，隔离构建 I:/DSH/work/audit-1.2.39-recheck；src 和 gradle.properties 已核对一致。测试 probe、init.gradle 在后者 audit/effects-*，未打包到正式 jar。版本保持 1.3，仅补丁升至 1.3.8。
