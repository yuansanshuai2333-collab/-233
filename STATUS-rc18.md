# 1.0.0-rc.18 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.18，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc17.md`、`STATUS-rc16.md`、`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` …
> **rc.17 的近战整方法包装方向正确，本批保留该修复**，集中修投射物生命周期，未扩大功能范围。

## 验证分层（不得互相替代）

| 类别 | 状态 |
|---|---|
| **自动测试** | **191 用例 / 0 失败 / 0 错误 / 28 个测试类** |
| **服务器加载** | **通过** —— 6 个 mixin 全部应用，零注入失败 |
| **客户端加载** | **本版本未复验**（rc.15 曾通过） |
| **实际行为验收** | **未完成** —— 见「最小实机验收清单」 |

---

## 一、区分「从未记录」与「已失效/已消费」（**本批核心修复**）

### 缺陷

上一版在**换主**或**命中已消费**时**删除**记录。删除后的投射物与**从未见过**的投射物无法区分，
于是下一次 `setOwner`（原版对同一支投射物会调用多次）把它当成**新箭**，
**赋予当前交战的代次** —— 旧箭的命中因此可能续期一场它从未参与的战斗。这正是这套机制要防的错误。

### 修复：单向状态机 + 终态

`ProjectileStamps` 改为显式状态：

| 状态 | 含义 |
|---|---|
| 无记录 | 从未见过 |
| `ATTRIBUTED` | 首次合法建立：发射者、目标、代次、发射刻 |
| `INVALIDATED` | **终态**：换主 / 被玩家或未托管生物取得 / 命中已消费 / 托管但当时无交战目标 |

规则：

- **首次合法建立后，发射者、目标、代次不可被普通 `setOwner` 改写。**
- **同主重复赋值保持原记录**（含原始发射刻）。传入的代次描述的是**现在**，不是这支投射物的发射时刻。
- **换主 → 终态**；**消费 → 终态**；**托管但无交战目标 → 终态**（不留下「没有记录」的假象）。
- **终态不可复活**：之后任何 `setOwner`（包括还给伙伴）都不会重新归因。
- **交战结束不需要额外失效**：戳保留发射时的代次，而该代次一旦不再有效，
  `recordHitAtGeneration` 自然拒绝旧箭。清理路径只做内存回收，**不削弱规则**。
- **实体销毁时释放状态**（`ProjectileRemovalStampMixin`），使存储量与**存活投射物**成正比，
  **不使用无限增长的永久 UUID 黑名单**。
- 非伙伴主人**传入 null 而不是提前 return**：跳过会让投射物保持「从未见过」，
  之后交回伙伴就会创建首次归因。契约测试断言这一写法。

### 本批由服务器启动抓到的真实缺陷（重要）

移除钩子最初写成注入 `ProjectileEntity` 的 `remove`。但
**`ProjectileEntity` 与 `PersistentProjectileEntity` 都没有声明该方法** —— 它来自 `Entity`。
因此注入**没有目标**，`defaultRequire: 1` 让 Mixin **在加载时直接报错并中止**：

```
Mixin apply for mod noncreatureaffinity failed ... ProjectileLaunchStampMixin
Critical injection failure: @Inject annotation on noncreatureaffinity$releaseStampOnRemoval
could not find any targets matching 'remove' in ProjectileEntity
```

**这正是不复跑服务器就会带着「启动即崩」的构建走出去的例子。**
修复：新增 `ProjectileRemovalStampMixin`，混入 `Entity` 并做 `instanceof ProjectileEntity` 判断；
新增断言 `removeIsDeclaredOnEntityOnly`：`Entity` 必须声明 `remove`，
两个投射物类**必须没有**声明它（若将来重写，钩子要重新检查，避免变成静默 no-op）。
复跑服务器：6 个 mixin 全部应用，零失败。

---

## 二、补齐真实调用顺序的回归测试（`ProjectileOwnerLifecycleTest`）

全部走**生产入口** `ProjectileStamps.observeOwnerAssignment`（两个钩子共用的唯一解释器），
并在需要处**先经过生产清理**再赋值；**没有**「在未清理的记录上调用两次 `record()`」这种代用测试。

| 交接点名 | 测试 | 断言 |
|---|---|---|
| 1. A 发射 → 换主 B → **连续两次**赋主钩子 | `handOverStaysInvalidatedAcrossRepeatedHooks` | 两次之后**仍为终态**，且**记录仍在**（不是被删除） |
| 2. A 发射 → **正式结束交战并清理** → 同目标重新接战 → 旧箭重复赋主 | `oldArrowIsNotRevivedByANewEngagement` | 旧箭命中**不得续期**新交战；原始代次永远被拒 |
| 2b. 未清理时同目标重新接战 → 重复赋主 | `oldArrowKeepsItsGenerationWithoutCleanup` | 发射代次**不被覆盖**，命中仍被拒 |
| 3. 命中记录**已消费** → 重复赋主 | `consumedHitCannotBeAuthorisedAgain` | 不得再次授权；`attributed()` 为 null |
| 4. 换成**玩家 / 空主人 / 未托管生物**，再换回伙伴 | `nonCompanionOwnersBlockPermanently`、`freshProjectileWithNoOwnerIsBlocked` | 不能绕过失效规则 |
| 5. **新创建的另一支箭**仍可正常归因 | `freshProjectileStillWorks` | 归因正确，且该场战斗的命中**确实计入进展** |
| 附加 | `removalReleasesState`、`noFightMeansNoAttribution`、`projectilesAreIndependent`、`strayHitIsNotCredited` | 存储有界、无交战不打戳、多枚互不干扰、误中不计数 |

