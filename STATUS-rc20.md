# 1.0.0-rc.20 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.20，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc19.md`、`STATUS-rc18.md`、`STATUS-rc17.md`、`STATUS-rc16.md`、`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` …
> 本批**只处理投射物重载**，**未改写 rc.19 已修好的交战清理规则**（保留）。

## 验证分层（不得互相替代）

| 类别 | 状态 |
|---|---|
| **自动测试** | **200 用例 / 0 失败 / 0 错误 / 28 个测试类** |
| **服务器加载** | **通过** —— 8 个 mixin 全部应用，零注入失败 |
| **客户端加载** | **本版本未复验**（rc.15 曾通过） |
| **实际行为验收** | **未完成** |

---

## 一、修正卸载状态方案

### 我上一版的错误（你指出，已核实并撤回）

我在 rc.19 把「**不主动删除**」写成了「**保证重载后保留**」。这是错的，有两条独立原因：

1. **`WeakHashMap` 根本不保证存续**。它的键是 `UUID` 对象，除了我传入时**没有任何地方持有强引用**，
   因此条目**在任意一次 GC 就可能消失** —— `getUuid()` 每次返回新实例，
   所谓「不删除」在 GC 面前毫无意义。
2. **即便换成强引用表也仍然不成立**。已核实 `ChunkSerializer.write` 只序列化
   **`entity.getUuid()`**（不是实体对象），所以**以 id 为键的归因无法跟随实体穿过存储**：
   卸载后内存里的条目对应的是一个已不在世界里的实体，重载回来的实体只有 id 相同而已。

**因此「保留重载前归因」需要的是持久化状态 + 处理服务器重启后代次重新开始的问题，
不是把 WeakHashMap 换成强引用表。** 已按你给的**保守方案**实现。

### 现行方案（三种情况明确区分）

| 情况 | 处理 | 结果 |
|---|---|---|
| **同一次服务器运行内** | 首次归因受保护；换主 / 命中已消费 / 交战结束 → **转终态且不可复活**；**清理路径绝不删除** | rc.19 已修好并**保留** |
| **区块卸载 → 重载** | 移除时**释放条目**；实体从存储**读回时置为终态** | 旧箭**不能**获得新代次 |
| **服务器重启** | 同上（内存存储本就随重启消失；读回时置终态） | 同上；同时避免「重启后**代次重新从 1 开始**」导致的误判 |

**实现要点**：

- 存储由 `WeakHashMap` 改为**强引用 `HashMap` + 显式释放**（销毁与卸载都释放），
  **不依赖 GC 时机**。
- 新增**存储读取缝**：`ProjectileStorageStampMixin`（`ProjectileEntity.readCustomData`）与
  `PersistentProjectileStorageStampMixin`（`PersistentProjectileEntity.readCustomData`）。
  **两个都需要** —— 映射字节码确认两个投射物类**各自声明** `readCustomData(ReadView)`
  （1.21.11 已是 View 式 API，旧的 `readNbt` 不存在），子类**不调用**父类实现。
  箭是 `PersistentProjectileEntity`，只挂父类会**静默失效**。
  该缝覆盖**区块重载、世界重载、维度切换、服务器重启**四条路径。
- `ProjectileStamps.shouldReleaseOnRemoval(boolean)` 现在**两种情况都释放**
  （销毁：实体没了；卸载：其重载状态本就是终态）。**保护重载的是读取侧，不是保留条目** ——
  这一点在注释与报告里都写明，避免再次把「不删除」当成「保留」。

### 明确的代价（记录在案）

**重载前射出的箭不再贡献战斗进展**，但**不会错误续期新交战**。
这是刻意的取舍：漏一次续期只是让战斗**略微偏早**结束，
而**错误续期**会让一场已经结束的战斗继续活着。

---

## 二、测试真正的重载边界

新增 3 项，走**生产缝**而非只检查布尔：

| 测试 | 覆盖 |
|---|---|
| `reloadedArrowCanNeverBeAttributed` | **保存 → 清空运行时存储 → 从存储重建（调用生产缝）→ 同目标新交战 → 重复赋主 → 命中**。断言旧箭**不可归因**、**不刷新 `lastProgressTick`**、**新箭仍能刷新进展** |
| `reloadedArrowStaysTerminal` | 存储读取可多次发生、赋主可多次发生，**仍为终态** |
| `restartIsCoveredByTheReadSeam` | **代次重新开始**（11 → 1）也不能让重启前的箭看起来是当前的 |
| `removalsReleaseAndTheReadSeamProtects` | 卸载与销毁都释放条目；**是读取缝在阻止重新归因** |

旧的 `unloadKeepsTheAttribution` 与新方案矛盾，**已删除**（它断言的正是被撤回的说法）。

---

## 三、单独检查跨维度

- `CHANGED_DIMENSION` 的字节码旗标为 **`shouldDestroy = true` / `shouldSave = false`**：
  维度切换会**销毁原实体**，目标维度里的实体是**重建**的。
- 重建路径会**读取存储**，因此**存储读取缝同样覆盖维度切换**：新实体在赋主或命中归因之前
  已被置为终态。
- **没有**把「`shouldSave() == false`」当作「投射物不可能在别处继续存在」的证明 ——
  这类推断正是本批要避免的。链路上的保护来自**读取侧的显式失效**，与销毁/保存的语义无关。
