# 1.0.0-rc.15 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.15，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc14.md`、`STATUS-rc13.md` … `STATUS-rc6.md`(**不可交付**)。
> 本批按交接清单完成第 1–3 项，并按要求**逐项区分**自动测试 / 服务器加载 / 客户端加载 / 实际行为验收。

## 本批（rc.15）修正的三项

### A. 拆开「呼吸让路」与「战斗控制」

**缺陷**：`controlsCompanion` 同时被两件事使用 —— 本 Goal 自己是否能运行，
**以及**原版 `FollowOwnerGoal` / `TrackTargetGoal` 的闸门条件。
低氧让路让 `controlsCompanion` 返回 false，于是闸门**一起失效**：
一只快淹死的伙伴会**重新获得原版跟随主人、原版报复/选敌**的行为。
「让它上浮」变成了「让它变回原版宠物」。

**修复**：拆成两个谓词，且都是**纯规则**（可脱离世界、实体与 GoalSelector 测试）：

- `AirSupply.isManaged(serverSide, alive, hasOwner, conflict, occupied)`
  —— **签名里没有任何空气参数**。闸门只由它驱动。
- `AirSupply.mayControlMovement(managed, mustYieldForAir)` —— 本 Goal 是否可运行。
- `gate(...)` 现在用 `!isManagedCompanion(mob)` 作为 `ConditionalGoal` 的许可条件，
  **不再**用 `controlsCompanion`。
- `tick()` 增加显式守卫，使「让路时不操纵移动」不依赖选择器是否先调 `shouldContinue`。

**效果**：低氧时只释放**移动**；WAIT 仍不追敌，WANDER 仍不跟随主人。

### B. 攻击标记生命周期三处修正

1. **删除错误描述**：`@At("RETURN")` 注入在**返回指令**上，属正常控制流，
   **异常传播不经过它**。此前注释声称「抛异常也会清除」是**错的**，已删除。
2. **不假装解决异常路径**：实测 `MobEntity#tryAttack` 字节码**只有 1 个返回指令、没有异常表**，
   异常只可能来自被调用的深层方法。本批**没有**用 try/finally 包装，也**没有**吞掉异常。
   替代做法：新增 `launchedTick == nowTick` 约束 —— **非当前刻的戳一律不认**。
   于是一旦真有异常留下戳，它**下一 tick 即自动失效**，不必等一个可能永不到来的清理信号。
3. **`clearAll()` 修复**：原来只清标记表，令牌表 `TOKENS` **没清**。
   已一并清空，并新增 `tokenCount()` 与对应断言。

**同时发现并修掉一个严重缺陷（不在清单上）**：

> **mixin 打在错误的类上。** 原为 `@Mixin(LivingEntity.class)`，但反汇编显示
> `LivingEntity#tryAttack(ServerWorld, Entity)` 是**空壳**（只调 `onAttacking` 后 `return false`），
> **真正的攻击主体在 `MobEntity#tryAttack`**。
> 即：**对任何生物都从未打过戳，迟到命中检查整体失效**，而所有单元测试仍然全过。
> 现改为 `@Mixin(MobEntity.class)`，并新增断言用**字节码长度**区分空壳与真身
> （`LivingEntity` 版 6 字节 vs `MobEntity` 版数百字节），使「打在错误类上」不再静默复发。

### C. 验收说明修正

- 模式切换写明**「潜行（蹲下）＋ 主手空手右键」**，并说明**必须潜行**
  （代码在 `!isSneaking()` 时 `PASS`，普通右键保留骑乘/坐下等原版交互），
  以及真实切换顺序 **跟随 → 漫游 → 等待 → 主动 → 跟随**（从默认跟随按 **3 次**到「主动」）。
- 「敌对生物 / 狼 / 猫」改为**区分**：「代码白名单返回 true」是**代码事实**；
  「实机追击并命中」**未验证** —— 本项目**从未做过真实战斗验证**。
  **不得**把「在白名单里」写成「行为已验证」。
- 远程命中不续期改为**阻塞项**：若正式版继续支持骷髅等远程伙伴，
  **必须在 1.0.0 之前**为投射物建立可校验的发射戳，**不得**仅凭「列为已知限制」就算完成行为目标。

---

## 当前产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.15+1.21.11.jar` |
| 自动测试 | **165 个 JUnit 用例，0 失败，0 错误**（26 个测试类） |
| 语言审计 | **89 个玩家可见键，0 缺失**；配置界面 **34 项 / 68 键，0 缺失**；占位符 0 不一致 |
| 服务器加载验收 | **通过**（rc.14 已做，rc.15 需复跑，见「游戏验收」） |
| 客户端加载验收 | **未做** |
| 实际行为验收 | **未做**（未连接客户端、未做四模式实机操作） |

