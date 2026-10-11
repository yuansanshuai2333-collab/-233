# 进度报告与待协助事项（rc.40）

> 面向外部审查者的交接说明。分两部分：**A. 目前进度**（含"已自动验证 / 已服务端验证 / 仅人工可验证"三层）、
> **B. 我难以独立完成、需要协助的事项**（每条写清：卡点、我的证据、需要什么、验收标准）。
> 版本状态：**1.0.0-rc.40，候选，未发布正式 1.0.0**。

---

## A. 目前进度

### A.1 产物与质量门

| 项 | 值 |
|---|---|
| 包 | `build/libs/naturalrelationship-1.0.0-rc.40+1.21.11.jar` |
| 体积 / SHA256 | 299559 字节 / `41F490187A6E00B6982775D549F9BFE3A4B1829AFE795D9E923B91C3CAB7DB06` |
| 自动测试 | **249 用例 / 0 失败 / 0 错误 / 33 个测试类**（rc.39：246 / 32） |
| 混入 | 常驻 10 个（`required: true`）+ 可选集成 2 个（`required: false`，插件按 NON 版本开关） |
| 配置 | 无新增；`nonStruggleScreen`（默认开）、`affinityLossOnEscape`（默认 25）沿用。本批据源码把 `nonStruggleScreen` 的中英文界面说明**补全为完整副作用清单**（决定已拍板，见 B4.3） |

### A.2 本批做的事：**把 rc.39 的目标真正接通**

rc.39 的目标是"强行交流接入 NON 官方 A/D 挣扎界面"。**它从来没有生效过**，而原因是一个必然抛异常的调用：

```java
// rc.39：finishOwnerUuid 传了 null
new NonAnimationSessionData.PlayerAttack(player.getUuid(), null, creature.getUuid(), Set.of(creature.getUuid()))
```

NON 的记录构造函数首句即要求 `victim / finishOwner / initiator` **三者都非 null**，否则抛
`IllegalArgumentException`。异常被 `catch (Throwable)` 吞掉 → 回退普通启动 → 只留一条 WARN。
**246 个测试全绿，因为它们只检查"代码提到了正确的类"，没有任何一条真正调用 NON 的构造函数。**

### A.3 为定位它做了什么：反编译 NON 本体

上一份报告把这条列为 **B2：需要外部协助确认"生物攻击"载荷语义**。本批没有等外部答复，
而是把 `libs/needsofnature-1.5.11.21.11.jar` 用 Vineflower 全量反编译（766 个类），逐条读源码。

| 问题 | 核实结果 | 依据位置 |
|---|---|---|
| 生物攻击怎么填 `PlayerAttack`？ | **不填**。NON 自己的生物攻击 = ATTACK 模式 + **无 `PlayerAttack`**，即 `NonAnimationSessionData.attack()`；`NeedsOfNature.buildModeContext(true)` 就是它（public static） | `NeedsOfNature:2652`、`:1147/5039/5058`、`NonGatherSystem:435`、`NonPlayerAttackSystem:267` |
| `PlayerAttack` 属于谁？ | **PVP 专用**。`isPlayerAttackContext(ctx) = playerAttack != null`；生物攻击的判据是 `attack && !isPlayerAttackContext(...) && hasMobActor(...)` | `NonGiveUpSystem:71-73/104/118-119` |
| 手造 PVP 载荷行不行？ | **不行**。构造函数拒绝 null；补上非 null 值又会让 NON 认为"**玩家**在攻击该玩家" | `NonAnimationSessionData:111-129` |
| 没有 Contest，进度怎么动？ | **客户端本地算**：`PvpRole.NONE` 时 `NonHudOverlay.recordAttackInput` 直接 `attackProgress += step`；满格后客户端发 `StopAttackAnimationC2SPayload` | `NonHudOverlay:752-786`、`NeedsOfNatureClient:1067-1084` |
| 谁算"挣脱成功"？ | 服务端收到该包且**不是 Contest** 时，直接调 `completeAttackEscape` → `stopAnimation` | `NeedsOfNature:6680-6693`、`:6695-6724` |

**→ 结论：`attack()`（不带 PVP 载荷）就是正确形状，界面与进度都成立，而且这正是 NON 自己的生物攻击路径。**
旧文档 `STRUGGLE-FINDING.md` §3 的相反结论（"没有 Contest 时连点会被丢弃"）是错的，已整篇改写并标注修正。

### A.4 改动清单

