# 交接说明 · 1.0.0-rc.39

> 本批：**强行交流接入 NON 官方 A/D 挣扎界面 + 真实逃脱进度**（新配置 `nonStruggleScreen`，默认开启）。
> **版本 1.0.0-rc.39，候选状态，未发布正式 1.0.0。**

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.39+1.21.11.jar` |
| 体积 / SHA256 | 299763 字节 / `B1DC64A56D4AB8BDA2E4DAA56C3464F646ADE7CC44DE4FA43FC89C87B8C883FF` |
| 自动测试 | **246 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 新增配置 | `nonStruggleScreen`（默认 **开**；关闭则退回"只有本模组挣脱键"） |

## 2. 为什么必须"按攻击交互启动"（依据）

- 逃脱控制只在**攻击实例**上下发：`sendEscapeControlState` 中
  `escapable = isAttack && isEscapableStage(stage)`，`resolveEscapeSettings` 第一句
  `if (!isAttack || snapshot == null || player == null) return none();`。
- `isAttack = startContext().sessionData().isAttack()`；公开 `NonAnimationApi.StartOptions` 无法声明它。
- 连点进度只存在于 NON 为**攻击实例**建立的 `NonPlayerAttackSystem$Contest`，
  而建立条件是 `onAnimationStarted` 收到非空 `PlayerAttack` 且 `victim ∈ actors`（首句即 `ifnull → return`）。
  → 因此：**要让界面有反应，就必须提供一份"攻击载荷"**，让 NON 认为"生物正在攻击该玩家"。

## 3. 本批实现

1. **强制交流改为按攻击逃脱启动**（`NonIntegration.startEscapableForcedMating`）：
   - 用 `AnimationStartContext.addonMetadata({nonaffinity: forced}).withSessionData(
     NonAnimationSessionData.playerAttack(PlayerAttack(victim=玩家, finishOwner=null, initiator=生物, attackers={生物})))`；
   - 用 `ServerAnimationController.startNowWithContext(...)` 启动**同一个交配动画定义**（阶段由其公开
     `definition.stages()` 映射为内部 `AnimationStageInfo`，道具槽位掩码未映射，仅影响动画期间手持物的显隐）；
   - 结果：NON 自己建立 Contest → 下发 A/D 挣扎条 → **连点推进真实逃脱进度** → 成功时由 NON 自己
     结算逃脱（`completeAttackEscape`）并停止动画。
   - **任何异常/成员缺失都会捕获并回退**到原来的普通启动（日志 WARN），不会让交互丢失或崩服。
2. **新增混入 `NonAttackEscapeMixin`**（仍在同一个版本受限的可选配置里）：
   注入 NON 的 `completeAttackEscape(world, player, instanceId, attackerUuid)` HEAD，
   按**实例 id** 标记"玩家挣脱成功" → 我方结算：**不奖励** + 按 `affinityLossOnEscape`（默认 25）**扣好感** + **不发友情之证**。
   已在**生产环境被改写的字节码**中核对：`completeAttackEscape` 偏移 5 调用我们的处理器。
3. **保留** rc.38 的"挣脱"按键作为兜底（当 `nonStruggleScreen` 关闭、或 NON 版本不支持时使用）；
   保留 rc.37 的判定修正（手动推进=不结算、不扣分）与日志可读性改进。
4. 版本限制与隔离沿用现有插件：未装 NON / 版本不在 `1.5.1*` / 成员缺失 → **整份配置关闭并在启动日志写明后果**；
   本批新增对 `NeedsOfNature.completeAttackEscape` 的存在性核对（缺失 → ERROR 日志 + 状态说明）。

## 4. 需要你知道的副作用（**如实说明**）

开启 `nonStruggleScreen` 后，NON 会把这次强制交流当作**攻击交互**处理，因此可能出现：

- 挣脱成功时按 NON 的配置造成**逃脱伤害**、给予短暂**逃脱后无敌**、写入它的**逃脱统计**；
- 与它自身的 "Give Up/defeated 尾巴/逃脱后追击（post-escape chasers）" 机制产生联动；
- 这些是 NON 的既有行为，**不是我方新增**；若你不希望如此，把 `nonStruggleScreen` 关掉即可
  （强制交流恢复为普通动画，用本模组挣脱键退出）。

我**无法在本机验证真实动画**（需要客户端），所以这一版的"界面能否出现、进度能否推进、逃脱伤害是否合适"
必须由你实测；任何异常都会在日志里留下 `could not start the forced mating as an attack escape (...)` 之类的行。

## 5. 验证分层（**不混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 246 用例 / 0 失败（新增：按实例标记挣扎成功→算挣脱→扣分；攻击启动路径接线断言；逃脱混入注册与引用） |
| 混入运行时核对（生产命名空间导出类） | `NonNaturalEndMixin` → `ServerAnimationController`；`NonAttackEscapeMixin` → `com.nonid.NeedsOfNature`；`completeAttackEscape` 内已调用我们的处理器 |
| 服务端启动（NON + 你的模组组合 + 默认内容包） | `Done (1.165s)`，适配器状态 `enabled and verified`，零注入失败 |
| **实机：A/D 界面出现 / 进度可推进 / 逃脱扣分 / 不发证** | **需人工，未完成** |

**请这样复测**：

1. 求欢 → **拒绝**（应只扣一次 `affinityLossOnReject`）→ 生物强行发起 →
   **期望出现 NON 的 A/D 挣扎条**，连点可推进；成功脱离后期望：
   `NON reported a successful struggle for mating <instanceId>` →
   `settled as NONE … escapedByPlayer=true` → `configuredEscapeLoss=25 applied=-25`，**无友情之证**。
2. 若界面没出现（或日志出现回退 WARN）：把该行日志发我，我按 NON 版本与回退原因调整。
3. 把 `nonStruggleScreen` 关掉后再试一次：期望回到"普通动画 + 用挣脱键退出"。
4. 顺带确认：正常跑完仍 `completed … evidence=NATURAL_END` + 加好感（好感满给证）；手动推进仍是 `MANUAL_ADVANCE` 不结算。

客户端兼容、F 键四模式、无动画动物按 F、驯马驯狗实机（rc.33 结论未在 rc.34–39 复测）仍待验收。
