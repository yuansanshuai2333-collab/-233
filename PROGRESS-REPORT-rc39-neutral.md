# 技术进度报告与待协助事项（rc.39 · 中性口径）

> 面向外部审查者的**纯技术**交接口径。文中用中性的系统术语描述各子系统，便于只讨论工程问题：
>
> | 文中术语 | 含义 |
> |---|---|
> | 本体 / 上游 | 依赖模组 NeedsOfNature（NON），提供动画引擎与播放控制 |
> | 流程 | 引擎一次播放的动画交互（有唯一 `instanceId`） |
> | 流程定义 | 内容包里的动画定义（`NonAnimationDefinition`） |
> | 关系值 | 本模组维护的玩家–生物数值（affinity），有上下限 |
> | 奖励物 | 关系值到上限时发放的物品（friendship token） |
> | 中途脱离 | 玩家在流程未自然结束时使其终止（escape/abort） |
> | 强制启动 | 本模组在"请求被拒后"自行启动的流程（`forced=true`） |
> | A/D 提示条 | 上游自带的连点进度界面（用于中途脱离） |
>
> 版本：**1.0.0-rc.39**，候选，未发布正式版。

---

## A. 目前进度

### A.1 产物与质量门

| 项 | 值 |
|---|---|
| 包 | `build/libs/naturalrelationship-1.0.0-rc.39+1.21.11.jar` |
| 体积 / SHA256 | 299763 字节 / `B1DC64A56D4AB8BDA2E4DAA56C3464F646ADE7CC44DE4FA43FC89C87B8C883FF` |
| 自动测试 | **246 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 混入 | 常驻 10 个（`required: true`）+ 可选集成 2 个（`required: false`，插件按上游版本开关） |
| 新增配置 | `affinityLossOnEscape`（默认 25，0=关）、`nonStruggleScreen`（默认开） |
| 新增按键 | `key.noncreatureaffinity.break_free`（默认未绑定，作为兜底手段） |

### A.2 已修复的工程问题（按批次）

| 批次 | 问题 | 关键证据 |
|---|---|---|
| rc.31 | **启动阻塞**：`Entity#readCustomData` 是抽象方法，无 RETURN → `@At("TAIL")` 注入失败使整个 mixin 不适用；改用具体方法 `readData`（`method_5651`） | 发布包注解 + 专用服务端 `Done` |
| rc.31 | **注入时机**：在生产命名空间导出类中核对顺序 = Fabric 附件(586) → 原版 `readCustomData`(589) → 上游数据读取(648–666) → 本模组回填(672) → return，确认"上游与附件数据都已恢复" | 导出类反汇编 |
| rc.31 | **状态映射死分支**：`data != null && getMode() == null` 恒为假 → 某状态永远不会被记录 | 规则函数 + 反例测试 |
| rc.31 | **逐实体遍历误写**：每 tick 全量遍历使用"会创建"的访问器，给无关生物写入并持久化空记录 | 实机日志 `entries: []` → 改为只读访问器 |
| rc.33 | **驯服入口从未触发**：目标方法先写归属(偏移2)后写标志(偏移7)，旧锚点在其间检查标志必然为假 | 原版字节码调用序列测试 + 旧锚点复现失败 |
| rc.35/37 | **结算误判**：上游停止事件不携带原因 → 改为观察真实代码路径；**手动推进阶段**不再算自然完成 | 实机日志中连续 4 次"下一阶段" + 生产字节码调用点核对 |
| rc.36/38 | 中途脱离的额外数值惩罚；**脱离成功后不再发奖励物**（共享判定 `isMatingGainReason`） | 规则/接线测试 |
| rc.39 | **接入上游自带的 A/D 提示条与真实进度**：按攻击型流程启动（上游才会建立进度对象） | 混入应用日志 + `completeAttackEscape` 偏移 5 调用我方处理器 |

### A.3 验证分层（**严禁混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 246 用例全绿（规则、会话状态机、接线/字节码契约、配置语言审计） |
| 发布包结构 | 通过：注入目标、清单、`fabric.mod.json`、混入注解均反汇编核对 |
| 服务端启动（上游 + 完整模组组合 + 默认内容包 368 个定义） | 通过：`Done`、零注入失败、适配器 `enabled and verified` |
| 服务端行为 | 通过：既有数据回填、持久化、幂等 |
| **客户端/实机行为** | **基本未验证**（唯一实测来自一轮 rc.36 日志；rc.34–rc.39 未实机回归） |

### A.4 由实测日志推动的修正（rc.36 那轮）

日志事实：拒绝（−15→70）→ 强制启动流程（`forced=true`）→ 连续 4 次"请求下一阶段" → 被判定为自然完成（+10→80）。
→ 结论：那次结束是**被调试键推过最后阶段**，被误判为自然完成。**rc.37 起**记为 `evidence=MANUAL_ADVANCE`，
**不结算、不惩罚**，日志可逐条核对。

---

## B. 难以独立完成、需要协助的事项

### B1（最高优先）实机验证只能在客户端做

- **卡点**：关键行为（A/D 提示条是否出现、进度是否推进、成功后上游的副作用、按键模式、驯服流程）都需要真实客户端。
  本机只能启动专用服务端（没有玩家实体 → 任何"玩家+生物"流程都无法启动）。