---

## 三、最小近战验收（**需人工，可用 rc.17 或 rc.18**）

| 场景 | 通过判据 |
|---|---|
| 目标**存活、距离合法、持续有效命中** | 超过 `companionEngageStallTicks`（默认 200 刻）**仍正常战斗** |
| **无有效命中、也无接近进展** | **正常停滞退出**（出现一次 stalled / `NO_PROGRESS` 行） |
| **切换为等待** | **停止追击**、不再替主人参战 |

**日志判读要求（明确）**：**不要求完全没有任何 `engagement ended` 日志**。
合法结束原因包括：

- **目标死亡**（`TARGET_GONE`）
- **模式切换**（切换会结束当前交战，属预期）
- **召回**（`OUT_OF_RANGE` + 返回窗口）
- 目标变为受保护 / 跨维度

**只有 `NO_PROGRESS`（停滞）才表示本批修复没生效。** 请把结束原因行一并贴回。

日志：`.minecraft/logs/latest.log` 整段（`debugLogging: true`），本模组的行以 `[NoN Affinity]` 开头。

---

## 四、报告要求（已执行）

- 「换主后双方都无法计入」**改为待验证**（不再是已完成的声明），
  直到**完整双钩子路径与交战结束路径**在实机通过：
  **规则与接线测试已通过**（见上表），**实机尚未验证**。
- 继续区分**规则测试 / 接线测试 / 客户端加载 / 实际行为验收**四类。
- **远程描述分三层**（不得混为一谈）：

  | 层面 | 状态 |
  |---|---|
  | 赋主接线（`setOwner` 打戳 + 父类/子类两个钩子 + 移除回收） | **已实现**，服务器加载确认应用 |
  | 规则 / 接线测试（本批 12 项生命周期回归） | **已通过** |
  | **实际命中验证**（骷髅射箭 → 伤害回调 → 续期） | **未验证** |

  因此**不写成「已全面支持远程」**。未覆盖 `setOwner` 以外的发射路径
  （三叉戟投掷形态、药水等）：**不误续期，但也不续期**。
- 近战异常清理仍为**待验证**（清理逻辑已实现、异常路径有测试、包装范围已由加载与结构性测试确认，
  **但真实异常场景未在实机构造**）。

---

## 服务器加载验收记录（本批）

```
Mixing EntityAffinityMixin                    into net.minecraft.entity.Entity
Mixing ProjectileRemovalStampMixin            into net.minecraft.entity.Entity
Mixing CompanionAttackStampMixin              into net.minecraft.entity.mob.MobEntity
Mixing MobGoalAccess                          into net.minecraft.entity.mob.MobEntity
Mixing PersistentProjectileLaunchStampMixin   into net.minecraft.entity.projectile.PersistentProjectileEntity
Mixing ProjectileLaunchStampMixin             into net.minecraft.entity.projectile.ProjectileEntity

@Inject::...$releaseProjectileStamp(Entity$RemovalReason, CallbackInfo)  has 0 override(s)
注入失败: none        本模组 ERROR: none        服务器: Done (1.065s)!
```

---

## 最小实机验收清单（需人工执行，按序）

启动：`gradle runClient --args="--quickPlaySingleplayer devworld"`
（或把 rc.18 的 jar 放进**测试用**实例 `mods/`；需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。
**不要**使用正式存档。`config/noncreatureaffinity.json` 设 `"debugLogging": true`。

1. **狼持续近战** —— 见第三节（注意合法的结束原因不算失败）。
2. **骷髅持续射击** —— 远程持续命中时**不因命中被判停滞**。
3. **等待模式低氧** —— 无目标 / 已有自卫目标两种情形：能上浮、**不追敌、不跟随**；
   检查是否反复回到水下锚点形成「下潜—低氧—上浮」循环。
4. **超过 10 格安全召回** —— 9.9 与 10 格**不召回**、10.1 格**安全召回**、召回后**不马上**冲回旧目标；
   等待/漫游 15 格**不召回**（**漫游仍应保护主人并协助攻击已有敌意的目标**）。
5. **客户端加载** —— 本版本能进入世界、本模组零 ERROR。

---

## 剩余限制

1. **实际行为验收：全部未完成**（上列 1–5）。
2. **换主后双方都无法计入** —— 规则与接线测试已通过，**实机未验证**。
3. **远程实际命中未端到端验证**；`setOwner` 以外的发射路径未覆盖（不误续期，但也不续期）。
4. **近战真实异常场景未实机构造**。
5. **低氧让路后由谁接管移动**只能实机确认（字节码已确认 `GoalSelector` 占用/替换规则）。
6. **战斗能力是「代码白名单」，不是「行为已验证」**：`hasCombatCapability` 对敌对生物 / 狼 / 猫
   返回 true 是**代码事实**；实机追击并命中**未验证**。不得写成「行为已验证」，
   不得把不支持战斗归因于玩家操作。
7. 原地自卫命中判定、NeedsOfNature 动画占用、原版 owner-assist/报复目标重置：未实机确认。

---

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.18+1.21.11.jar` |
| 体积 / SHA256 | 258688 字节 / `B62DB5551491E888BE6308CE2E5679C71C2120BAC8B403E4E726E0F3E5E33B12` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 正式版前置

**四模式、召回、持续近战与远程行为验收完成前，保持候选版本，不发布正式 1.0.0。** 当前：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
