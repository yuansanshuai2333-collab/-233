# 进度报告与待协助事项（rc.39）

> 面向 GPT/审查者的交接说明。分两部分：**A. 目前进度**（含"已自动验证 / 已服务端验证 / 仅人工可验证"三层）、
> **B. 我难以独立完成、需要协助的事项**（每条都写清：卡点、我的证据、需要什么、验收标准）。
> 版本状态：**1.0.0-rc.39，候选，未发布正式 1.0.0**。

---

## A. 目前进度

### A.1 产物与质量门

| 项 | 值 |
|---|---|
| 包 | `build/libs/naturalrelationship-1.0.0-rc.39+1.21.11.jar` |
| 体积 / SHA256 | 299763 字节 / `B1DC64A56D4AB8BDA2E4DAA56C3464F646ADE7CC44DE4FA43FC89C87B8C883FF` |
| 自动测试 | **246 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 混入 | 常驻 10 个（`required: true`）+ 可选集成 2 个（`required: false`，插件按 NON 版本开关） |
| 新增配置 | `affinityLossOnEscape`（默认 25，0=关）、`nonStruggleScreen`（默认开） |
| 新增按键 | `key.noncreatureaffinity.break_free`（**默认未绑定**，兜底用） |

### A.2 已修复的问题（按批次）

| 批次 | 内容 | 关键证据 |
|---|---|---|
| rc.31 | **rc.26 启动阻塞**：`Entity#readCustomData` 是抽象方法，`@At("TAIL")` 无 RETURN → 改用具体方法 `readData`（`method_5651`） | 发布包注解 + 服务端启动 `Done` |
| rc.31 | 时机正确性：生产命名空间导出类中 `readData` 内顺序 = Fabric 附件(586) → 原版 `readCustomData`(589) → NON 系列(648–666) → 我方回填(672) → return | 导出类反汇编 |
| rc.31 | **坐下宠物没有落到 WAIT**（旧判据恒为死分支："未选过模式"不可表达） | 新规则 `resolveInitialMode` + 反例测试 |
| rc.31 | 逐实体遍历误创建并持久化空附件（`tickCooldowns` 等） | 实机日志 `entries: []` → 改为 `peek` |
| rc.33 | **正常驯马入口从未同步**：`bondWithPlayer` 先写主人(偏移2)后设驯服标志(偏移7)，旧锚点 `setOwner` TAIL 处 `isTame()` 恒为 false | 原版字节码调用序列测试 + 临时旧锚点复现失败 |
| rc.35/37 | **结算判定**：公开停止事件不带原因 → 改为观察真实代码路径；**手动推进阶段**（NON 调试键 `next_stage_debug`）不再算完成 | 你的实例日志 14:26:39–40 四行"已请求下一阶段" + 生产字节码调用点核对 |
| rc.36/38 | 挣脱额外扣好感 + **挣扎成功不发友情之证**（门槛 `isMatingGainReason`） | 规则/接线测试 |
| rc.39 | **强行交流接入 NON 官方 A/D 挣扎界面与真实逃脱进度**：按攻击逃脱启动（Contest 才会建立） | 混入应用到 `NeedsOfNature`，`completeAttackEscape` 偏移 5 调用我方处理器 |

### A.3 验证分层（**严禁混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 246 用例全绿（规则、会话状态机、接线/字节码契约、配置语言审计） |
| 发布包结构 | 通过：注入目标、配置清单、`fabric.mod.json`、混入注解全部反汇编核对 |
| 服务端启动（NON 1.5.11 + 你的模组组合 + 默认内容包 368 定义） | 通过：`Done`、零注入失败、适配器 `enabled and verified` |
| 服务端行为 | 通过：旧宠物回填（坐下→WAIT、马→FOLLOW）、持久化、幂等 |
| **客户端与实机行为** | **基本未验证**（唯一实测来自你那轮 rc.36 日志；rc.34–rc.39 均未实机回归） |

### A.4 你那轮日志（rc.36）改进了什么

