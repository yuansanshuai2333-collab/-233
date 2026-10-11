# NON 的「挣扎」界面到底怎么接（反编译依据 + 上游请求稿）

> **rc.40 修正说明**：本文件旧版（rc.36/rc.38 时期）的结论里有一条**是错的**，rc.39 据此走了弯路，
> 现已用 NON 1.5.11 的**反编译源码**逐条核对并改正。错在哪、正确的是什么，见第 3 节。

## 1. 门槛：没有攻击上下文就没有逃脱控制 —— 仍然成立

`NeedsOfNature.sendEscapeControlState(...)`：

```java
boolean escapable = isAttack && isEscapableStage(currentStage);
if (!isAttack || !NonPlayerAttackSystem.syncEscapeControl(world, instanceId, escapable, isDefeatAnimation)) {
    for (ServerPlayerEntity player : playerActors) {
        AttackStateS2CPayload.EscapeSettings settings =
            resolveEscapeSettings(snapshot, player, isAttack, isDefeatAnimation, isStuckAnimation);
        ServerPlayNetworking.send(player,
            new AttackStateS2CPayload(instanceId, isAttack, escapable, settings, PvpRole.NONE, 0.0F));
    }
}
```

而 `isAttack = hasPlayer && sessionData != null && sessionData.isAttack()`，
`resolveEscapeSettings` 首句 `if (!isAttack || snapshot == null || player == null) return none();`。
**即：只有"起始上下文 = ATTACK"的实例才会给玩家下发逃脱控制。**

`isEscapableStage(stage) = AnimationGameplayMetadata.escapable(stage.animationId())`，
内容包未声明时**默认 true**；声明 `escapable: false` 的阶段不下发。

## 2. NON 有**两种**攻击实例，判据只有 session data

| 形状 | session data | `isPlayerAttackContext(ctx)` | 谁在用 |
|---|---|---|---|
| **玩家攻击** | `NonAnimationSessionData.playerAttack(new PlayerAttack(victim, owner, owner, attackers))` | `true` | PvP（`NonPlayerAttackSystem.tryStartPlayerAttack`）、defeated 尾巴 |
| **生物攻击** | `NonAnimationSessionData.attack()`（**没有** `PlayerAttack`） | `false` | NON 自己的生物攻击、`buildModeContext(true)` |

`NonGiveUpSystem` 用的判据就是第二种：

```java
boolean mobAttack = attack && !playerAttack && (defeated || hasMobActor(world, snapshot.actorUuids()));
```

`NeedsOfNature.buildModeContext(boolean attack)` 是 **public** 的：

```java
public static AnimationStartContext buildModeContext(boolean attack) {
    return AnimationStartContext.empty()
        .withSessionData(attack ? NonAnimationSessionData.attack() : NonAnimationSessionData.voluntary());
}
```

NON 自己的生物攻击入口（如 `NeedsOfNature:1147`、`5039`、`5058`，`NonGatherSystem:435`）全都传
`buildModeContext(true)`。**所以"生物攻击该填什么 PlayerAttack"的答案是：根本不填。**

## 3. 旧版的错误（rc.39 因此上线了一个死功能）

旧版第 3 节写的是：

> `attack()` 会让界面出现，但不会创建 Contest；客户端连点送到 `handleInput` → 找不到 Contest →
> …进度永远不动。要做成"真挣扎"就必须伪造一份 PvP `PlayerAttack`。

**前半段（不会创建 Contest）是对的；结论是错的。** 真正的事实分两半：

**（a）PVP 载荷手造不出来，也造不得。**
`NonAnimationSessionData.PlayerAttack` 的规范构造函数：

```java
public PlayerAttack(UUID victimUuid, UUID finishOwnerUuid, UUID initiatorUuid, Set<UUID> attackerUuids) {
   if (victimUuid != null && finishOwnerUuid != null && initiatorUuid != null) { ... }
   else { throw new IllegalArgumentException("Player attack participants must not be null"); }
}
```

rc.39 传了 `finishOwnerUuid = null`，于是**每次启动都抛异常**、被 `catch (Throwable)` 吞掉并回退，
A/D 界面**从未出现**。即使补上非 null 值，`isPlayerAttackContext` 会变成 `true`，
NON 就会按"**玩家**在攻击这个玩家"处理——那是另一条特性（Give Up 链关闭、`IGNORE_DAMAGE` 等）。

