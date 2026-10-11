# 1.0.0-rc.16 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.16，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` … `STATUS-rc6.md`(**不可交付**)。
> 本批按交接清单完成第 1、3、5 项；第 2、4 项是**真实行为验收**，需人工操作，已给出完整步骤与日志要求。

## 验证分层（本批最终记录）

| 类别 | 状态 |
|---|---|
| **自动测试** | **186 用例 / 0 失败 / 0 错误 / 28 个测试类**（rc.15 为 165 / 25） |
| **服务器加载** | **通过** —— 5 个 mixin 全部加载，零注入失败 |
| **客户端加载** | **通过**（rc.15 已做：进入 devworld、玩家登录、本模组零 ERROR） |
| **实际行为验收** | **未完成** —— 见第 2、4 项的人工步骤 |

**四者互不替代。** 自动测试通过不等于能加载，加载通过不等于行为正确。

---

## 第 1 项：统一 STATUS 报告与修正验收标准

- 测试数字按最终记录改为 **186 项 / 28 个测试类**（本批新增后重算，取自 `build/test-results`）。
- 修正**验收标准**：**漫游仍应保护主人，并协助攻击已有敌意的目标**；
  「不再替主人参战、停止追击」的测试**必须使用「切换为等待」**，不能用漫游。
  rc.15 报告里「跟随→漫游后不再替主人参战」的写法是**错的**，已删改。

---

## 第 2 项：一只狼的真实近战（**未完成，需人工**）

这一项**必须**经过真实 `MobEntity` 攻击 → 伤害回调 → 控制器，
**不能用直接调用标记方法的测试代替**。本环境无法操作客户端内的战斗，故交给你执行。

### 步骤与判据

| 步骤 | 通过判据 | 观察什么日志 |
|---|---|---|
| 狼在**主动**模式，找一只僵尸持续近战 | 战斗时长**明显超过** `companionEngageStallTicks`（默认 200 刻 = 10 秒）仍不退出 | 是否出现 `engagement stalled while evidence still arrived` 或 `engagement with ... ended: NO_PROGRESS` |
| 同上，狼**持续命中** | **有效命中刷新进展**，不会边打边被判停滞 | 不应出现任何 `NO_PROGRESS` / `stalled` 行 |
| 同上，狼**站着不动也不命中**（打一只无敌/无伤的怪） | 约 10 秒后**确实放弃** | 应出现一次 stalled 行，随后**不再**重开同一目标 |
| 放弃后**继续**站在旁边 | **本轮及下一轮都不重开**同一目标 | 不应出现对同一 UUID 的 `restored cleared target` |

**需要的日志**：`.minecraft/logs/latest.log` 整段（含 `debugLogging: true`），
以及狼战斗起止的**大致时间点**。本模组的行以 `[NoN Affinity]` 开头。

**如果命中不刷新进展**，请把这段日志发回：它意味着打戳路径或伤害来源判定有实际缺陷，
而不是规则问题。

---

## 第 3 项：远程投射物发射标记（**已实现**）

### 实现要点

- **在真正发射时打戳**：新增两个 mixin 挂在 `setOwner(Entity)` 上 ——
  `ProjectileLaunchStampMixin`（`ProjectileEntity`）与
  `PersistentProjectileLaunchStampMixin`（`PersistentProjectileEntity`）。
  **两个都需要**：子类自身实现了 `setOwner`，只注入父类**不会**对箭生效。
  交接清单要求「先明确发射路径」——经映射 jar 确认：
  `ArrowEntity extends PersistentProjectileEntity extends ProjectileEntity`，
  **骷髅射箭走的就是 `PersistentProjectileEntity.setOwner`**。
- **把伙伴 UUID、目标 UUID、交战代次绑定到具体投射物**（`ProjectileStamps`，以投射物 UUID 为键的
  弱键表，因此未命中的箭不会留住条目）。
