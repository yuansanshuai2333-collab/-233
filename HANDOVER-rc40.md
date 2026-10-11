# 交接说明 · 1.0.0-rc.40

> 本批：**反编译 NeedsOfNature 本体，核实"生物攻击"载荷语义，并据此修掉 rc.39 里一个从未生效的致命缺陷**。
> **版本 1.0.0-rc.40，候选状态，未发布正式 1.0.0。**

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.40+1.21.11.jar` |
| 体积 / SHA256 | 299559 字节 / `41F490187A6E00B6982775D549F9BFE3A4B1829AFE795D9E923B91C3CAB7DB06` |
| 自动测试 | **249 用例 / 0 失败 / 0 错误 / 33 个测试类**（rc.39 为 246 / 32） |
| 配置 | 无新增；`nonStruggleScreen`（默认开）、`affinityLossOnEscape`（默认 25）沿用。**本批据源码把 `nonStruggleScreen` 的配置说明补全**（中英同步），把副作用完整写进界面文案 |

## 2. 一句话结论

**rc.39 的 A/D 挣扎界面从来没有出现过。** 原因不是"猜错了上游语义"，而是一个**必然抛异常的调用**：

```java
// rc.39（错）
new NonAnimationSessionData.PlayerAttack(player.getUuid(), null, creature.getUuid(), Set.of(creature.getUuid()))
```

NON 的记录构造函数首句即 `victim != null && finishOwner != null && initiator != null`，否则抛
`IllegalArgumentException("Player attack participants must not be null")`。它被 `catch (Throwable)` 吞掉、
回退到普通启动，只在日志里留一条 WARN——**所有测试仍然是绿的**，因为它们只检查"代码提到了正确的类"。

## 3. 核实到的上游事实（全部来自 `libs/needsofnature-1.5.11.21.11.jar` 的反编译源码）

| 问题 | 事实 | 位置 |
|---|---|---|
| 生物攻击怎么填 `PlayerAttack`？ | **不填**。NON 自己的生物攻击是「ATTACK 模式 + 无 `PlayerAttack`」，即 `NonAnimationSessionData.attack()`；`NeedsOfNature.buildModeContext(true)` 就是它 | `NeedsOfNature:2652`、`:1147/5039/5058`、`NonGatherSystem:435`、`NonPlayerAttackSystem:267` |
| `PlayerAttack` 属于谁？ | **PVP 专用**。`isPlayerAttackContext(ctx) = playerAttack != null`；生物攻击判据是 `attack && !isPlayerAttackContext(...) && hasMobActor(...)` | `NonGiveUpSystem:71-73/104/118-119` |
| 没有 Contest 时进度怎么动？ | **客户端本地算**。`PvpRole.NONE` 时 `NonHudOverlay.recordAttackInput` 直接 `attackProgress += step`；满格后客户端发 `StopAttackAnimationC2SPayload` | `NonHudOverlay:752-786`、`NeedsOfNatureClient:1067-1084` |
| 谁是"挣脱成功"？ | 服务端收到该包且**不是 Contest** 时直接调 `completeAttackEscape` → `AnimationService.stopAnimation` | `NeedsOfNature:6680-6693`、`:6695-6724` |

**旧文档 `STRUGGLE-FINDING.md` §3 的结论是错的**（它认为无 Contest 时连点会被丢弃），
已按上述事实整篇改写，文中标了"rc.40 修正说明"。

## 4. 本批改动

1. **`NonIntegration.startEscapableForcedMating`**：载荷改为 `NonAnimationSessionData.attack()`，
   即 NON 自己的生物攻击形状。
2. **顺手修掉道具抑制槽位丢失**：不再手写 `AnimationStageInfo` 映射，改为取 NON 自己的定义
   （`AnimationDefinitions.getDefinition(id).stages()`，与 `NonAnimationApi.start` 同一来源），
   **恢复了每个 prop 的 suppressed-slot 掩码**（此前手写映射丢了它，动画期间手持物显隐是错的）。
3. **新增启动日志**：`started forced mating <动画> as a mob-attack escape (instance <id>) so the player gets
   NON's struggle screen (<n>/<m> stage(s) escapable)`。实机若没出现挣扎条，这一行会先说明"这条动画
   有几个阶段可挣扎"——内容包把阶段标成 `escapable: false` 时，NON 本来就不会下发。