- **需要协助**：用客户端跑下列场景，按**实例**回传日志行 + 关系值数值 + 是否收到奖励物：
  1. **A/D 提示条（新）**：拒绝 → 强制启动 → 期望出现 A/D 条且连点推进；成功后期望
     `NON reported a successful struggle for mating <id>` → `settled as NONE … escapedByPlayer=true` →
     `configuredEscapeLoss=25 applied=-25`，**无奖励物**；若未出现，回传
     `could not start the forced mating as an attack escape (...)` 那行。
  2. **早期脱离 / 较晚脱离 / 自然结束** 三条，分别记录数值变化与奖励物。
  3. **手动推进阶段**：期望 `evidence=MANUAL_ADVANCE`，数值不变、不惩罚、不发奖励物。
  4. **关闭 `nonStruggleScreen`** 后再试：期望回到"普通流程 + 本模组脱离键"。
- **验收标准**：日志行与预期标记完全对应；任何不符把原文回传定位。

### B2 上游"攻击型流程"载荷的语义需要外部确认

- **卡点**：要让 A/D 条**有反应**，必须让上游为这次流程建立进度对象；其建立条件是
  `onAnimationStarted` 收到**非空 `PlayerAttack` 且 `victim ∈ actors`**（首句 `ifnull → return`）。
- **我方证据**：`sendEscapeControlState` 局部变量表 `isAttack / isDefeatAnimation / isStuckAnimation / escapable`；
  `escapable = isAttack && isEscapableStage(stage)`；`resolveEscapeSettings` 首句 `!isAttack → none()`；
  `Contest`/`PlayerAttack` 字段均为 `(victimUuid, finishOwnerUuid, initiatorUuid, attackerUuids)`。
- **不确定**：上游自己的"生物攻击玩家"流程到底怎么填这几个字段（尤其 `finishOwnerUuid` 是否必须为 null）。
  该构造位于包私有代码，我只能从 PvP 路径反推；当前用的是
  `(victim=玩家, finishOwner=null, initiator=生物, attackers={生物})`。
- **需要协助**：确认正确形状（或向上游作者确认）；给到我后改一处常量级代码即可（已隔离在单个方法内）。

### B3 `StopReason` 读不到——需要上游或更好的适配思路

- **卡点**：`ServerAnimationController$StopReason` 是**包私有**枚举，只作为参数传给
  `stopInstance(World, UUID, boolean, StopReason)`；以完整描述符注入被 Mixin 拒（`Scanned 0 target(s)`，
  映射写法与中间名写法都试过，`require=1` 强制暴露）。
- **现状**：自动完成 = tick 路径内联停止；其余一律不结算。
- **需要协助**：评估更可靠的读取方式（例如在 `fireStopEvent` 处读 snapshot、或读内部
  `StoppedContext.snapshot()`），或推进上游请求（`StoppedContext` 暴露 `StopReason`）。

### B4 产品政策需要拍板（不应由实现者单方面决定）

1. **未脱离、自然结束的"强制启动"流程是否应增加关系值？** 现行：会（+`affinityPerMating`，无主动加成）。
2. **用调试键推完流程**：现行"不结算、不惩罚"。是否应视为**中途脱离**并施加惩罚？（可堵住"跳过以逃避惩罚"）
3. **`nonStruggleScreen` 默认开**是否接受它带来的上游副作用（成功后可能有伤害、短暂无敌、计入上游统计、
   与其放弃/追击机制联动）？
4. **本模组自建脱离键**是否保留（默认未绑定），还是等上游公开接口后移除？

### B5 长期运行的状态/内存边界（代码级保证已有，缺长时实测）

- 已有：会话终态集合上限 1024、停止证据上限 2048、投射物按实例身份释放且移除钩子幂等、跟随驾驶记录上限 512。
- **需要协助**：多小时会话中每隔一段时间执行 `/nonaffinity stampcounts`，回传 `melee/projectile/projectileUuidIndex` 序列。

### B6 客户端兼容矩阵（我只有服务端）

- 仅依赖组（上游 + Fabric API）与完整组合两组，我都只测了**服务端**；客户端专属模组在服务端被跳过，
  其客户端共存**未验证**。
- **需要协助**：客户端两组各跑一次"能启动、进世界、退出重进、日志无注入报错"。

### B7 发布门槛的判断

- 我倾向：先拿到 B1（实机）+ B2（载荷语义）的结论，再谈正式版。
- 当前对上游内部实现的依赖属于"版本受限 + 可回退 + 失败大声报告"；**是否可接受为正式版发布条件，需要审查者明确表态**。
  若否决，我按 B3/B4 退回到"仅自建脱离键、不依赖上游内部"的形态。

---

## C. 现成核对命令（可直接执行）

```bash
# 1) 本模组全部结算/适配器日志
grep -E "natural-completion adapter|stop-path adapter|mating settlement|settled as NONE|mating settled for|completed with|is breaking free|NON reported a successful struggle|never observed running|ignoring duplicate" logs/latest.log

# 2) 上游阶段推进/脱离痕迹
grep -E "next_stage_requested|escape|struggle" logs/latest.log

# 3) 回退与注入报错
grep -E "could not start the forced mating as an attack escape|InvalidInjection|Mixin apply for mod noncreatureaffinity" logs/latest.log
```

**字段含义**：`evidence=NATURAL_END`（tick 自动完成）/ `MANUAL_ADVANCE`（手动推进）/
`INTERRUPTED`（其他停止）/ `UNKNOWN`（适配器未生效）；`escapedByPlayer` 表示玩家主动脱离；
`applied=` 为**实际**变化量（受上下限夹取）。
（以上 grep 串是程序里的**字面标识符**，必须保持原样才能匹配日志。）