- **结算时校验的是投射物上记录的发射代次**，不是命中时的当前代次。
  `onCompanionProjectileHit` 把 `stamp.generation()` 交给
  `EngagementState.recordHitAtGeneration(...)`，与近战共用同一处代次判定。

### 覆盖的场景（全部有测试）

| 场景 | 测试 |
|---|---|
| 同场有效命中续期 | `sameFightHitRenews` |
| 旧代次同目标命中不续期 | `oldGenerationHitDoesNotRenew` |
| 误中其他目标不续期 | `strayHitIsNotCredited` |
| 多枚在途投射物互不覆盖 | `multipleProjectilesAreIndependent` |
| 两伙伴互不干扰 | `companionsDoNotInterfere` |
| 发射代次被真正记录（而非命中时读取） | `stampCapturesTheLaunchGeneration` + `damageSiteUsesTheProjectileStamp` |

### 覆盖的物种与路径（明确）

| 项 | 状态 |
|---|---|
| **骷髅的箭** | **已覆盖**（`ArrowEntity` → `PersistentProjectileEntity.setOwner`） |
| 其他 `ProjectileEntity` 子类（雪球、鸡蛋、末影珍珠等） | **已覆盖发射打戳**，但这些本身不造成近战式伤害 |
| **三叉戟投掷形态** | **未覆盖**：它不走 `setOwner` 这条赋主路径 |
| 药水等由其他代码路径生成并赋主的投射物 | **未覆盖** |
| 模组自定义发射器 | **未覆盖** |

**未覆盖的路径不会误续期**（没有戳就不计为进展），但**也不会续期** ——
这是**偏安全的方向**，同时是**明确的限制**，不是「已全面支持远程」。

> 仍**不做**的声明：本批**没有**在实机验证骷髅的箭确实续期战斗。
> 加载层面已确认两个 mixin 成功应用，逻辑层面已用测试锁定，
> **实机确认仍是未完成项**（见第 2 项同类的观察方式：让骷髅伙伴持续命中，看是否被判停滞）。

---

## 第 4 项：低氧 WAIT 验收（**未完成，需人工**）

| 场景 | 通过判据 | 失败表现 |
|---|---|---|
| **无目标**：等待模式下把狼带下水下潜 | 能上浮呼吸；**不跟随主人** | 上浮后开始朝主人走 |
| **已有自卫目标**：等待模式下有敌人，再把它按进水里 | 能上浮；**不追击那个敌人** | 一边上浮一边靠近敌人 |
| **恢复**：上浮呼吸后回到下令点 | 回到等待锚点并保持；**不追敌、不跟随** | 变成跟随 |
| **下潜—低氧—上浮循环** | 恢复后**不应反复**回到水下锚点又触发低氧 | 反复上浮下潜抖动 |

第四行是重点：若锚点在水下，恢复等待后可能被驱回水下，再次低氧，形成循环。
**若出现，请记录**锚点位置、水面高度、以及 `no path back to anchor` 之类的行，
再针对**实际控制路径**修复（不是调整阈值掩盖）。

---

## 第 5 项：攻击异常清理（**已实现，不再是未完成**）

交接清单要求：若实现调用包装，应使用**真正的 finally**并**验证异常路径**，不吞异常。
本批照此实现：

- 新增 `AttackStampHandler.runWithStamp(...)`：`try { body } finally { 清理 }`。
  **`finally` 是真语义**，正常返回、返回 false、抛 `Throwable` 都会清理。
- **不捕获任何异常**：`finally` 之外没有 `catch`，原始 throwable 原样向上传播，
  不包装、不替换、不吞掉。测试 `productionHandlerUsesARealFinally` 明确断言**源码里没有 `catch (`**。
- mixin 改为 `@Redirect` 包住 `tryAttack` 的**方法体起点**，因此**生产环境跑的就是这个 `finally`**，
  测试驱动的是同一个实现，不是为测试写的副本。
