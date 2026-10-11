# 1.0.0-rc.13 当前状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.13，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc12.md` … `STATUS-rc6.md`(**不可交付**)。

## 当前产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.13+1.21.11.jar` |
| 体积 | 243884 字节（238.2 KB） |
| SHA256 | `FC4ADC17883031CC3C3AF7A9963D3D972EDB8F8D4859558E725105247B792801` |
| 自动测试 | **137 个 JUnit 用例，0 失败，0 错误**（23 个测试类） |
| 语言审计 | **89 个玩家可见键，0 缺失**；配置界面 **34 项 / 68 键，0 缺失** |
| 游戏验证 | **无**（未启动客户端） |

构建方式：`jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` 指向项目内 `.codex-gradle-home`。
命令：`clean test build` → `BUILD SUCCESSFUL`。产物已核对：`fabric.mod.json` 版本为
`1.0.0-rc.13+1.21.11`，jar 内含 `CompanionAttackStampMixin.class` 且已登记在 `noncreatureaffinity.mixins.json`。

## 本批完成的 rc.12 未完成项

| rc.12 未完成项 | 本批状态 |
|---|---|
| 第三步：停滞退出的完整处理路径 + 重新验证本轮 Selection | **完成** |
| 第四步：薄适配接口 + 测试替身（调用链） | **完成** |
| 迟到命中代次 | **完成（仅近战，见下文限制）** |
| P2-1：wait/wander 半径界面可编辑但行为失效 | **完成** |
| P2-2：语言审计用手工档位列表 | **完成**（改为动态推导 + 断言无孤儿键） |
| 游泳门控 | **只做了根因定位，未改代码**（见文末） |

## 第三步：停滞退出

`end()` 原本已被调用，但**停滞分支没有自己的抑制路径，也没有回验本轮已选出的目标**，
所以本轮会把同一个目标立刻写回去，停滞退出等于无效。

现在：

- `EngagementState.suppressStalledTarget(...)`：停滞退出走**独立**抑制路径。
- **它不是召回的事件水位**。召回是「新决定要回去」，更新的敌意事实可以推翻它；
  停滞**不是决定**。而打不到的敌人**每秒都会被敌意扫描重新发布**，
  任何基于水位的抑制都会被下一次扫描推翻，伙伴会反复走回打不到的战斗。
  所以停滞窗口**只认时钟**：窗口内任何事实都不能重开该目标。
- `selectionSurvivesRun(...)`：**回验本轮 Selection**，只丢弃被抑制的那一个目标，
  **其他威胁照常响应**（不会因为一场陈旧战斗而放弃本轮的新目标）。
- 两个分支（有 Selection / 证据已过期的 IGNORE）都覆盖。

## 第四步：薄适配接口 + 测试替身

新增 `RecallFlow`：把「是否该召回」和「传送是否发生」收敛为一个**两方法的适配接口**。

- 生产侧**只有一处实现**，仍然调用 `CompanionRecall.needed` / `tryRecall`，
  距离规则**单一来源**，没有复制第二份。
- 替身验证调用链：**成功恰好一次端口调用 + 一次状态转换**；
  **失败只调用端口、不执行任何成功转换**（不建抑制、不开返回窗口、不留水位）。
- 顺带删掉 `onRecalled` 内重复的 `applyRecallResult` 调用 —— 它与新流程**重复执行了成功路径**。

## 迟到命中代次

新增 `CompanionAttackStampMixin`：在 `LivingEntity#tryAttack` 的 **HEAD** 打本次交战**代次**戳；
命中时用 `EngagementState.recordHitAtGeneration(...)` 校验，代次不符即**拒绝续期**。

**为什么必须打在发起时**：`AFTER_DAMAGE` 是**结算时**触发。近战同刻结算，
但**投射物要过很多刻才命中**，那时代次早已改变；在结算处打戳只会读到当时的代次，
**等于什么都没验证**。

**限制（明确、不含糊）**：只覆盖**近战**。投射物由远程目标生成，**不走 `tryAttack`**，
因此它的命中**不会被计为进展**。这是偏安全的方向，但
**「远程命中不再续期」是本批的真实行为限制**，不是未验证的说法。

代次戳在交战结束时清除，避免上一场的戳被下一场匹配。

## 本批新发现并修掉的三个缺陷

### (a) 停滞退出的「原因」曾把已放弃的战斗复活

第一版为了让「本次退出是停滞」可观测，把交战记录**重新写成一场新交战**。
但 `begin()` 会重设进度时钟，于是记录看起来像**一场刚开始且已经停滞**的战斗，
而**保活分支判定的正是这个字段**。

**修复**：退出原因改为独立字段 `lastEngagementExitWasStall`，
**记录保持无交战状态**，`engagedTarget` 保持为空，进度时钟保持停止。

### (b) 停滞窗口到期后**永久屏蔽**该目标

