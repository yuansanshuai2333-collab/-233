# 1.0.0-rc.10 当前状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.10，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc9.md`、`STATUS-rc8.md`、`STATUS-rc7.md`、`STATUS-rc6.md`(**不可交付**，递归缺陷)。

## 当前产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.10+1.21.11.jar` |
| 体积 | 227.2 KB |
| SHA256 | `1AE154D5B337FF6E885F83A1CC453869322791633AAB42E93EBD7C760F740108` |
| 自动测试 | **96 个 JUnit 用例，0 失败** |
| 语言审计 | **89 个玩家可见键，0 缺失** |
| 游戏验证 | **无**（未启动游戏） |

## 本轮修正的四个缺陷

### a) 执行顺序：召回时 `engagedTarget` 可能已为空

**实际顺序可能是**：控制器先因**越界**结束交战 → 跟随服务**随后**召回。
此时 `onRecalled` 读取 `engagedTarget` **已是 null** → **抑制窗口根本不会建立** → 循环依旧。

**修复**：`end()` 在清除**之前**保留 `lastAbandoned` 与 `lastAbandonedSequence`；
`applyRecallResult` 使用**保留值**，不读实时目标。

### b) 已回到附近仍固定屏蔽新威胁

原来召回后**无条件**屏蔽一段时间。伙伴若**已经安全回到主人身边**，
旁边站着真实威胁却**不响应**。

**修复**：`onRecalled` 计算实际距离，**已在范围内则返回窗口为 0**；
`canEngage` 在 returning 状态下若**已回到范围内则立即解除**。

### c) 抑制窗口到期后放行远距离追击

**修复**：`canEngage` 现在要求**四个条件同时成立**：
**目标资格**（调用方已校验）、**跟随范围**（距离 > 召回距离一律不接战）、
**事件水位**（`sequence <= suppressedSequence` 直接拒绝）、**恢复条件**（返回中且仍在范围外则拒绝）。

**`suppressedSequence` 现在真的被读取** —— 不再只是保存。

### d) 停滞退出丢弃本轮 Selection（上一批已修，本批纳入测试）

停滞只结束**陈旧的那场交战**，**不再提前返回**，本轮选出的**新合法威胁继续执行**。

## 第一层测试：96 用例，逐条对应

`RecallResultRulesTest`（新增 7 项）：

| 测试 | 证明 |
|---|---|
| `recallAfterFightEndedStillSuppresses` | **实际执行顺序**：先 end、后召回，抑制仍建立 |
| `nearbyThreatAnsweredWhenAlreadyBack` | 已回到附近时**不必等计时器**即可响应新威胁 |
| `expiryDoesNotRestartDistantChase` | **窗口到期 + 距离超范围 → 仍不接战**；回到范围后才可用 |
| `staleFactsCannotUndoTheRelease` | **水位被真实读取**，旧事实无法撤销释放 |
| `failedRecallChangesNothing` | 召回失败**不执行任何成功转换** |
| `recallOfOneDoesNotDisturbTheOther` | A 召回不影响 B |
| `continuousScanCannotRestartDistantChase` | **持续扫描 10 轮都无法重启越界追击** |

**测试不在生产会漏掉的分支上手工调用 `suppress` 来制造通过** ——
全部走 `end()` → `applyRecallResult(...)` → `canEngage(...)` 的**生产顺序**。

## 仍未完成

- **第二层接线测试**：成功/失败回调经薄适配层的测试替身验证 —— **未做**
- **迟到命中不验证代次**：同目标重开战后延迟投射物仍会续期
- **P2-1**：companionWaitRadius / companionWanderRadius 界面可编辑但**行为已失效**
- **P2-2**：语言审计用手工档位列表，**不是通用动态键审计**
- **游泳门控**：CompanionMovementGoal 与原版 SwimGoal 同优先级 —— 需实机确认

## 自动测试与游戏验收的分界

- **自动测试已通过**：96 个 JUnit 用例；语言审计 89 键 0 缺失。
- **尚未完成的游戏验收**：**全部**。本轮**没有启动过客户端**。

## 说明

`onRecalled` 现在只在**召回真正成功**时被调用（失败不进该路径），
成功转换全部委托给 `applyRecallResult`，与测试调用的是**同一个方法**。