- **异常路径有测试**：`AttackStampExceptionPathTest` 用真实令牌驱动
  「抛 `RuntimeException` → 戳被清理、异常原样抛出」「抛 `Error` 同样清理」
  「正常返回 / 返回 false 也清理」。
  （纯 JUnit 进程无法链接 `MobEntity`，因此通过 `MeleeAttackStamps.clearOnAttackFinished` 这一
  **按 id 的清理入口**驱动 —— 它委托给与生产相同的 `clearToken`，只有一份实现。）
- **已删除**所有「`RETURN` 在抛异常时也执行」的错误描述。`RETURN` 注入从此不再使用。
- 同刻校验（`launchedTick == nowTick`）**保留**，作为**补充防护**，不再声称它是异常路径的解法。

---

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.16+1.21.11.jar` |
| 体积 / SHA256 | 255555 字节 / `52690720E95E96B301E196A75B95614A23788F9254E0E5B12E7D75131535D098` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

### 服务器加载验收记录（本次）

```
Mixing EntityAffinityMixin                        into net.minecraft.entity.Entity
Mixing CompanionAttackStampMixin                 into net.minecraft.entity.mob.MobEntity
Mixing MobGoalAccess                              into net.minecraft.entity.mob.MobEntity
Mixing PersistentProjectileLaunchStampMixin       into net.minecraft.entity.projectile.PersistentProjectileEntity
Mixing ProjectileLaunchStampMixin                 into net.minecraft.entity.projectile.ProjectileEntity

ProjectileLaunchStampMixin -> @Inject::...$stampProjectileLaunch(...)  has 0 override(s) in child classes
注入失败: none
本模组 ERROR: none
服务器: Done (0.284s)!
```

- `CompanionAttackStampMixin` 现在是 `@Redirect`；`defaultRequire: 1` 表示注入点不匹配会**启动时失败**，
  日志中**零注入失败**即证明它已成功应用。
- 三个投射物/攻击 mixin 均**已注册并应用**。

### 客户端加载验收

沿用 rc.15 的结论（进入 devworld、玩家登录、本模组零 ERROR）。
rc.16 只改了 mixin 与纯逻辑，**客户端加载需在下次实机时一并复看**。

---

## 剩余限制与阻塞项（明确）

1. **实际行为验收：全部未完成**（第 2、4 项 + 骷髅远程的实机确认）。
2. **三叉戟投掷形态与药水等投射物路径未打戳** —— 不误续期，但也不续期。
   若正式版要支持这些，需按同样方式接入其真实发射路径。
3. **近战异常路径未在实机构造** —— 逻辑路径已有测试，但游戏内触发一次真实异常不属于本批范围。
   （注意：这**不再**是「清理逻辑未完成」；清理已实现并验证，只是未在实机制造异常。）
4. **低氧让路后由谁接管移动**只能实机确认：字节码已确认 `GoalSelector` 的占用/替换规则
   （`PrioritizedGoal.canBeReplacedBy` 优先级相等时返回 false），
   「让路后 `BreatheAirGoal` / `SwimGoal` 是否真的先于其他 MOVE 目标启动」需实测。
5. **战斗能力是「代码白名单」，不是「行为已验证」**：`hasCombatCapability` 对
   敌对生物 / 狼 / 猫返回 true 是**代码事实**；实机追击并命中**未验证**。
   **不得**把「在白名单里」写成「行为已验证」；**不得**把不支持战斗归因于玩家操作。
6. **原地自卫命中判定**（`isInAttackRange` + `canSee`，每秒 1 次）未实机验证。
7. **NeedsOfNature 动画期间占用**、**原版 owner-assist/报复目标是否在两次 tick 间重置目标**：未实机确认。

---

## 正式版前置

**四模式、召回、持续近战与远程行为验收完成前，保持候选版本，不发布正式 1.0.0。**
当前状态：**不满足**。

---

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
- 人工验收启动方式：`gradle runClient --args="--quickPlaySingleplayer devworld"`，
  或把 rc.16 的 jar 放进**测试用**实例的 `mods/`（需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。
