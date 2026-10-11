# 1.0.0-rc.21 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.21，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc20.md`、`STATUS-rc19.md`、`STATUS-rc18.md`、`STATUS-rc17.md`、`STATUS-rc16.md`、`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` …
> 本批**只修跨维度执行顺序**，未扩大功能。

## 验证分层（不得互相替代）

| 类别 | 状态 |
|---|---|
| **自动测试** | **201 用例 / 0 失败 / 0 错误 / 28 个测试类** |
| **服务器加载** | **通过** —— 8 个 mixin 全部应用，零注入失败 |
| **客户端加载** | **本版本未复验**（rc.15 曾通过） |
| **实际行为验收** | **未完成** |

---

## 一、修复同 UUID 新旧实体的清理冲突（你指出，已在 rc.20 上复现）

### 反汇编得到的真实顺序（`Entity#teleportCrossDimension`）

```
EntityType.create(world, reason)   // 新实体，新 UUID
newEntity.copyFrom(oldEntity)      // uuid 被复制到新实体上
  ...readData / readCustomData     // 新实体读取 → 存储读取钩子在此触发
oldEntity.removeFromDimension()    // setRemoved(CHANGED_DIMENSION)
```

即**新实体先读、旧实体后移除**，而两者**共用同一个 UUID**。
读取侧建立终态后，旧实体的移除按 UUID **删除**了它 → **保护被抵消** → 新实体可被重新归因。

### 测试先失败（在 rc.20 上实际运行的结果）

```
ProjectileOwnerLifecycleTest > DIMENSION: the real order -- new entity reads, old entity is removed, ...
  the old entity's removal must NOT erase the new entity's terminal state ==> expected: not <null>
```

### 修复

- 新增 `STORAGE_AUTHORIZED` 标记：**刚由存储读入**的投射物会被标记为「存储保护中」。
- `onRemoved(id, destroying)` 改为按**移除是否销毁**分流：
  - **销毁型**移除且该 id 仍受存储保护 → **保留终态，只消耗该标记**
    （这正是「旧实体随维度切换销毁」的情形）；
  - 其他情形 → 正常释放（含**卸载型**移除）。
- **标记是消耗性的**，不是永久保留跨维度 UUID：实体一经重新赋主即消耗该标记
  （此时终态已可自证），新实体**最终被真正销毁**时会释放状态并清掉标记。
  **没有用永久保留所有跨维度 UUID 来规避清理。**
- 移除钩子传入 `reason.shouldDestroy()`；卸载型移除仍照常释放。

**注意（重要，已在注释与报告写明）**：
`removeFromDimension()` 调用的是 **`setRemoved(CHANGED_DIMENSION)`**，**不经过 `remove()`**，
而我们的移除钩子挂在 `remove()` 上。所以**在本版本里跨维度并不会触发移除钩子**，
这条路径的冲突在**当前映射**下不会发生。测试断言的是**两种可能都成立**（钩子触发或不触发），
使保护不依赖于「钩子恰好不被调用」这一巧合。

---

## 二、按真实顺序的回归（`crossDimensionOrderKeepsTheNewEntityNonAttributable`）

严格按 `create → copyFrom → 读取 → 旧实体移除 → 重复赋主 → 命中` 执行，覆盖你点名的六条：

| 要求 | 断言 |
|---|---|
| 新实体读取并失效 | 读取缝调用后状态存在且**为终态** |
| 旧实体以 `CHANGED_DIMENSION` 移除 | 移除调用后**状态仍存在、仍为终态**（这是 rc.20 失败处） |
| 新实体重复赋主 | **不得被重新归因** |
| 实际存储状态不可归因、命中不刷新进展 | `attributed()` 为 null；`lastProgressTick` **前后不变** |
| 新实体最终销毁后状态可释放 | 再次移除后条目**为空** |
| 新发射的其他箭不受影响 | 另一支箭**可归因**，且其命中**确实刷新进展** |

另新增/保留：`reloadedArrowStaysTerminal`、`restartIsCoveredByTheReadSeam`、
`removalsReleaseAndTheReadSeamProtects`。
**没有**只测「清理后再读取」—— 那正是被指出不覆盖本次顺序的写法。

---

## 三、报告与注释修正

**撤回我此前的两处相反描述**（你给的字节码事实成立）：