4. **新增防回归测试**（直接对 NON 真实类断言，测试类路径为此加入 NON 本体 jar）：
   - `NonAttackPayloadContractTest`：① `attack()` 是 ATTACK 模式且 `playerAttack() == null`；
     ② `PlayerAttack` 的规范构造函数**拒绝 null finishOwner**，补全后才会被接受。
   - `MatingSettlementWiringTest`：字节码里**禁止**再出现 `NonAnimationSessionData$PlayerAttack` /
     `playerAttack` / `AnimationStageInfo$ActorPropEntry`；**要求**出现 `NonAnimationSessionData.attack()`、
     `AnimationDefinitions.getDefinition`、`AnimationGameplayMetadata.escapable`。

## 5. 发布包核对（生产命名空间，反汇编 `build/libs/...rc.40....jar`）

```
AnimationDefinitions.getDefinition:(Lnet/minecraft/class_2960;)L.../AnimationDefinitions$Definition;
AnimationDefinitions$Definition.stages:()Ljava/util/List;
NonAnimationSessionData.attack:()Lcom/nonid/NonAnimationSessionData;
ServerAnimationController.startNowWithContext:(Lnet/minecraft/class_3218;Lnet/minecraft/class_3222;Lnet/minecraft/class_2960;Ljava/util/List;Ljava/util/List;Ljava/util/List;Lcom/nonid/api/animation/NonAnimationDamageBehavior;ZLjava/util/UUID;Lcom/nonid/internal/animation/server/AnimationStartContext;ZZ)Ljava/util/UUID;
AnimationGameplayMetadata.escapable:(Lnet/minecraft/class_2960;)Z
```

`PlayerAttack` 在生产包里**已无任何引用**（grep 为空）。

## 6. 验证分层（**不混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 249 用例 / 0 失败 / 0 错误 |
| NON 语义核实 | 通过：反编译 NON 1.5.11 全量 `com.nonid` 源码，逐条对照 §3 |
| 生产包反汇编 | 通过：上表 5 条调用点；无 `PlayerAttack` |
| 服务端启动（NON + 你的模组组合 + 370 个动画定义） | 通过：`Done (0.313s)`；两个混入均 `applying`；适配器 `enabled and verified`；零注入失败 |
| **实机：A/D 界面出现 / 连点推进 / 逃脱扣分 / 不发证** | **需人工，未完成** |

## 7. 请这样复测（用 rc.40 换掉客户端 `mods` 里的 rc.37/rc.39）

1. 求欢 → **拒绝**（只扣一次 `affinityLossOnReject`）→ 生物强行发起。
   - 期望日志：`started forced mating <动画> as a mob-attack escape (instance <id>) so the player gets NON's
     struggle screen (<n>/<m> stage(s) escapable)`。**先看这一行**：若 `<n>` 为 0，则内容包把阶段标成
     不可挣脱，界面本来就不会出现，请把这一行发我。
   - 期望画面：NON 的 A/D 挣扎条；**连点可推进**（进度条会涨，松手会回落）。
2. 连点到底（成功脱离）后期望：
   `NON reported a successful struggle for mating <id>` →
   `settled as NONE … escapedByPlayer=true stopReason=… evidence=…` →
   `configuredEscapeLoss=25 applied=-25`，**无友情之证**。
3. 若出现 `could not start the forced mating as an attack escape (<异常>)`：把整行发我。
4. 把 `nonStruggleScreen` 关掉再试一次：期望回到"普通动画 + 挣脱键（`break_free`）"，扣分相同。
5. 顺带确认正常跑完仍是 `completed … evidence=NATURAL_END` + 加好感；手动推进仍是
   `evidence=MANUAL_ADVANCE` 不结算。

## 8. 需要你知道的副作用（**据源码，不是猜测**）

以生物攻击形状启动后，NON 会走它自己的"生物攻击"流程，**可能**出现：

- 挣脱成功时按 NON 配置造成**逃脱伤害**（不会把你打到 1 HP 以下）；
- 给予**逃脱后短暂无敌**，并写入它的**逃脱统计**；
- 触发**逃脱后追击**：同一生物可能被请求再次对你发起攻击（`requestOriginalAttackers`）；
- Give Up 链路因 `mobAttack == true` 而激活（到阈值后会出现它的放弃提示）；
- 若 NON 的 destroyed-skin 系统开启，攻击实例开始时会给**玩家**累积 destroyed-skin 伤害。

**这些全是 NON 既有行为**，本模组只提供了一个起始上下文。不想要就把 `nonStruggleScreen` 关掉，
强行交流退回"普通动画 + 本模组挣脱键"，扣分规则完全相同。

> **决定记录（2026-09-22）：以上副作用经审阅后确认接受**，`nonStruggleScreen` 保持**默认开启**。
> 该结论已写入 `AffinityConfig#nonStruggleScreen` 的注释，并**完整写入配置界面的中英文说明**——
> 玩家在界面上看到的，就是这份清单。
