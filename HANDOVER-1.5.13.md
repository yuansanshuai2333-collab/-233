# 1.5.13 生命周期与嘎吱修复内部候选

基于冻结1.5.12；仅S3生命周期防重保留、嘎吱核心性别接续及专用受击保护，不改奖池/怀孕/核心寿命/NoN生成。公开仍1.4.19，本候选未合入/安装/发布。正式JAR SHA256：5363abed88d593f6232b1e60606cb8c8d2cc7aca1e57435ad274d2253a48682f。

S3：MatingSessionLifecycle以真实当前ServerWorld/服务器身份及NoN getSession准入，覆盖START与三处返回补记。STOP消费全批后pin，重入/迟到拒绝；finally释放反馈及pin，未知死STOP不增历史，未知活STOP保留终态；仅引擎已移除且结算退出时服务tick清理，discard/重放/消失走同链。关闭先封准入再清，逃离即时诊断检查本次running被消费，不依赖永久handled历史。无TTL/容量淘汰，尚活故障实例必须保留保护。

嘎吱：原核心关系记忆新增有效non_gender 1/2/3，在既有restore同步gender.male/female标签和实际GenderHolder。旧无字段/非法值/NONE0不猜、不锁定；NoN仍拥有原生生成。重放不捕获临时性别，未复制怀孕/精力/实体NBT。专用damage在becomeAngryAndGetPlayer前复用FriendDamageProtection，对方玩家、开关、门槛、自卫、无核心和绕过无敌分支保留。

完整112套724单测0失败错误跳过。实际固定NoN专服99/0（首服85+同JVM新服14），新进程核心读档3/0，有效苍白橡木核心及树脂6/0。原始实际日志/QA及边界见验证报告；两玩家为实际服务器构造实体，不是真人TCP；同JVM专服工具适配进程级bootstrap，不等同客户端菜单自然重开。下一步独立复审13，另补丁R1，之后生产池；12的R1未由13修，12及整体未达最终发布门槛。
