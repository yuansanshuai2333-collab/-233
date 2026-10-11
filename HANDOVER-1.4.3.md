# 1.4.3 挚友自然退场保护

用户报告监守者钻地、嘎吱怪与心绑定后消失；用户随后明确要求嘎吱怪先跳过，同时授权修复恼鬼、末影螨、流浪商人和商人羊驼自然退场。

FriendRetention 按全部历史关系中当前达到可配置门槛者判断，不依赖玩家在线，不写永久 persistent 标记。明确排除 CreakingEntity、死亡实体及回想参与实体。

Warden mobTick 刷新 DIG_COOLDOWN 并恢复 DIGGING 姿态；DigTask.shouldRun 和 finishRunning 拦截挚友退场。Vex.tick 的 alive 字段读取只针对寿命分支；Endermite.tickMovement 的 isPersistent 调用仅在该计时分支扩展判定；商人和羊驼分别取消专门退场方法。MobEntity.checkDespawn 的保护先放行和平模式原版删除。

未修改嘎吱怪；不得在后续报告中称其问题已修复。死亡、变形、分裂和第三方特殊机制不是本次承诺的全面覆盖。

验证：旧版复现 8 项失败，新增 23 项全部通过，总服务端 259 项，单元 548 项。证据与交付：H:\杂事\特殊生物退场检查。