14:26:21 拒绝（−15→70）→ 14:26:21 强制交流开始（`forced=true`）→ **14:26:39/40 连续 4 次"已请求下一阶段（服务端）"** → 14:26:40 判为完成 +10（→80）。
→ 结论：那次结束是**被调试键推过最后阶段**，被我方误判为自然完成。**rc.37 起**该情况记为 `evidence=MANUAL_ADVANCE`，**不结算、不扣分**，日志可核对。

---

## B. 难以独立完成、需要协助的事项

### B1（最高优先）**无法在本机做客户端实机验证**——需要有人跑并回传日志

- **卡点**：所有关键行为（A/D 挣扎界面是否出现、进度是否推进、生存/伤害副作用、F 键四模式、驯马驯狗）都需要真实客户端；
  我这台机器只能起专用服务器（无玩家实体 → 无法启动任何 玩家+生物 动画）。
- **我的证据**：所有服务端验证记录在 `HANDOVER-rc*.md`；自动测试不能覆盖事件时序与真实停止原因（我在 rc.35 文档里也明确写了这一点）。
- **需要 GPT 做的**：用客户端跑下列场景，**按实例逐条回传日志行 + 好感数值 + 是否收到友情之证**：
  1. **A/D 挣扎（新）**：拒绝 → 强制交流 → 期望出现 NON 挣扎条，连点可推进；
     成功脱离后期望 `NON reported a successful struggle for mating <id>` →
     `settled as NONE … escapedByPlayer=true` → `configuredEscapeLoss=25 applied=-25`，**无友情之证**。
     失败/未出现界面时回传 `could not start the forced mating as an attack escape (...)` 那行。
  2. **早期挣脱 / 较晚挣脱 / 正常跑完**（三条，分别记录好感变化与是否发证）。
  3. **手动推进阶段**：期望 `evidence=MANUAL_ADVANCE`，好感不变、不扣分、不发证。
  4. **`nonStruggleScreen` 关闭**后再试：期望走"普通动画 + 挣脱键"。
- **验收标准**：日志行与我方预期 marker 完全对应；任何一条不符，把原文发我定位。

### B2 **NON 内部结构依赖的风险与"攻击载荷"语义**——需要非我本机可得的信息

- **卡点**：为了让 A/D 界面**有反应**，必须让 NON 为这次动画建立 Contest，而它的建立条件是
  `onAnimationStarted` 收到**非空 `PlayerAttack` 且 `victim ∈ actors`**（首句 `ifnull → return`）。
- **我的证据**：
  - `sendEscapeControlState` 局部变量表：`isAttack / isDefeatAnimation / isStuckAnimation / escapable`，
    其中 `escapable = isAttack && isEscapableStage(stage)`；
  - `resolveEscapeSettings` 首句 `!isAttack → EscapeSettings.none()`；
  - `NonPlayerAttackSystem$Contest` 字段 `(victimUuid, finishOwnerUuid, initiatorUuid, attackerUuids, progress, …)`；
  - `PlayerAttack` 记录字段 `(victimUuid, finishOwnerUuid, initiatorUuid, attackerUuids)`。
- **我不确定的**：**NON 自己的"生物攻击玩家"（PvE）流程到底怎么填 `PlayerAttack`**（尤其
  `finishOwnerUuid` 是否必须为 null、`initiatorUuid` 用生物还是玩家）。这段构造在包私有类里，我只能从 PvP 路径反推。
  我当前用的是 `(victim=玩家, finishOwner=null, initiator=生物, attackers={生物})`。
- **需要 GPT 做的（任一即可）**：
  1. 帮忙在 NON 端确认/找到 PvE 攻击载荷的真实构造（源码或反编译对照）；
  2. 或向 NON 作者确认；
  3. 若确认不同，把正确形状给我，我改一行即可（结构已隔离在 `startEscapableForcedMating`）。
- **验收标准**：A/D 界面出现且连点**确实推进**、成功逃脱时 NON 走它自己的 `completeAttackEscape`（我方日志会打印）。

### B3 **`StopReason` 读不到**——需要上游或更巧的适配方案

- **卡点**：`ServerAnimationController$StopReason` 是**包私有**枚举，只作为参数传给
  `stopInstance(World, UUID, boolean, StopReason)`；我尝试以完整描述符注入该方法，Mixin 报
  `Scanned 0 target(s)`（映射写法与中间名写法都试过，`require=1` 强制暴露），因此改为"按代码路径判定"。