停滞窗口水位是 `Long.MAX_VALUE`。到期后 `suppressedTarget` 被清除，
但**水位留在原地**，于是 `sequence <= suppressedSequence` 对**任何**事实都成立：
该目标**在整个服务器会话内再也不会被选中**，表现为「伙伴再也不打某个敌人」。

**修复**：窗口关闭时**一并清除水位与停滞标记**；水位判定也**先看时钟**。

**这个缺陷是新增测试抓出来的**，不是靠读代码看出来的。

### (c) `companionWanderRadius` 界面可编辑但无行为

按用户决定 WANDER 完全交给生物自身 AI、不再拉回锚点，该半径**没有任何行为可挂**。
已从配置类与界面移除（旧 JSON 里的该键在加载时被忽略）。同时补上配置界面的 `long` 滑块。

## 测试类型区分（按要求明确）

| 类型 | 本批状态 |
|---|---|
| **规则测试**（`EngagementState` / `EngagementRules` / `RecallFlow` 层） | 通过 |
| **生产接线测试**（经薄适配层或生产接缝验证调用链） | **本批新增，通过** |
| **游戏验收** | **无**（未启动客户端） |

新增测试类：

- `StallExitRulesTest` —— 停滞抑制的完整规则与窗口边界。
- `CompanionStallWiringTest` —— **接线层**：驱动生产 `markStallExit` / `selectionSurvivesRun`，
  验证退出确实结束战斗、建立抑制、**拒绝本轮同一个目标**、**放行其他威胁**。
- `RecallFlowTest` —— **调用链替身**：成功/失败回调次数与状态转换。
- `CompanionEventStoreFlowTest` —— **事件存储层**：两个伙伴共用一份 owner 事实，
  一个的读取/召回**不消耗、不影响**另一个；序号单调、按 owner 隔离、按龄剪枝、有界保留。
- `AttackStampMixinContractTest` —— 注入目标契约（若 Yarn 改名，**普通测试就会失败**，
  而不是等到**启动游戏**才失败）+ 混入注册检查。
- `ConfigLanguageAuditTest` —— 反射读配置字段与界面列表，**断言**两种语言齐备且**无孤儿键**。

**仍然不能声称的**：这些接线测试用的是**替身实体**，因为构造真实 `MobEntity` 需要启动游戏。
它证明的是**控制流的顺序与次数**，不是「真实生物确实被写入了目标」。
后者仍属游戏验收。

## 未完成清单

- **游戏验收：全部**。本批**没有启动过客户端**。
- **远程命中不续期**（迟到命中代次的已知限制，见上）。
- **游泳/呼吸门控**：见下节，只做了根因定位。
- 现有 `stalledFightIsNotRestartedByFacts` 只检查 `isStalled` 返回值，
  不构成「停滞后不重开」的证据；该结论现在由 `CompanionStallWiringTest` 与
  `StallExitRulesTest` 承担。

## 游泳/呼吸门控：根因已定位（本批未改代码）

用 `javap` 反汇编**实际的映射后 Minecraft jar**（不是猜），得到确定事实：

| 原版 Goal | 占用的控制位 |
|---|---|
| `SwimGoal` | 只有 **`JUMP`**（`canStart` 里调 `getJumpControl().setActive()`） |
| `BreatheAirGoal` | **`MOVE` + `LOOK`** ← **真正冲突的是它** |
| `EscapeDangerGoal` / `FleeEntityGoal` / `WanderAroundGoal` / `MeleeAttackGoal` | `MOVE`（+ `LOOK`） |

原版 `MobEntity` 用 `goalSelector.add(0, new SwimGoal(this)); goalSelector.add(0, new BreatheAirGoal(this));`
注册两者。我们的 `CompanionMovementGoal` 是 **priority 0**、占用 **`MOVE` + `JUMP`**。
`GoalSelector` 同优先级按插入顺序执行、**控制位重叠则先启动者独占**：

- 入水 `canStart()` 为真 → 我们的 Goal 占据 `MOVE` → **`BreatheAirGoal` 无法运行** →
  被牵引入水的伙伴**不会主动上浮**（`SwimGoal` 只占 `JUMP`，不受 `MOVE` 影响）。
- 陆地上 `canStart()` 为假 → 原版目标正常。

**结论**：`rc.12` 里「与原版 SwimGoal 同优先级」的说法**指向了错误的类**，
实际风险在 `BreatheAirGoal` 与 `MOVE` 占用上。**本批没有改这里** ——
`CompanionMovementGoal` 的持有/跟随两态都依赖 `MOVE`，在没有实机验证的情况下收紧
是**猜测性放宽**，不属于本批的诚实范围。这一条应作为**实机验收项**，且**只限制在需要呼吸时让路**
（例如 `mob.isSubmergedInWater()` 且 `getAir() < maxAir` 时让出 `MOVE`），
不要在陆地上放宽。

## 正式 1.0.0 的前置

四模式及召回的**游戏验收**仍未开始。本批**没有启动过客户端**。