| # | 内容 | 为什么 |
|---|---|---|
| 1 | `startEscapableForcedMating` 改用 `NonAnimationSessionData.attack()` | 修掉必然抛异常的调用；并让 NON 走它自己的"生物攻击"语义，而不是伪装 PVP |
| 2 | 不再手写 `AnimationStageInfo` 映射，改取 `AnimationDefinitions.getDefinition(id).stages()` | 手写映射**丢了每个 prop 的 suppressed-slot 掩码**（动画期间手持物显隐错误）；现在与 `NonAnimationApi.start` 同源 |
| 3 | 新增启动日志（含"几个阶段可挣扎"） | 实机没出现挣扎条时，日志能先排除"内容包把阶段标成不可挣脱"这一种可能 |
| 4 | 新增 `NonAttackPayloadContractTest`（2 例）+ 加强 `MatingSettlementWiringTest` | 让"手造 PVP 载荷"这类错误**在单测里就失败**，而不是只在实机里静默回退 |
| 5 | 改写 `STRUGGLE-FINDING.md`、新增 `HANDOVER-rc40.md`、本报告 | 旧结论已被证伪，必须留痕 |

### A.5 验证分层（**严禁混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | **249 用例全绿**（新增：直连 NON 真实类的载荷契约；字节码禁止项/必需项） |
| 上游语义核实 | 通过：反编译 NON 1.5.11 全量源码，逐条对照 A.3 表 |
| 生产包反汇编 | 通过：5 条调用点均在生产命名空间核对；`PlayerAttack` 已无任何引用 |
| 服务端启动（NON + 你的模组组合 + 370 个动画定义） | 通过：`Done (0.313s)`、两个混入 `applying`、适配器 `enabled and verified`、零注入失败 |
| 服务端行为（旧宠物回填 / 持久化 / 幂等） | 沿用 rc.31–rc.39 结论，本批未改动相关代码 |
| **客户端与实机行为** | **仍未验证**（唯一实测来自你那轮 rc.36 日志） |

### A.6 生产包反汇编证据（可直接核对）

```
com/nonid/internal/animation/data/AnimationDefinitions.getDefinition:(Lnet/minecraft/class_2960;)Lcom/nonid/internal/animation/data/AnimationDefinitions$Definition;
com/nonid/internal/animation/data/AnimationDefinitions$Definition.stages:()Ljava/util/List;
com/nonid/NonAnimationSessionData.attack:()Lcom/nonid/NonAnimationSessionData;
com/nonid/internal/animation/server/ServerAnimationController.startNowWithContext:(Lnet/minecraft/class_3218;Lnet/minecraft/class_3222;Lnet/minecraft/class_2960;Ljava/util/List;Ljava/util/List;Ljava/util/List;Lcom/nonid/api/animation/NonAnimationDamageBehavior;ZLjava/util/UUID;Lcom/nonid/internal/animation/server/AnimationStartContext;ZZ)Ljava/util/UUID;
com/nonid/internal/animation/data/AnimationGameplayMetadata.escapable:(Lnet/minecraft/class_2960;)Z
```

`grep PlayerAttack` 在生产包里**为空**。

---

## B. 难以独立完成、需要协助的事项

### B1（最高优先，未变）**实机验证只能在客户端做**

- **卡点**：A/D 条是否出现、连点是否推进、逃脱副作用、F 键四模式、驯马驯狗，都需要真实客户端。
  本机只能起专用服务端（没有玩家实体 → 任何"玩家 + 生物"动画都无法启动）。
- **本批的改善**：以前"界面没出现"只能猜；现在启动日志会先给出
  `… (<n>/<m> stage(s) escapable)`。`<n> == 0` 就直接是内容包标了 `escapable: false`，不必再查。
- **需要你做的**：把 **rc.40** 换进客户端 `mods`（原来放的是 rc.37），跑 `HANDOVER-rc40.md` §7 的 5 条，
  按**实例**回传日志行 + 好感数值 + 是否收到友情之证。
- **验收标准**：日志行与预期 marker 完全对应；任何一条不符，把原文发我定位。

### B2（**已由本批自行解决**，无需外部协助）

原问题："NON 自己的 PvE 攻击流程怎么填 `PlayerAttack`（尤其 `finishOwnerUuid` 是否必须为 null）？"
→ **答案：不填。** NON 的生物攻击是 `NonAnimationSessionData.attack()`，不含 `PlayerAttack`；
`PlayerAttack` 是 PVP 专用，且其构造函数不接受任何 null 参与者。依据见 A.3。
**如果你有 NON 作者的渠道，仍值得确认一次**，但代码已不依赖这一确认（它用的就是 NON 自己的形状）。

### B3 `StopReason` 读不到 —— 未变，仍需要上游或更好的办法

- **卡点**：`ServerAnimationController$StopReason` 是包私有枚举，只作为参数传给
  `stopInstance(World, UUID, boolean, StopReason)`；以完整描述符注入被 Mixin 拒（`Scanned 0 target(s)`）。