**（b）没有 Contest 也照样是"真挣扎"，因为进度是客户端本地算的。**
`NonHudOverlay.setEscapeControlState(...)` 收到 `PvpRole.NONE` 时不把服务端进度当权威；
`NonHudOverlay.recordAttackInput(...)`：

```java
if (attackPvpRole == PvpRole.NONE) {
    float step = resolveAttackProgressStep();
    attackProgress = Math.min(1.0F, attackProgress + step);   // ← 本地推进
}
```

`NeedsOfNatureClient.handleAttackInput(...)`：

```java
NonHudOverlay.recordAttackInput(instanceId, attackKey);
if (NonHudOverlay.isServerControlledAttack() && ...) { /* 只有 PVP 才回传按键 */ }
if (NonHudOverlay.isAttackProgressComplete() && ...) {
    ClientPlayNetworking.send(new StopAttackAnimationC2SPayload(instanceId));   // ← 满了就请求停止
}
```

服务端 `registerAttackStopRequests()`：

```java
if (!NonPlayerAttackSystem.isContest(instanceId)) {
    ServerAnimationController.ActiveInstanceSnapshot snapshot = ...;
    if (isAttackSnapshot(snapshot) && snapshot.actorUuids().contains(player.getUuid())) {
        completeAttackEscape(world, player, instanceId, null);   // ← 就是"挣脱成功"这条路径
    }
}
```

`completeAttackEscape` 内部 `AnimationService.stopAnimation(world, instanceId)`，实例结束。
**所以：`attack()` + 非玩家攻击者 = 真挣扎条 + 真进度 + NON 自己的逃脱结算。**

## 4. 相关机制仍是包私有

| 机制 | 可见性 |
|---|---|
| `NonPlayerAttackSystem`（PvP Contest、连点输入、逃脱控制同步） | 包私有 |
| `NonGiveUpSystem`（按住放弃、黑屏跳过） | 包私有 |
| `ServerAnimationController$StopReason`（停止原因） | 包私有枚举 |

本模组用到的是**公开面**：`NonAnimationSessionData`（public record）、
`NeedsOfNature.buildModeContext`（public static）、`NonAnimationApi`，以及两个**内部但 public**的
入口 `ServerAnimationController.startNowWithContext(...)`、`AnimationDefinitions.getDefinition(...)`。
后者仍在可选集成的版本门禁内（`1.5.1*`），失败必大声回退。

## 5. 建议向 NON 提出的接口请求（可直接转发）

> **请求 1（首选，现在仍值得提）：让公开 API 能启动"可挣脱"的实例。**
> 例如在 `NonAnimationApi.StartOptions` 增加 `boolean escapable`（或直接接受一个
> `AnimationStartContext`/`sessionData`），使第三方不必借助 `startNowWithContext` 这类内部入口。
> 目前可行的等价写法是 `NonAnimationSessionData.attack()`，但它把实例标成"被生物攻击"，
> 会顺带启用 NON 的逃脱伤害 / 逃脱统计 / 逃脱后追击 / Give Up 链。
> 若能在公开 API 上区分"只是一次可挣脱的交互"和"真的是一次攻击"，就干净了。
>
> **请求 2：在 `NonAnimationEvents.StoppedContext` 暴露 `StopReason`。**
> 目前第三方只能靠代码路径推断"是自然结束还是被中断"（见 `HANDOVER-rc35.md`）。
>
> **请求 3（可选）：公开的停止接口** `NonAnimationApi.stop(ServerWorld, UUID)`。
> 公开 API 里已有 `advanceStage`，但"停在最后一个阶段之外"只能靠推进，语义上不干净。

## 6. 本模组当前的处置（rc.40）

- 强行交流默认以 **NON 自己的生物攻击形状**启动（`NonAnimationSessionData.attack()` +
  `startNowWithContext`），玩家得到 NON 的 A/D 挣扎条，连点推进，成功后 NON 调
  `completeAttackEscape`，本模组在那一点标记"玩家挣脱"→ 按 `affinityLossOnEscape` 扣好感、不发证。
- 关闭 `nonStruggleScreen`、NON 版本不被支持、或该动画的阶段全部 `escapable: false` 时：
  退回"普通动画 + 本模组挣脱键"（`key.noncreatureaffinity.break_free`），**扣分规则完全相同**。
- 任何一步失败都写 WARN 并回退，不会丢交互、不会崩服。
