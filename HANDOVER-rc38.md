# 交接说明 · 1.0.0-rc.38

> 本批：**给强行交流补上"能拒绝"的手段**，并说明**为什么 NON 的 A/D 挣扎界面用不到交配动画上**。
> **版本 1.0.0-rc.38，候选状态，未发布正式 1.0.0。**

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.38+1.21.11.jar` |
| 体积 / SHA256 | 296188 字节 / `D369FE03ACD2E087062B34DACEBD830BD3EFAF857946801513F341AAB46D4E22` |
| 自动测试 | **244 用例 / 0 失败 / 0 错误 / 32 个测试类** |

## 2. 你要的"挣扎界面"：为什么接不上（有字节码依据）

你截图里的 **A/D + 进度条** 是 NON 的攻击逃脱界面。它的下发条件是
`NeedsOfNature.sendEscapeControlState(...)`（局部变量表实读：`isAttack / isDefeatAnimation / isStuckAnimation / escapable`）：

```java
if (snapshot == null) return;
boolean escapable = isAttack && isEscapableStage(currentStage);          // ① 必须是攻击实例
if (isAttack && NonPlayerAttackSystem.syncEscapeControl(world, id, escapable, isDefeat)) return;  // ② PvP 连点系统接管
for (player : playerActors) {
    EscapeSettings s = resolveEscapeSettings(snapshot, player, isAttack, isDefeat, isStuck);
    // resolveEscapeSettings 第一句：if (!isAttack || snapshot == null || player == null) return none();
    ...
}
```

- `isAttack = getNonSessionData(snapshot.startContext()).isAttack()` —— **由动画起始上下文是不是"攻击"决定**。
- 公开 API `NonAnimationApi.StartOptions` 的字段只有
  `damageBehavior / ignoreAttackers / positionAnchor / requester / metadata / forceDebugChat / allowMultiplePlayers`
  ——**没有**任何声明该上下文的入口；攻击上下文由包私有的 `NonPlayerAttackSystem` 构造。
- 连点进度在包私有 `NonPlayerAttackSystem$Contest` 里；且
  `onAnimationStarted(...)` 的第一段就是 `if (PlayerAttack == null) return;`
  ——也就是说：**硬造一个"攻击上下文"只会让界面出现、连点却毫无反应**（没有 Contest 可推进），
  这正是我在 rc.36 说明里拒绝伪造的原因。要做成真的，就必须伪造 PvP 载荷，让 NON 按攻击语义处理交配动画
  （逃脱伤害、逃脱后追击、Give Up 链、defeated 尾巴），风险不可接受。

**建议向 NON 提的接口请求**已写成可直接转发的稿子：`STRUGGLE-FINDING.md` §5
（① `StartOptions` 增加 `escapable`；② `StoppedContext` 暴露 `StopReason`；③ 公开 `stop(instanceId)`）。

## 3. 本批实际做了什么

1. **新增"挣脱"按键**（`key.noncreatureaffinity.break_free`，**默认未绑定**，在"控制"里自行设置）：
   - 按下后由**服务端**判断你是否正处在**本模组的强制交流**中（客户端不能指定目标，也不能结束别人的动画）；
   - 命中则：先标记"玩家挣脱"→ 用公开 API `NonAnimationApi.advanceStage(...)`（NON 没有公开的 stop）
     有界推进直到实例结束 → 结算为**不奖励** + 按 `affinityLossOnEscape`（默认 25）扣好感 + **不发友情之证**；
   - 日志：`player <名> is breaking free of forced mating <instanceId>`，随后
     `settled as NONE … escapedByPlayer=true evidence=MANUAL_ADVANCE` 与
     `forced mating escaped by … configuredEscapeLoss=25 applied=-25 affinity=…`。
2. **修正误导提示**：原 `escape_hint` 写的是"（攻击它即可挣脱）"，但动画期间**点不出攻击**，
   现改为"（在"控制"里设置"挣脱"按键即可挣脱）"，中英文均已更新。
3. 保留 rc.37 的判定修正：**手动推进阶段不再算完成**（`evidence=MANUAL_ADVANCE` → 不结算），
   以及 rc.36 的挣脱扣分/不发友情之证。
4. 保留 rc.37 的日志可读性改进：`PATH_*`/`ARRIVED` 仅在 `debugLogging=true` 时输出。

**如实说明**：这条"挣脱"是**本模组自己的按键**，界面**不是** NON 的 A/D 挣扎条——
在 NON 暴露接口之前，第三方无法把它接到自己启动的动画上。若你更希望"等上游、不要自建按键"，
告诉我，我把按键移除（判定与扣分逻辑不变，届时靠 NON 的界面触发）。

## 4. 验证分层（**不混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 244 用例 / 0 失败（新增：挣脱只命中"本人 + 强制"的会话；挣脱后不奖励且扣 `affinityLossOnEscape`；接线字节码断言） |
| 服务端启动（NON + 你的模组组合 + 默认内容包） | `Done (0.916s)`，适配器两条路径均已观察，零注入失败 |
| 既有行为回归（服务端） | 旧宠物回填 `WAIT`/`FOLLOW` 正常 |
| **实机：按"挣脱"键能否结束、扣分与是否发证** | **需人工，未完成**（本机无客户端，无法启动真实动画） |

**请这样复测**（先到"控制"里给"挣脱"绑一个键）：

1. 求欢 → **拒绝**（应只扣一次 `affinityLossOnReject`）→ 生物强行发起 →
   按**挣脱键**：期望交互立即结束、出现
   `is breaking free of forced mating …`、`settled as NONE … escapedByPlayer=true`、
   `configuredEscapeLoss=25 applied=-25`，**好感再降 25**、**背包没有友情之证**。
2. 强制交流中按**下一阶段（调试）**把动画推完（不按挣脱键）：期望
   `settled as NONE … evidence=MANUAL_ADVANCE`，**好感不变、不扣挣脱分**、无证。
3. **正常跑完**（不按键）：期望 `completed … evidence=NATURAL_END` + `mating settled … applied=+N`；
   好感满时出现 `friendship token handed …`（正常路径不能被改坏）。
4. 顺手确认：强行交流里**没有** NON 的 A/D 挣扎条（上游限制）；挣脱键在非强制交流时按下去**什么都不发生**。

客户端兼容、F 键四模式、无动画动物按 F、驯马驯狗实机（rc.33 结论未在 rc.34–38 复测）仍待验收。