构建：`jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home`；
`clean test build` → `BUILD SUCCESSFUL`。

---

## 四类验证必须分开读（本批要求）

| 类别 | 含义 | 本批状态 |
|---|---|---|
| **自动测试** | `gradle test`，纯 JUnit，用替身或纯值 | **165 通过** |
| **服务器加载** | 专用服务器能启动、mixin 能应用 | **rc.14 通过**；rc.15 需复跑 |
| **客户端加载** | 正式客户端能进入世界、无崩溃 | **未做** |
| **实际行为验收** | 狼按四模式真实行动、真实战斗 | **未做** |

**自动测试通过 ≠ 服务器能加载 ≠ 客户端能加载 ≠ 行为正确。** 四者互不替代。

---

## 一、rc.14 已完成的源码修复（保留）

### 1. RecallFlow

**核对结论（报告与实现不一致之处）**：

| 报告说法 | 实际实现 |
|---|---|
| 「两方法的适配接口」 | 实际是**单方法** `@FunctionalInterface` |
| 成功后判断是否返回 | 用的是**瞬移前**的 `distanceToOwner` |

**真正的缺陷**：`recallDue` 要求距离**大于**召回距离，所以「瞬移前距离 ≤ 召回距离」
**恒为假** → `alreadyBack` 分支**在生产环境不可达**，只有替身能触发。

**修复**：

- 适配器返回 `RecallOutcome(recalled, distanceAfterRecall)`：**只报告真正观测到的落点距离**。
- `CompanionRecall.tryRecall` 返回**落点 `Vec3d`**（无安全点返回 `null`）；
  生产侧由落点计算「召回后到主人的距离」。
- **无法测量落点时按「尚未返回」处理** —— 否则伙伴会立刻冲回原处，正是返回窗口要打断的循环。
- **召回资格规则单一实现**：`recallDue(ownerUsable, sameWorld, distance, recallDistance)`。
  三项条件：**主人可用（存活且非旁观）**、**同维度**、**距离严格大于召回距离**。
- `RecallContext` 构造时拒绝 `ownerId == null` 却 `ownerUsable == true` 的矛盾组合。

### 2. 攻击标记：作用域与伤害来源

- **近战失败遗留标记**：标记原本只在 HEAD 打上，**没有任何清除点**。
- **投射物误用近战标记**：命中只核对**攻击者 + 目标**，**未核对伤害来源**。

**修复**：新增 `MeleeAttackStamps`（HEAD + RETURN 限定作用域）；
命中要求 **直接近战（`source.getSource() == attacker`） + 标记存在 + 目标一致 + 代次有效 + 同刻**。

### 3. 水中与低氧让路

反汇编实际映射 jar 得到的事实：

| Goal | 占用的控制位 | 优先级 |
|---|---|---|
| `SwimGoal` | **只有 `JUMP`** | 0 |
| `BreatheAirGoal` | **`MOVE`**（+ `LOOK`） | 0 |
| `CompanionMovementGoal`（本项目） | **`MOVE` + `JUMP`** | 0 |

**交接清单的怀疑成立，且风险面比 rc.13 报告写的大**：真正与我们 `MOVE` 冲突的是
`BreatheAirGoal`；同时我们对 `JUMP` 的占用也压住 `SwimGoal`。
（`GoalSelector.tick` 的字节码确认：启动时用 `canReplaceAll` 检查控制位占用，
而 `PrioritizedGoal.canBeReplacedBy` 在**优先级相等时返回 false**，
所以两个 priority-0 的原版 Goal 是在我们的 Goal 让出后才可能接管的。）

---

## 二、本批新发现并修掉的缺陷（不在交接清单上）

1. **mixin 打在错误的类上**（最严重，见上文 B）—— 迟到命中检查此前整体失效。
2. `recallDue(context)` 忽略 `ownerUsable`，只读 `ownerId` → 「主人已死亡/旁观」会被判为应召回。
3. `RecallContext` 允许 `(ownerId=null, ownerUsable=true)` 的矛盾形状。
4. **契约测试原本是不可能失败的检查**：想扫描原版字节码判控制位，
   但测试 classpath 上的类字节**字段名已混淆**，扫描恒返回「无控制位」。
   已改为检查本项目未混淆的源码。
5. **`MobEntity` 在纯 JUnit 进程中无法链接**（`VerifyError`），
   所以任何对它做反射的测试都不可用；改为纯字节码读取。

