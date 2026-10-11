# 1.0.0-rc.9 当前状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.9，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc8.md`(rc.8)、`STATUS-rc7.md`(rc.7)、`STATUS-rc6.md`(**rc.6 不可交付**，递归缺陷)。

## 当前产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.9+1.21.11.jar` |
| 体积 | 226.7 KB |
| SHA256 | `F79685981982828A37F30205289E8B85B39A4D9E6DD4DADCD6709F534C0F6694` |
| 自动测试 | **89 个 JUnit 用例，0 失败** |
| 语言审计 | **89 个玩家可见键，0 缺失** |
| 游戏验证 | **无**（未启动游戏） |

## 本轮完成

### 1. 修复 `apply` 停滞分支丢弃新目标（评审指出）

新停滞分支在**旧交战停滞**时直接 `return` —— 但能走到那里说明 `choose()` 已经为
**当前威胁**选出了合法动作，`return` **把本轮的新目标丢掉了**。

**修复**：停滞只结束**陈旧的那场交战**，**不再提前返回**，流程继续处理本轮选出的新目标。
**区分「继续旧目标」与「切换到新的合法威胁」，避免旧战斗状态阻挡新威胁响应。**

### 2. P1-b 规则进入生产共用的状态

`EngagementState` 新增（**生产与测试用同一套方法**，未另写筛选算法）：

| 成员 | 作用 |
|---|---|
| `suppressedTarget` / `suppressedUntilTick` / `suppressedSequence` | 被放弃的目标在窗口内不可重选；记录**事件水位** |
| `returning` / `returningUntilTick` | 召回后**返回途中不接任何新战**（与抑制不同：抑制针对特定目标） |
| `canEngage(candidate, now)` | **唯一规则**：返回中一律不可接战；否则看是否被抑制 |
| `end()` | **不清除**抑制与返回状态 —— 它们存在的意义就是**比那场战斗活得更久** |

**接线**：
- `onRecalled` 在清除前**保存被放弃目标与事件水位**，再 `suppress` + `beginReturning`
- **候选选择**统一经过 `canEngage` —— **新序号无法绕过返回状态与抑制窗口**

新增配置：`companionReleaseSuppressTicks`(200)、`companionReturnTicks`(60)。

## 第一层测试覆盖（评审点名的 6 项，逐条对应）

| # | 场景 | 测试 |
|---|---|---|
| 1 | 召回后旧事件不能立即重选原目标 | `recallSuppressesTheOldTarget` |
| 2 | 同目标持续扫描即使产生新序号，也不能绕过距离与返回状态 | `freshEvidenceCannotBypassTheRules` |
| 3 | A 的召回不修改共享事件，B 仍可正常响应 | `recallOfOneCompanionLeavesTheOtherWorking` |
| 4 | 附近新威胁可响应；原目标窗口过后不被永久封禁 | `newThreatAnswerableAndOldTargetRecovers` |
| 5 | 停滞放弃后不会被持续事件每轮重启 | `stallDoesNotRestartFromContinuousEvents` |
| 6 | 召回失败不进入召回成功状态，也不误清其他伙伴 | `failedRecallDoesNotEnterTheRecallSuccessState` |

另加 `suppressionIsScopedToItsTargetAndWindow`（抑制只作用于该目标、窗口到期自动失效）。

**89 用例 / 0 失败。**

**说明**：第 3 项（两伙伴互不干扰）按你的意见在**状态层**验证 ——
两个独立 `EngagementState` + 一份共享事件列表，**不需要真实世界**。

## 第二层（生产接线测试）：**未完成**

「成功召回确实调用状态转换、失败不调用」以及「候选选择确实经过上述规则」
**尚未有测试**。这一层需要小型适配接口或反射直调，**本轮未做**。

## 仍未修复

- **迟到命中不验证代次**：同目标重开战后延迟投射物仍会续期（需发起时标记代次）
- **P2-1**：companionWaitRadius / companionWanderRadius 界面可编辑但**行为已失效**
- **P2-2**：语言审计用手工档位列表，**不是通用动态键审计**
- **游泳门控**：CompanionMovementGoal 与原版 SwimGoal 同优先级 —— 需实机确认

## 自动测试与游戏验收的分界

- **自动测试已通过**：89 个 JUnit 用例；语言审计 89 键 0 缺失。
- **尚未完成的游戏验收**：**全部**。本轮**没有启动过客户端**。

## 本轮经验

两阶段脚本又拦下一次半套改动（`EngagementState` 的锚点假设了单行注释、实际跨多行）。
**先读实际文件再写锚点**，这一点已连续几轮证明必要。