- **现状**：自动完成 = tick 路径内联停止；其余一律不结算（宁可漏奖，不可错奖）。
- **需要你/审查者做的**：评估是否有更可靠的读取方式（例如在 `fireStopEvent` 处读 snapshot），
  或把"`StoppedContext` 暴露 `StopReason`"作为上游请求推进（`STRUGGLE-FINDING.md` §5 请求 2）。
- **验收标准**：判定不再依赖"代码路径枚举"，而是直接使用 NON 报告的原因。

### B4 产品政策需要拍板（我不应替你决定）

1. **未挣脱、自然跑完的"强制交流"是否应该加好感？** 现行：会（+`affinityPerMating`，无主动加成）。
   "被强迫还加好感"是否合理，需要你定；改成 0 或改成扣分都是一行配置。
2. **用调试键"下一阶段"推完强制交流**，现行"不奖励、不扣分"。是否视为**挣脱**（扣 `affinityLossOnEscape`）？
3. **`nonStruggleScreen` 默认开**是否接受它带来的 NON 攻击副作用？（本批已把清单从"猜测"升级为
   **源码级确认**：逃脱伤害、逃脱后无敌、逃脱统计、逃脱后追击、Give Up 链、destroyed-skin 累积。
   见 `HANDOVER-rc40.md` §8。）
   → **【已拍板 2026-09-22：接受】** 默认保持开启；副作用清单已写入 `AffinityConfig` 注释
   与配置界面的中英文说明（玩家在界面上看到的就是完整清单）。**本条关闭。**
4. **本模组自建挣脱键**是否保留（默认未绑定）？现在它有了明确定位：`nonStruggleScreen` 关闭、
   NON 版本不受支持、或阶段不可挣脱时的兜底。

### B5 长期运行的状态/内存边界（未变）

- 已有：会话终态集合上限 1024、停止证据上限 2048、投射物按实例身份释放且移除钩子幂等、跟随驾驶记录上限 512。
- **需要你做的**：多小时会话中每隔一段时间执行 `/nonaffinity stampcounts`，回传
  `melee/projectile/projectileUuidIndex` 序列。

### B6 客户端兼容矩阵（未变，我只有服务端）

- 仅依赖组（NON + Fabric API）与完整组合，我都只测了**服务端**；客户端专属模组
  （CustomSkinLoader、entity_model_features、entity_texture_features、modmenu）的客户端共存**未验证**。
- **需要你做的**：客户端两组各跑一次"能启动、进世界、退出重进、日志无注入报错"。

### B7 发布门槛的建议

- 本批把 rc.39 的"依赖内部实现"从**未经验证的猜测**变成了**有源码依据的对接**：
  - 用的载荷是 NON **自己**的 public 形状（`NonAnimationSessionData.attack()`）；
  - 阶段列表来自 NON **自己**的定义（`AnimationDefinitions.getDefinition`），不再手写；
  - 代码里**没有任何**对 PVP 载荷的伪造。
- 仍属内部面的只剩两处 public 入口：`ServerAnimationController.startNowWithContext`、
  `AnimationDefinitions.getDefinition`，都在**版本门禁 + 失败大声回退**之下。
- **需要审查者明确表态**：这样的依赖是否可作为正式 1.0.0 的发布条件？
  若否决，我按 B3/B4 退回"仅本模组挣脱键、不依赖任何内部入口"的形态（改动量小，因为兜底路径已存在）。

---

## C. 现成可用的核对命令

```bash
# 1) 本模组全部结算/适配器/挣扎日志
grep -E "natural-completion adapter|stop-path adapter|mating settlement|settled as NONE|mating settled for|completed with|is breaking free|NON reported a successful struggle|started forced mating|never observed running|ignoring duplicate" logs/latest.log

# 2) 攻击启动是否失败（本批重点）
grep -E "could not start the forced mating as an attack escape|stage\(s\) escapable" logs/latest.log

# 3) 注入报错与回退
grep -E "InvalidInjection|Mixin apply for mod noncreatureaffinity|will NOT be granted" logs/latest.log
```

**字段含义**：`evidence=NATURAL_END`（tick 自动完成）/ `MANUAL_ADVANCE`（手动推进）/
`INTERRUPTED`（其他停止）/ `UNKNOWN`（适配器未生效）；`escapedByPlayer` 表示"玩家挣脱"；
`applied=` 是**实际**加/扣数值（会被上下限夹取）；
`<n>/<m> stage(s) escapable` 是**本批新增**：`n = 0` 表示内容包不允许挣扎，界面不会出现。
