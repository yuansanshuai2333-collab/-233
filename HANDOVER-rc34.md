# 交接说明 · 1.0.0-rc.34

> **本版本已作废，不要使用。** 审查未通过：完成判定使用了「时间估计」（最后阶段是否跑够预期窗口），
> 无法区分正常完成与较晚挣脱，放宽阈值会把挣脱一起放进来。**请改用 `HANDOVER-rc35.md` 与 rc.35 包**，
> 其中已删除全部时间估计，改为观察真实停止路径，并如实报告「读不到原因时不奖励」的限制。
> 保留此文是为了记录当时的判断与实测过程。

> 本批：**修复「强行发起深入交流 → 玩家挣脱 → 仍然加好感」**。
> **版本 1.0.0-rc.34，候选状态，未发布正式 1.0.0。**
> rc.26–rc.33 为坏包或中间包，**rc.33 的驯马修复继续有效**，请使用 rc.34。

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.34+1.21.11.jar` |
| 体积 / SHA256 | 286064 字节 / `614037D2BFAEC9B84CD6DC38E773503AD13BF06483783A4A2D1BCDC358EB1D36` |
| 自动测试 | **233 用例 / 0 失败 / 0 错误 / 32 个测试类**（新增 14 个结算用例） |
| 元数据 | `1.0.0-rc.34+1.21.11`；`fabric-api>=0.141.6`；10 个 mixin，`defaultRequire: 1` |

## 2. 关于用户日志（如实说明）

**我在工作区内找不到你本次会话的日志**：实例 `1.21.11-Fabric 0.19.5/logs` 里最新的是 9-18 的记录，
没有任何包含本次复现过程的日志文件；项目 `run/logs` 最新为 02:00。因此**你的具体复现路径仍然未知**，
本文与 rc.35 说明中的结论**不是**从你的会话日志读出的，也没有据此推断你按了哪个键；
它们来自：

1. **本模组代码审计**（下方 4 个缺口全部在代码中定位到）；
2. **NeedsOfNature 1.5.11.21.11 的字节码与公开 API 实读**（下面列出的类型/方法均为实读结果，非推测）；
3. 发布包上的启动与既有路径回归（见 §6）。

如果你能提供该次会话的 `logs/latest.log`，我可以把日志里的时间线补进本报告。

## 3. 根因（已在你指出的 4 处缺口上复核确认）

**关键事实：NON 的公开停止事件不携带原因。**
`NonAnimationEvents$StoppedContext` 只有 `world / instanceId / animationId / actorUuids / actorKeys / stages`。
原因（`NATURAL_END` / `DAMAGE_STOP` / `EXTERNAL_STOP` / `ACTOR_UNAVAILABLE` / `PLACE_FAILED` / …）
只存在于 NON 内部 `ServerAnimationController$StopReason`，**没有暴露给 API**。
所以「收到 STOPPED」≠「交流完成」。旧代码正是这样假设的：

| 缺口 | 代码位置（rc.33） | 后果 |
|---|---|---|
| 1. STOPPED 未看原因/未验完成 | `NonIntegration` 旧 STOPPED 回调 | 未命中 ESCAPED 就 `finishMating` → 加好感 |
| 2. 无 ACTIVE 记录直接按自主结算 | 旧 `settleAutonomousMating` | 生物**自主**强行发起的动画挣脱后照样加好感（你遇到的正是这条） |
| 3. `markEscaped` 只遍历 ACTIVE | 旧 `markEscaped` | 自主动画的挣脱标记不到 |
| 4. 挣脱标记写在伤害回调 | `CompanionDamageEvents` ALLOW_DAMAGE | NON 对「攻击挣脱」有**延迟结算**（内部 `PENDING_ATTACK_ESCAPE_RESOLUTIONS`、`ATTACK_ESCAPE_DAMAGE_DELAY_TICKS`），STOPPED 可能先到 → 标记来不及 |

另外查到的、对本修复有用的 NON 内部事实（字节码实读）：

- `advanceStage(...)`：当 `stageIndex + 1 >= stages.size()`（即处于**最后一个阶段**）时调用
  `stopInstance(..., NATURAL_END)`。**自然结束只发生在最后一个阶段跑够时长之后**。
- `AnimationStageTiming.resolveDurationTicks(...)`：非循环阶段 = `max(1, ceil(cycle / speed))`；
  循环阶段的窗口来自**按动画配置的包元数据**（`AnimationGameplayMetadata.stageSeconds/peakStage`），
  **公开 API 无法读取**。
- 攻击挣脱路径：`NonAttackSystem` / `NonPlayerAttackSystem`（含 `Contest`、`syncEscapeControl`、
  `applyPlayerAttributedEscapeDamage`），**均为包私有内部实现，公开 API 没有对应事件**。

**结论：不能靠停止原因判定，只能靠「可观察的完成证据」。** 这就是本批的实现方式。

## 4. 修复设计

**新增纯逻辑（可脱离游戏测试）**

- `MatingSettlementRules`：结算判定。
  - `decide(Facts)` → `NONE` / `REWARD_VOLUNTARY` / `REWARD_PLAYER_INITIATED`，**没有惩罚分支**；
  - 完成条件 = 未被打断 **且** 观测到进入最后阶段 **且** 最后阶段跑够预期窗口（容差 2 tick）；
  - 任何「无法确认」一律 `NONE`：**未确认的停止事件不自动发放完成奖励**；
  - `expectedStageTicks(...)`：非循环阶段 = `max(1, ceil(cycle/speed))`（与 NON 规则一致）；
    循环阶段 = 用**同一动画上一个阶段实测到的窗口**；单阶段循环时退化为保守下限 200 tick。
- `MatingSessions`：按**实例**保存状态（谁发起、是否强制、开始 tick、最后阶段、上一阶段时长、是否被打断），
  以及**终态集合**：
  - `take()` 取出即记终态 → **重复停止回调不会二次结算**；
  - 未知实例也记终态 → **不会再落入「无记录=自主完成」补发奖励**；
  - `markInterrupted(player, creature)` 标记**该组合的全部在跑动画**（含非本模组发起），
    且**不再依赖伤害回调的时序**。

**接线（`NonIntegration`）**

- 新注册 `STARTED`（每一个玩家+生物动画都登记）与 `STAGE_CHANGED`（阶段进度）；
- `STOPPED` 只负责「取实例 → 交给规则判定 → 奖励或拒绝」，并打印**判定依据**；
- 奖励仍走唯一入口 `AffinityService.recordMating`；**拒绝不扣分**（拒绝扣分只属于拒绝路径）；
- 删除了 `settleAutonomousMating`、`ACTIVE`、`ESCAPED`、`SETTLED_INSTANCES` 四套旧结构。

**规则符合你的四条要求**

| 要求 | 实现 | 测试 |
|---|---|---|
| 明确拒绝按 `affinityLossOnReject` 扣（0 不扣） | 仍在 `AffinityService.recordRejection`，**唯一扣分点** | 保留原逻辑 + 新增日志显示 `configuredPenalty / applied / affinity` |
| 挣脱/中断不得获得正向好感或成功奖励 | `decide` 遇 `interrupted` 直接 `NONE`（连 `matingCount` 也不加） | `MatingSettlementTest` 场景 1/2 |
| 同一次请求已扣过拒绝，挣脱不重复扣 | 结算路径**结构上无法**触碰 `recordRejection` | 字节码断言：`NonIntegration` 不含 `recordRejection` 引用；`Decision` 无惩罚常量 |
| 正常自愿完成仍按原规则结算 | 完成 → `playerInitiated` 决定是否加加成，走原 `recordMating` | 场景 3（含「只结算一次」） |

**其他奖励入口核查**：全量 grep `addAffinity` / `recordMating` / `recordPeak` 后确认——
正向入口只有 `recordMating` 一处；`recordPeak`（动画高峰加好感）**当前无任何调用方**，不存在「挣脱前先加好感」的路径。
故本批未改动它，仅在报告中记录。

## 5. 新增日志（用于你复现时判读）

| 时机 | 日志（`logs/latest.log`） |
|---|---|
| 拒绝请求 | `request rejected by <玩家> to <生物>: configuredPenalty=15 applied=-15 affinity=<值> rejections=<次数>` |
| 挣脱标记 | `player <玩家> broke out of N mating animation(s) with <生物>; no affinity for those` |
| 拒绝结算（无奖励） | `mating <动画id> settled as NO_RECORD (<原因>): interrupted=… finalStageSeen=… finalStageTicks=… expectedStageTicks=… startedByUs=… forced=…`，原因 ∈ `escaped_or_rejected / final_stage_never_reached / no_expected_stage_length / final_stage_stopped_early` |
| 正常完成 | `mating <动画id> completed: finalStageTicks=… expectedStageTicks=… startedByUs=… forced=… -> playerInitiated=…` 紧跟 `mating settled for <玩家> + <生物>: playerInitiated=… requested=+N applied=+M affinity=<值> count=<次数>` |
| 未知实例 | `mating <id> stopped but was never observed running; no affinity granted` |
| 重复停止 | `ignoring duplicate stop for mating <id> (already handled)` |

## 6. 已完成的自动验证 vs 需人工实测（**不得混写**）

**已自动验证（本轮实际执行）**

| 项 | 结果 |
|---|---|
| 单元测试 | 233 用例 / 0 失败（含 14 个新结算用例：4 条要求 + 重复停止 + 未知实例 + 目标失效 + 边界） |
| 接线（字节码） | 发布包内 `NonIntegration` 引用 `MatingSettlementRules.decide/expectedStageTicks` 与 `MatingSessions.take`；**不含** `recordRejection`、**不含** `settleAutonomousMating` |
| 发布包服务端启动（NON + 用户模组组合 + 默认内容包 76 个动画定义） | `Done (0.855s)`，`animation integration enabled`，零注入失败 |
| 既有非交流路径回归 | 旧宠物回填仍为坐下→`WAIT`、`FOLLOW`，持久化正常 |

**需人工实测（你有客户端，我这边无法启动真实动画）**——按你给的 5 项：

1. **拒绝 → 强行开始 → 挣脱**：记录拒绝前/后好感（`/nonaffinity info` 或屏幕提示 + 上述日志 `applied`），
   挣脱后再看好感：应**只**有那一次拒绝扣分（`applied=-15`），**没有** `mating settled …` 行，
   也**没有**第二次拒绝扣分。
2. **NON 自主强行开始 → 挣脱**：应出现 `settled as NO_RECORD (escaped_or_rejected …)` 或
   `final_stage_never_reached`，好感不变。
3. **正常自愿完成**：应出现 `completed … -> playerInitiated=true` 与 `settled … applied=+N`，
   且**只出现一次**（同一 instance id 不会出现第二条 `settled`）。
4. **重复停止 / 目标失效**：日志应为 `ignoring duplicate stop` 或
   `stopped but was never observed running`，好感不变。
5. 记录每一次的**实例 ID、判定依据（finalStageTicks/expectedStageTicks）、结算增量**。

> **一个需要你重点确认的取舍**：为避免「无法确认也发奖励」，本批采用**严格证据**策略。
> 若你发现**正常完成也拿不到好感**，日志会明确写出缺哪一项证据
> （`no_expected_stage_length` / `final_stage_stopped_early` 等），把它发我即可按实测数据精确放宽，
> 而不是靠猜。默认内容包的两个阶段都是 `loop`，脚本走的是「上一阶段实测窗口」这条路径，正常应当通过。

## 7. 未验证范围（继续有效）

客户端启动与兼容、F 键四模式行为、无动画动物按 F 的提示、骨头驯狼/驯马实机（rc.33 起训马入口已修但实机仍未复测）、
rc.25 遗留的近战/远程续期与双伙伴互不干扰等，均仍需人工实测。
