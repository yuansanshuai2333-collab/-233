# 1.0.0-rc.11 当前状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.11，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc10.md` … `STATUS-rc6.md`(**不可交付**)。

## 当前产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.11+1.21.11.jar` |
| 体积 | 227.7 KB |
| SHA256 | `D0EBD13F4050F1523338BE717C1E8DC7EC79B158276A46BA89F3AB6737710B55` |
| 自动测试 | **104 个 JUnit 用例，0 失败** |
| 语言审计 | **89 个玩家可见键，0 缺失** |
| 游戏验证 | **无**（未启动游戏） |

## 本轮修正

### 1. 跟随范围按模式应用（行为修正）

原来**所有模式**都套用主人范围限制，导致：

| 模式 | 错误行为 | 现在 |
|---|---|---|
| **WAIT** | 主人走远或离线 → **失去原地自卫能力** | **不受范围限制**，仍能原地自卫 |
| **WANDER** | 被强加跟随范围 | **无范围约束** |
| FOLLOW / ATTACK | 正确 | 仍按主人范围 |

### 2. 分开计算三个距离（行为修正）

原来**只看伙伴—主人距离**。结果：**伙伴被召回身边后，会立刻重新追击一个仍远在他处的旧目标** ——
因为没人问过目标自己在哪。

现在 `canEngageUnderMode` 同时检查：

- **伙伴—主人**（同伴自己在范围内）
- **目标—主人**（目标也得在范围内）
- 伙伴—目标（战斗延续时由既有逻辑处理）

### 3. 重新接战比退出更严格（防边界反复）

**退出**界限是召回距离；**开始新战斗**要求比它更靠内（`companionReengageMargin`，默认 1 格）。
否则正好卡在边界上的目标会**每轮被丢弃又重拾**。

### 4. 事件序号限制按被放弃目标处理

`sequence <= suppressedSequence` 现在**只在候选等于被抑制目标时**生效 ——
**不会封锁其他目标**（即使它们的事件序号更小）。**共享事件仍未被清空**。

## 生产流程测试（8 项新增，逐条对应你点名的场景）

`ModeRangeRulesTest`：

| 测试 | 覆盖 |
|---|---|
| `recalledCompanionDoesNotResumeADistantChase` | **伙伴距主人 2 格、旧目标距主人 14 格**、抑制到期、扫描产生新事件 → **仍不重启越界追击** |
| `waitDefendsInPlaceWithoutOwnerRange` | **WAIT 在主人远离或离线时可原地自卫**；同样条件 FOLLOW 被拒（证明模式真的起作用） |
| `wanderHasNoOwnerRange` | WANDER 无范围约束 |
| `reentryIsStricterThanExit` | 滞回：9.5 格拒绝、5 格允许 |
| `suppressionIsScopedToItsOwnTarget` | **抑制 A 不影响 B，即使 B 的事件序号更小** |
| `recallDoesNotDisturbTheOtherCompanion` | 两伙伴共享一份事件列表，A 召回**不改 B 的状态或事件** |
| `stalledFightIsNotRestartedByFacts` | 停滞后**本轮及下一轮**都不因新事实重开 |
| `failedRecallProducesNoSuppression` | 召回失败**不执行成功处理、不产生虚假抑制** |

**104 用例 / 0 失败。**

测试全部走 `end()` / `applyRecallResult(...)` / `canEngageUnderMode(...)` 的**生产顺序**，
**没有在生产会跳过的分支上手工调用 `suppress` 来制造通过**。

## 未完成

- **薄适配接口 + 测试替身**：你要求把**实体读取与瞬移执行**隔离到适配层，
  用替身验证生产**调用链**（成功/失败回调确实被调用）。
  **本轮未做** —— 目前的测试覆盖**规则**，尚未覆盖**调用链**。
- **停滞退出时保存并抑制刚放弃的目标**：部分 —— `end()` 已保存 `lastAbandoned`，
  但停滞分支**尚未调用 `applyRecallResult` 之外的抑制路径**，也未重新验证本轮 Selection。
- **迟到命中不验证代次**
- **P2-1**：companionWaitRadius / companionWanderRadius 界面可编辑但**行为已失效**
- **P2-2**：语言审计用手工档位列表
- **游泳门控**：CompanionMovementGoal 与原版 SwimGoal 同优先级 —— 需实机确认

## 自动测试与游戏验收的分界

- **自动测试已通过**：104 个 JUnit 用例；语言审计 89 键 0 缺失。
  覆盖**规则层**：模式化范围、三距离、滞回、抑制作用域、水位。
- **尚未完成的游戏验收**：**全部**。本轮**没有启动过客户端**。
  新增的 `endEngagement` 反射回归测试仍在运行，防递归复发。

## 已知的实现约束

`canEngageUnderMode` 接收 `ownerRange` 布尔量而非模式枚举，因为 `EngagementState` 位于
`policy` 包、**不依赖 `data` 包的 `CompanionMode`**。调用方（控制器）负责按模式换算，
该换算在候选筛选处**只有一处**。

若后续新增模式，**必须同步这一处换算** —— 这是当前的已知单点。