| 我之前写的 | 实际（已核实） |
|---|---|
| `getUuid()` 返回实体对象的引用语义 | **返回 `uuid` 字段**（`getfield uuid; areturn`） |
| `PersistentProjectileEntity.readCustomData` **不**调用父类，只挂父类会静默失效 | **它确实 `invokespecial ProjectileEntity.readCustomData`**，因此**两个读取钩子对箭都会触发** |

- 保留两个读取钩子的理由改为：**不依赖委托持续存在**；重复触发**由幂等吸收**
  （`onProjectileLoadedFromStorage` 重复执行写入相同的终态与相同的标记），并已断言。
- 契约测试同步改正：断言**子类确实委托父类**（此前断言的是相反结论）。
- **明确声明**：「读取钩子存在」**不等于**整个跨维度流程已通过 ——
  真正要验证的是**新实体读取与旧实体移除之间的顺序**，由上面的生命周期测试单独断言，
  且**实机仍未验证**。

---

## 四、真实行为验收（需人工，本批继续）

**不以测试数量替代实机结论。** 请记录：**版本号、场景、观察结果、对应日志**。
启动：`gradle runClient --args="--quickPlaySingleplayer devworld"`
（或把 rc.21 的 jar 放进**测试用**实例 `mods/`；需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。
**不要**使用正式存档。`config/noncreatureaffinity.json` 设 `"debugLogging": true`。

### A. 狼近战（先做）

| 场景 | 通过判据 |
|---|---|
| 持续**有效命中**（目标存活、距离合法）超过 200 刻（10 秒） | **仍正常战斗**；出现 `NO_PROGRESS` **才算异常** |
| **既无命中、也无接近进展** | **正常停滞退出** |
| 切换为**等待** | **停止追击**、不再替主人参战 |

日志：请把**结束原因行**贴回。目标死亡 / 模式切换 / 召回 / 受保护 / 跨维度导致的
`engagement ended` **属正常**，**不要求日志里没有它**。

### B. 骷髅持续射击

持续远程命中时**不因命中被判停滞**。

### C. 等待模式低氧

无目标 / 已有自卫目标两种情形：能上浮、**不追敌、不跟随**；
检查是否反复回到水下锚点形成「下潜—低氧—上浮」循环。

### D. 超过 10 格安全召回

9.9 格与**恰好 10 格****不召回**；**10.1 格****安全召回**到主人附近 2–3 格；
召回后**不马上**冲回旧目标；等待 / 漫游 15 格**不召回**
（**漫游仍应保护主人并协助攻击已有敌意的目标**）。

### E. 客户端加载

本版本能进入世界、本模组零 ERROR。

---

## 服务器加载验收记录（本批）

```
Mixing EntityAffinityMixin / ProjectileRemovalStampMixin        into net.minecraft.entity.Entity
Mixing CompanionAttackStampMixin / MobGoalAccess                into net.minecraft.entity.mob.MobEntity
Mixing PersistentProjectileLaunchStampMixin / ...Storage...     into ...PersistentProjectileEntity
Mixing ProjectileLaunchStampMixin / ...Storage...               into ...ProjectileEntity

mixins: 8   注入失败: none   本模组 ERROR: none   服务器: Done (0.568s)!
```

---

## 待实机验证的声明（不得当作已完成）

1. **跨维度流程** —— 生命周期测试已按真实顺序通过，**实机未验证**；
   且当前映射下跨维度**不触发**移除钩子，这一点是反汇编结论而非实机观察。
2. **重载后终态** —— 规则与接线测试已通过，**实机重载未复现**。
3. **远程实际命中续期** —— 未验证（赋主接线与规则测试已完成）。
4. **近战真实异常场景** —— 未在实机构造。

## 剩余限制

1. **实际行为验收：全部未完成**（A–E）。
2. **重载前射出的箭不再贡献进展**（有意取舍，见 rc.20）。
3. `setOwner` 以外的发射路径（三叉戟投掷形态、药水等）：不误续期，但也不续期。
4. **战斗能力是「代码白名单」，不是「行为已验证」** —— 不得写成「行为已验证」，
   不得把不支持战斗归因于玩家操作。
5. **低氧让路后由谁接管移动**只能实机确认。
6. 原地自卫命中判定、NeedsOfNature 动画占用、原版 owner-assist/报复目标重置：未实机确认。

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.21+1.21.11.jar` |
| 体积 / SHA256 | 261006 字节 / `3D6377F76C0DB705FAB2F8CBD85C8F51F378D11DDDE6BDCD474709DFE1E9ECA6` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 正式版前置

**四模式、召回、持续近战与远程行为验收完成前，保持候选版本，不发布正式 1.0.0。** 当前：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