---

## 三、自动测试

**165 用例 / 0 失败 / 0 错误 / 26 个测试类**。

| 测试类 | 覆盖 |
|---|---|
| `AirSupplyTest` | 让路规则；**分离的接线**：低氧不改变 `isManaged`、WAIT 低氧仍不让路给追击、非托管生物永不操控 |
| `CompanionStallWiringTest` | 生产 `markStallExit` / `selectionSurvivesRun`；**低氧 WAIT 仍不重开旧目标** |
| `MeleeAttackStampsTest` | 标记作用域；**非当前刻的戳无效**；两条交接点名的回归；`clearAll` 清两张表 |
| `AttackStampMixinContractTest` | **MobEntity 是真身、LivingEntity 是空壳**（字节码长度）；HEAD+RETURN 两端；控制位接线 |
| `RecallFlowTest` | 落点决定返回窗口；瞬移前距离**不可能**满足资格；规则单一来源 |
| 其余（rc.13 及以前） | 规则层、停滞、事件存储、双伙伴、语言审计等 |

**测试都执行生产逻辑**，未在替身中复制规则。**不证明**真实生物被写入目标、真实伤害被结算。

---

## 四、游戏验收

### A. 服务器加载验收：rc.14 通过，rc.15 需复跑

rc.14 的日志确证两个注入点都在真实方法上应用成功、零 mixin 失败、本模组零 ERROR。
**rc.15 改了 mixin 的目标类（`LivingEntity` → `MobEntity`），因此必须重新确认加载**，
不能沿用 rc.14 的结论（本次已复跑，结果见文末「本次运行记录」）。

### B. 客户端加载验收：未做

### C. 人工行为测试步骤

**准备**

1. 把 `build/libs/naturalrelationship-1.0.0-rc.15+1.21.11.jar` 放入一份**测试用**实例的 `mods/`
   （含前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。**不要覆盖正式存档。**
2. 在 `config/noncreatureaffinity.json` 设 `"debugLogging": true`。
3. 收集日志：`.minecraft/logs/latest.log`（整段）+ 必要时 `debug.log`。
   本模组的行都以 `[NoN Affinity]` 开头；mixin 相关看启动段的 `Mixing ...` 与 `@Inject::`。

**模式切换的操作方式（照 `CompanionInteractions` 的实际条件写）**

- **潜行（Shift，即游戏内“蹲下”）＋ 主手空手**，对着伙伴右键。
  **必须潜行**：代码在 `!serverPlayer.isSneaking()` 时直接 `PASS`，
  所以**普通右键仍然保留骑乘、坐下等原版/模组交互**，不会被本模组抢走。
- **主手必须空**：非空手不切换。
- **切换顺序是 `FOLLOW → WANDER → WAIT → ATTACK → FOLLOW`**
  （`CompanionCombatPolicy.nextMode`，由 `CompanionMode.next()` 委托，只有一处定义）。
  从默认「跟随」出发：按 **1 次**到「漫游」，按 **2 次**到「等待」，按 **3 次**到「主动」。
- 每次成功切换都会留 `companion interact applied ... modeAfter=...`；
  同一次物理点击的第二次回调会被去重并记 `SUPPRESSED duplicate`。
  **若一次点击切换了两次模式，请把这两行一起贴上来。**

**四种模式**

| 步骤 | 通过判据 | 失败时的日志线索 |
|---|---|---|
| **跟随**：走到 6 格外 | 狼走过来，3 格内停下，不抖动 | `PATH_REJECTED`、`ARRIVED at owner` |
| 跟随：主人攻击僵尸 | 狼协助攻击该僵尸 | `restored cleared target` / 无任何目标相关行 |
| 跟随：主人被僵尸打 | 狼攻击该僵尸 | 同上 |
| **主动**（3 次）：站远处，僵尸**主动锁定主人** | 狼主动接战（不需要先被打） | `scanHostility` 是否产生事实（需 debug） |
| 主动：附近有**中立**牛 | 狼**不**攻击牛 | 若攻击 → 误伤缺陷 |
| **漫游**（1 次）：下令后走开 | 狼**不**被拉回原位，用自身 AI 漫游 | 若被拉回 → 锚点逻辑误用 |
| 漫游：主人被打 / 攻击已有敌意的目标 | 保护 / 协助，仍不被拉回 | — |
| **等待**（2 次）：下令后主人走开 30 格 | 狼**留在原地**；被僵尸打时**原地反击** | `no path back to anchor` |
| 等待：僵尸在攻击距离外绕圈 | 狼**不追击** | 若移动 → 违反「不追击」 |