- 维度切换后**代次校验**仍然成立（同一场战斗的代次未变，而该箭已是终态，根本不会被计入）。

---

## 四、报告纠正

- **撤回**「卸载状态保证保留直到销毁或服务器停止」—— 实现与测试都不支持，已从
  `STATUS-rc19.md` 的结论中作废，并在本文件明确记录撤回原因。
- 现在**明确区分三种情况**：**同次运行内的终态保护 / 区块重载 / 服务器重启**（见第一节表格）。
- `NO_PROGRESS` 的判读沿用 rc.19：**只有持续有效命中或持续接近时仍判停滞才异常**；
  无进展退出是正常行为；目标死亡 / 模式切换 / 召回 / 受保护 / 跨维度都是**合法结束原因**，
  **不要求日志里没有 `engagement ended`**。

---

## 服务器加载验收记录（本批）

```
Mixing EntityAffinityMixin                       into net.minecraft.entity.Entity
Mixing ProjectileRemovalStampMixin               into net.minecraft.entity.Entity
Mixing CompanionAttackStampMixin                 into net.minecraft.entity.mob.MobEntity
Mixing MobGoalAccess                             into net.minecraft.entity.mob.MobEntity
Mixing PersistentProjectileLaunchStampMixin      into net.minecraft.entity.projectile.PersistentProjectileEntity
Mixing PersistentProjectileStorageStampMixin     into net.minecraft.entity.projectile.PersistentProjectileEntity
Mixing ProjectileLaunchStampMixin                into net.minecraft.entity.projectile.ProjectileEntity
Mixing ProjectileStorageStampMixin               into net.minecraft.entity.projectile.ProjectileEntity

@Inject::...$invalidateStampFromStorage(ReadView, CallbackInfo)              has 0 override(s) in child classes
@Inject::...$invalidatePersistentStampFromStorage(ReadView, CallbackInfo)    applied
注入失败: none        本模组 ERROR: none        服务器: Done (1.154s)!
```

---

## 五、真实行为验收（**本批开始，需人工**）

**不再以测试数量增长代替行为验收。** 按序执行，未完成则不得视为通过。

启动：`gradle runClient --args="--quickPlaySingleplayer devworld"`
（或把 rc.20 的 jar 放进**测试用**实例 `mods/`；需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。
**不要**使用正式存档。`config/noncreatureaffinity.json` 设 `"debugLogging": true`。
本模组的行以 `[NoN Affinity]` 开头。

### A. 狼近战（先做）

| 步骤 | 通过判据 |
|---|---|
| 持续**有效命中**（目标存活、距离合法）超过 `companionEngageStallTicks`（默认 200 刻 = 10 秒） | **仍正常战斗**；此情形下出现 `NO_PROGRESS` **才算异常** |
| **既无命中、也无接近进展** | **正常停滞退出**（预期一次 `NO_PROGRESS`） |
| 切换为**等待** | **停止追击**、不再替主人参战 |

日志：把**结束原因行**一并贴回。目标死亡 / 模式切换 / 召回导致的 `engagement ended` **属正常**。

### B. 骷髅远程

持续远程命中（目标存活、距离合法）时**不因命中被判停滞**。

### C. 等待模式低氧

- **无目标**：等待模式下带狼下潜 → 能上浮呼吸，且**不跟随主人**。
- **已有自卫目标**：有敌人的等待狼按进水里 → 能上浮，且**不追击那个敌人**。
- **恢复**：回到下令点并保持等待；**检查是否反复回到水下锚点形成「下潜—低氧—上浮」循环**。

### D. 超过 10 格安全召回

- 9.9 格 / **恰好 10 格** → **不召回**；**10.1 格** → **安全召回**到主人附近 2–3 格。
- 召回后**立即**观察：**不马上**冲回原来的敌人。
- 等待 / 漫游走到 15 格 → **不召回**（**漫游仍应保护主人并协助攻击已有敌意的目标**）。

### E. 客户端加载

本版本能进入世界、本模组零 ERROR。

---

## 剩余限制

1. **实际行为验收：全部未完成**（A–E）。
2. **重载前射出的箭不再贡献进展** —— 见第一节的代价说明。
3. **远程实际命中续期**未验证。分三层：赋主接线（已实现）/ 规则与接线测试（已通过）/ 实际命中（未验证）。
   `setOwner` 以外的发射路径（三叉戟投掷形态、药水等）：不误续期，但也不续期。
4. **近战真实异常场景**未在实机构造（清理逻辑与异常路径测试已完成）。
5. **战斗能力是「代码白名单」，不是「行为已验证」** —— 不得写成「行为已验证」，
   不得把不支持战斗归因于玩家操作。
6. **低氧让路后由谁接管移动**只能实机确认。
7. 原地自卫命中判定、NeedsOfNature 动画占用、原版 owner-assist/报复目标重置：未实机确认。
8. **卸载时条目被释放**：这是保守方案的直接结果，不是缺陷；重载后的箭由读取缝置为终态。

---

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.20+1.21.11.jar` |
| 体积 / SHA256 | 260624 字节 / `D0559D41CD46D1C668E45FF58DE2F7571B0214CC5FDF15E9E4283616DFECBA32` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 正式版前置

**四模式、召回、持续近战与远程行为验收完成前，保持候选版本，不发布正式 1.0.0。** 当前：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