- **现状**：自动完成 = tick 路径（`maybeAdvanceStages` 内联停止）；其余一律不奖励。
- **需要 GPT 做的**：评估是否有更可靠的读取方式（例如在 `fireStopEvent` 处读 snapshot、或用 ASM/反射读
  内部 `StoppedContext.snapshot()`），或把上游请求（`StoppedContext` 暴露 `StopReason`）推进。
- **验收标准**：判定不再依赖"路径枚举"，而是直接使用 NON 报告的原因。

### B4 **产品政策需要拍板（我不应替你决定）**

1. **未挣脱、自然跑完的"强制交流"是否应该加好感？** 现行：会（+`affinityPerMating`，非主动加成）。
   从语义上看"被强迫还加好感"可能不合理；改成 0 或改成扣分都是一行配置，但需要你决定。
2. **用调试键"下一阶段"推完强制交流**，现行是"不奖励、不扣分"。是否应视为**挣脱**（扣 `affinityLossOnEscape`）？
   好处：堵住"跳过以逃避扣分"的路径；坏处：把调试操作等同于挣扎。
3. **`nonStruggleScreen` 默认开**是否接受它带来的 NON 攻击副作用（逃脱伤害 / 逃脱后无敌 / 计入逃脱统计 /
   可能与 Give Up、逃脱后追击联动）？
4. **本模组自建"挣脱"按键**是否保留（默认未绑定），还是等上游接口后移除？

### B5 **长期运行与内存/状态边界**（我有代码级保证，但缺长时间实测）

- 我已做：`MatingSessions` 终态集合上限 1024、`MatingStopEvidence` 上限 2048、投射物按实例身份释放且 `setRemoved` 幂等、
  `CompanionFollowService` 驾驶记录上限 512。
- **缺**：多小时会话里 `/nonaffinity stampcounts` 与相关计数的回落曲线（需要实机）。
- **需要 GPT 做的**：在长时间会话中每隔一段时间执行 `/nonaffinity stampcounts`，回传 `melee/projectile/projectileUuidIndex` 序列。

### B6 **客户端兼容矩阵**（我只有服务端）

- 环境 A（仅 Fabric API）与 B（你的完整组合）我都只测了**服务端**；客户端专属模组（CustomSkinLoader、
  entity_model_features、entity_texture_features、modmenu）在服务器被跳过，其客户端共存**未验证**。
- **需要 GPT 做的**：客户端 A/B 两组各跑一次"能启动、进世界、退出重进、日志无注入报错"。

### B7 **发布门槛的建议**

- 我倾向：**先拿到 B1（实机挣扎/结算）+ B2（攻击载荷确认）的结论，再谈 1.0.0**；
  在 NON 未提供公开接口前，rc.39 对 NON 内部的依赖属于"版本受限 + 可回退 + 失败大声报告"的可接受范围，
  但这是**需要审查者认可**的判断。
- **需要 GPT 做的**：给出"依赖内部实现是否可作为 1.0.0 发布条件"的明确意见；若否决，
  我按 B3/B4 退回到"仅本模组挣脱键 + 不依赖内部接口"的形态。

---

## C. 现成可用的核对命令（便于 GPT 直接执行）

```bash
# 1) 我方全部结算/适配器日志
grep -E "natural-completion adapter|stop-path adapter|mating settlement|settled as NONE|mating settled for|completed with|is breaking free|NON reported a successful struggle|never observed running|ignoring duplicate" logs/latest.log

# 2) NON 侧挣扎/跳阶段痕迹
grep -E "next_stage_requested|escape|struggle" logs/latest.log

# 3) 报错与回退
grep -E "could not start the forced mating as an attack escape|InvalidInjection|Mixin apply for mod noncreatureaffinity" logs/latest.log
```

**我方日志字段含义**：`evidence=NATURAL_END`（tick 自动完成）/ `MANUAL_ADVANCE`（手动推进）/
`INTERRUPTED`（其他停止）/ `UNKNOWN`（适配器未生效）；`escapedByPlayer` 表示"玩家挣脱"；
`applied=` 是**实际**加/扣数值（会被上下限夹取）。