**距离与召回**

| 步骤 | 通过判据 |
|---|---|
| 跟随/主动：走到 **9.9 格** | **不召回** |
| 走到**恰好 10 格** | **不召回**（规则是「严格超过」） |
| 走到 **10.1 格** | **安全召回**到主人附近 2–3 格 |
| 等待 / 漫游：走到 15 格 | **不召回** |
| 主人在**不同维度** | 不瞬移、不加载远处区块、不报错 |
| 主人在**水里 / 岩浆边 / 悬崖 / 窄墙** | 找**安全**落点或**原地不动**；**不得**强行瞬移到玩家坐标；**不得**卡服 |
| 主人**离线** | 等待模式仍能**原地自卫**；跟随/主动不接战不报错 |
| 召回后**立即**看伙伴 | 不应**马上**冲回原来的敌人（返回窗口 + 抑制生效） |
| 战斗中主人快速跑开 | 应**安全召回**且**不**继续追原目标 |

**停滞与威胁**

| 步骤 | 通过判据 |
|---|---|
| 让狼攻击一个**打不到**的敌人（玻璃后 / 高台上） | 约 `companionEngageStallTicks`（默认 200 刻）后放弃 |
| 放弃后**持续**站在附近 | **本轮及下一轮都不重开**同一目标 |
| 放弃后旁边**出现另一个**敌人 | **仍然响应新敌人** |
| 两个伙伴（两只狼）绑同一主人 | 召回 / 停滞处理**互不影响** |

**模式切换与重载**

| 步骤 | 通过判据 |
|---|---|
| 战斗中切换模式（跟随→漫游） | 立刻停止追击、不再替主人参战 |
| 切换 主动 → 跟随 | 不残留旧目标 |
| **重载世界**（退出再进） | 模式、锚点、绑定关系保留；无异常 |
| 无锚点的旧存档 | 不报错、不凭空造锚点 |

**水中与低氧（本批拆分修复的重点，必须有实机结论）**

| 步骤 | 通过判据 |
|---|---|
| **等待**模式下带狼入水下潜 | 空气下降时狼**能自己上浮**；**同时不追敌、不跟随主人** |
| 上岸后 | 恢复**等待**行为（回到下令点附近），不因让路而变成跟随 |
| 跟随模式下入水并下潜 | 上浮后恢复跟随 |
| **陆地等待** + 旁边有水 | 不因该规则放弃原地等待、不产生位移 |
| 用**鱼 / 美西螈**等水生生物当伙伴 | **不**被赶出水面；行为正常 |
| **同时**低氧 + 旁边有敌人 | 允许上浮；**不得**转为追击 |

**远程命中（必须记录的阻塞项，不是可接受的限制）**

| 步骤 | 观察什么 |
|---|---|
| 骷髅伙伴持续远程命中敌人 | 战斗是否仍被判定**停滞**并退出（投射物无发射戳，命中不计为进展） |
| 记录 | 退出时的 tick、`companionEngageStallTicks`、伙伴与目标的距离 |

**这不是「已知限制所以通过」。** 若正式版继续支持骷髅等远程伙伴，
必须在 **1.0.0 之前**为投射物建立可校验的发射戳
（在投射物实体上记录发射时的**代次与目标**，命中时校验），
否则主动/跟随模式对远程伙伴**不可靠**。实机测试用于**确认问题的实际表现**，
以便实现后用同一场景验证修复，而不是把它归档了事。

### D. 行为验收通过后才可发布正式 1.0.0

**当前状态：不满足。** 服务器加载已通，客户端加载与行为**均未测**；
远程伙伴的续期问题**必须在 1.0.0 之前解决**（若继续支持远程物种）。

---

## 五、剩余限制（明确列出，不含糊）

1. **远程命中不续期战斗** —— 已是**阻塞项**，见上。
2. **水/呼吸门控：让路后由谁接管移动，未实机确认**。
   字节码已确认 `GoalSelector` 的占用/替换规则，但
   「让路后 `BreatheAirGoal` 与 `SwimGoal` 是否真的先于其他 MOVE 目标启动」
   只能实机观察。**必须实测水中、上岸、低氧三种场景。**
3. **原地自卫的命中判定**（`isInAttackRange` + `canSee`，每秒 1 次）**未实机验证**。
4. **战斗能力是「代码白名单」，不是「行为已验证」**：

   | 说法 | 状态 |
   |---|---|
   | `hasCombatCapability` 对**敌对生物 / 狼 / 猫**返回 true | **代码事实** |
   | 这些物种**实机追击并命中** | **未验证** —— 从未做过真实战斗验证 |
   | 马等**不在白名单**，只走移动、不参战 | **代码事实**；刻意区分「移动已适配」与「可协同攻击」 |

   **不得**把「在白名单里」写成「行为已验证」；**不得**把不支持战斗归因于玩家操作。
