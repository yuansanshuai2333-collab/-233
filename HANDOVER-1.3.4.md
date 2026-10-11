# 自然关系 1.3.4

整体审查先记录后修复，新增 12 类覆盖、20 项运行时断言。证据目录：H:/杂事/自然关系-1.3.3整体先查后修。

修改：FriendRetention 仅排除核心绑定的嘎吱怪，按关系对隔离回想；OffspringAffinityRules.nextGeneration 饱和递增并接入两条出生路径；ReunionRecipe 拒绝非正充能编号。

验证：549 单元测试、321 服务端断言、35 复活流程、4 重启恢复、5 无 NoN 启动检查通过。生产与构建 src 无差异。审查探针位于 audit-1.2.39-recheck/audit/probe-src/audit/WholeAudit134Probe.java，不打入交付包。

旧 RetirementProbe 中嘎吱怪排除用例补充 initHomePos，明确测试核心嘎吱怪；无核心嘎吱怪由新增用例覆盖。附近父代测试使用玩家附近已参与实体查询的区块，避免远处非活动区块导致无法选择父代的假失败。

未更改玩法门槛、奖励、核心原版退场、归属或怀孕机制。没有实际整合包长期游玩验收，开发服务器没有动画内容包。

客户端实际启动及三种物品状态资源检查：3 项通过。交付 jar SHA256：2243EC21B797AF996A4ADF4C4F22C3EA7C6D3506C5DFD02A9508B05C06CDEE0A。
