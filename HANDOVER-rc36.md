# 交接说明 · 1.0.0-rc.36

> 本批：**挣脱强行交流 → 额外扣好感（新配置项）**，以及 **挣扎成功后不再给友情之证**。
> 同时记录：**NON 自带的「挣扎」机制为什么无法用于强行交流**（有字节码依据，含上游请求建议）。
> **版本 1.0.0-rc.36，候选状态，未发布正式 1.0.0。**

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.36+1.21.11.jar` |
| 体积 / SHA256 | 293893 字节 / `11A8D8A367F99D4670E1DB6AD549E6332990D1A8F280EB718AE57C005E72C545` |
| 自动测试 | **241 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 新增配置 | `affinityLossOnEscape`（默认 25，范围 0–100；0 = 关闭） |

## 2. 关于你这一轮的日志（**我这边读不到**）

我在工作区里**找不到你本次实测的日志**：实例 `1.21.11-Fabric 0.19.5/logs` 最新仍是 9-18，
项目 `run/logs` 最新为今天 02:00（早于 rc.33/34/35 构建），最近 12 小时内新写入的只有我自己的隔离测试服务端日志。
**请把该次会话的 `logs/latest.log`（或它的完整路径）给我**，我按下面这些关键字逐条核对：

```
grep -E "request rejected by|broke out of|settled as NONE|mating settled for|completed with|ignoring duplicate|never observed running|natural-completion adapter" logs/latest.log
```

我要确认的点：① 拒绝时是否只扣一次（`configuredPenalty/applied`）；② 挣脱时是否出现
`settled as NONE … escapedByPlayer=true`；③ 正常完成是否出现 `completed … -> playerInitiated=…` 且同一
`instanceId` 只结算一次；④ 是否出现过 `evidence=UNKNOWN`（说明适配器没生效，那样一切奖励都会被拒）。

## 3. 关于「强行交流也要能用 NON 的挣扎」

**结论：这一版无法从公开 API 打开，原因在 NON 侧，不在本模组。** 依据（字节码实读）：

- 客户端是否能出现挣扎（连点/挣脱）由 `NeedsOfNature.resolveEscapeSettings(...)` 决定；
  它在**第一个条件**不满足时直接返回 `EscapeSettings.none()`：
  `if (!isAttackSnapshot(snapshot) || snapshot == null || player == null) return none();`
- 而 `isAttackSnapshot(snapshot)` = `getNonSessionData(snapshot.startContext()).isAttack()`，
  即**只有以「攻击（attack）」起始上下文启动的动画**才会下发挣扎控制。
- 公开 API 的 `NonAnimationApi.StartOptions` 字段只有
  `damageBehavior / ignoreAttackers / positionAnchor / requester / metadata / forceDebugChat / allowMultiplePlayers`
  ——**没有任何可以声明「可挣脱/攻击上下文」的入口**；攻击上下文由**包私有**的
  `NonPlayerAttackSystem.buildPlayerAttackContext(...)` 构造。

所以**本模组既不能声明、也不能安全伪造**这个上下文：强行用内部攻击上下文启动一段「交配」动画，
会让 NON 把这段动画当成攻击交互处理（受害者判定、PvP 相关逻辑、defeated 尾巴等），风险远大于收益。

**建议（上游请求）**：向 NON 请求以下任一，本模组随后即可接入：
1. `StoppedContext` 暴露 `StopReason`（也顺带解决「完成/中断判定」更直接）；或
2. `StartOptions` 增加 `escapable` / `escapeProfile` 选项（让 API 启动的动画也能下发挣扎控制）。

**在你装上支持该能力的 NON 版本之前，本模组的挣脱方式仍是「攻击生物」**：
强行交流是以 `STOP_ON_DAMAGE` 且 `ignoreAttackers = false` 启动的，攻击即可中断；
开始时会按 `forcedMatingEscapable` 发送 `escape_hint` 提示。
**如实说明**：`forcedMatingEscapable` 目前只控制这条提示是否发送，**不**控制能否挣脱
（能否挣脱由 NON 的伤害行为决定）——这一点已在代码注释中写明，避免再次出现「开关没接线」的误解。

## 4. 本批实现的（你的两个要求）

### 4.1 挣脱成功 → 额外扣好感

- 新增配置 `affinityLossOnEscape`（默认 **25**，比拒绝扣分默认 15 更多），范围 0–100，**0 = 关闭**（恢复旧行为）。
- 只在**三个条件同时成立**时扣：① 是**玩家自己**挣脱（来自攻击路径，记在会话里，与「演员死亡/卸载」等
  非玩家原因的打断分开）；② 该次是**强制**交流（强制 = 拒绝之后生物强行发起）；③ 配置值 > 0。
  **自愿交流中途放弃不额外扣分。**
- **每个动画实例最多扣一次**：结算取走会话即终态；重复停止回调、重复挣脱标记都不会二次扣。
- 与拒绝扣分的关系：拒绝扣分仍只在拒绝那一刻发生一次（`recordRejection`），挣脱扣分是**另一笔、独立配置**的
  代价；两者不会因为同一个事件重复计费。
- 日志：`forced mating escaped by <玩家> + <生物>: configuredEscapeLoss=25 applied=-25 affinity=<值>`
  （`applied` 是**实际**扣减，撞到下限时会小于配置值）。

### 4.2 挣扎成功不给友情之证

友情之证只在**正向且原因为「完成的交流」**时、且好感已到上限时发放。现在把这条门槛提成共享判定
`AffinityService.isMatingGainReason(reason)`，而挣脱走的是 `escape` 原因 + **负向**变化，
因此**结构上不可能**拿到友情之证（并用单元测试锁定：`escape`/`rejected`/`peak`/空值都不算完成交流）。
日志也会写明 `(no reward, no friendship token)`。

## 5. 验证分层（**不混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 241 用例 / 0 失败（含挣脱扣分规则、只扣一次、只对强制且玩家挣脱生效、友情之证门槛、配置语言审计） |
| 发布包接线（字节码） | `NonIntegration` 引用 `escapeLoss` 与 `recordForcedEscape`、用 `markPlayerEscape`；**不含** `recordRejection` |
| 服务端启动（NON + 你的模组组合 + 默认内容包） | `Done (0.874s)`，适配器 `enabled and verified`，零注入失败 |
| 既有行为回归（服务端） | 旧宠物回填 坐下→`WAIT`、`FOLLOW` 正常 |
| **实机：挣脱扣分 / 无友情之证 / 挣扎是否出现** | **需人工，未完成** |

**需你实测（记录好感与物品）**：
1. 让友好生物求欢 → **拒绝**（记下好感，应只扣一次 `affinityLossOnReject`）→ 生物强行发起 →
   **攻击挣脱**：期望再扣 `affinityLossOnEscape`（默认 25），且**背包没有多出友情之证**；
   日志应出现 `forced mating escaped by …` 与 `settled as NONE … escapedByPlayer=true`。
2. **自愿交流中途挣脱**：期望**不**额外扣分，只失去本次奖励，也没有友情之证。
3. **正常自愿完成**（不挣脱）：期望仍发奖，好感到上限时**照旧**给友情之证（这条不能被本批改坏）。
4. 顺手确认：强行交流里**仍然没有** NON 的挣扎界面（这是 NON 侧限制，见 §3），
   攻击可以中断，提示信息正常出现。

客户端启动与兼容、F 键四模式、无动画动物按 F、驯马驯狗实机（rc.33 的结论**未在 rc.34/35/36 复测**）仍待验收。