5. **NeedsOfNature 动画期间的占用**（`isActorBusy`）实机未复测。
6. **原版 owner-assist / 报复目标**是否在两次控制器 tick 之间重置目标，未实机确认。
7. **`@At("RETURN")` 不覆盖异常路径**（已如实说明，并用「非当前刻戳无效」兜底）；
   真实异常场景未构造验证。
8. 服务器加载验收在**专用服务器**上完成，**不是客户端**；客户端渲染/输入路径未覆盖。

---

## 六、可追溯性与边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **没有安装到游戏 mods 目录**；**没有修改任何玩家存档**。
- 为加载验收启动专用服务器时，会在项目 `run/` 下**自动保存开发用世界**。
- 构建命令、JDK/Gradle 版本、产物哈希见文首与「本次运行记录」。

---

## 本次运行记录

### 自动测试与构建

```
clean test build → BUILD SUCCESSFUL
TESTS: 165  FAILURES: 0  ERRORS: 0  测试类: 25
JAR:    naturalrelationship-1.0.0-rc.15+1.21.11.jar
BYTES:  249076
SHA256: DE5D36030CAE741F0679EC27C8385B678D7BCDAE59201C77C0D20CF806A9E35A
```

语言审计（`node tools/lang-audit.mjs`）：

```
配置界面选项: 34 项 / 68 键   缺失: 0
玩家可见键: 89   缺失: 0
占位符不一致: 0
语言文件校验通过
```

### 服务器加载验收（rc.15 复跑）

```
Mixing CompanionAttackStampMixin from noncreatureaffinity.mixins.json into net.minecraft.entity.mob.MobEntity
  -> @Inject::noncreatureaffinity$stampAttackLaunch(ServerWorld, Entity, CallbackInfoReturnable)  has 0 override(s)
  -> @Inject::noncreatureaffinity$clearAttackStamp(ServerWorld, Entity, CallbackInfoReturnable)   has 0 override(s)
```

- **目标类已确认为 `net.minecraft.entity.mob.MobEntity`**（rc.14 是 `LivingEntity`，那是错的）。
- 两个注入点均成功应用；**零 mixin 失败、零注入错误**。
- 服务器 `Done (0.301s)!`。

### 客户端加载验收（本批首次完成）

```
Loading Minecraft 1.21.11 with Fabric Loader 0.19.5
Loading 53 mods: ... - noncreatureaffinity 1.0.0-rc.15+1.21.11
[NoN Affinity] registered item noncreatureaffinity:friendship_token
[NoN Affinity] NeedsOfNature detected; animation integration enabled.
[NoN Affinity] initialized (NeedsOfNature present: true)
OpenAL initialized on device OpenAL Soft on 耳机 (THS520)
Sound engine started
Starting integrated minecraft server version 1.21.11
Player818 logged in with entity id 114 at (201.77, 73.73, -349.63)
Player818加入了游戏
```

- 客户端完整启动（渲染线程、音效、材质图集）、进入 **devworld**、玩家实际登录。
- **本模组零 ERROR**。唯一 ERROR 来自 **NeedsOfNature 的 buttplug.io 可选依赖缺失**
  （`ClassNotFoundException: io.github.blackspherefollower.buttplug4j.ButtplugException`），
  与被测模组无关，且它自行降级（`disabling toy output`）。
- **没有进行任何交互**：未绑定伙伴、未切换模式、未战斗。

> **退出原因如实说明**：客户端进程整体在 `18:39:20` 结束（`connected` 10 秒后 `连接中断`），
> 那是我为**停止专用服务器**所发的后台作业终止操作所致，**不是崩溃**，
> 日志中也没有伴随异常或 crash-report。为避免影响你正在使用的桌面，本批**没有重新拉起客户端**。

### 未做的验收

- 四模式实机行为、主人攻击/受击、距离召回、停滞后退出、低氧呼吸、两伙伴互不影响、骷髅远程。
- 这些都需要**人工操作**，步骤见上文「C. 人工行为测试步骤」。

> 启动方式（在你方便时自行执行，会在桌面打开窗口）：
> `gradle runClient --args="--quickPlaySingleplayer devworld"`
> 或直接把 rc.15 的 jar 放进测试实例的 `mods/`。
> **不要**使用正式存档；`devworld` 是项目内的开发用世界。

