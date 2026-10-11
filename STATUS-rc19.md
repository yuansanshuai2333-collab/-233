# 1.0.0-rc.19 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.19，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc18.md`、`STATUS-rc17.md`、`STATUS-rc16.md`、`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` …
> 本批**只修投射物交战清理这一条链路**，未扩大功能范围。

## 验证分层（不得互相替代）

| 类别 | 状态 |
|---|---|
| **自动测试** | **196 用例 / 0 失败 / 0 错误 / 28 个测试类** |
| **服务器加载** | **通过** —— 6 个 mixin 全部应用，零注入失败 |
| **客户端加载** | **本版本未复验**（rc.15 曾通过） |
| **实际行为验收** | **未完成** |

---

## 一、修复生产清理（`clearForCompanion` → `markEngagementEnded`）

### 缺陷（你指出，已核实）

`ProjectileStamps.clearForCompanion()` 用的是 `removeIf`，**删除**该伙伴的有效记录。
**删除后的记录与「从未见过」不可区分**，于是仍然在飞的旧箭在下一次 `setOwner` 时被当成新箭、
**重新建立归因并拿到当前交战的代次**。这与我在 rc.18 修的删除缺陷是**同一类**，我漏在了清理路径上。

### 修复（采用你给的更明确方案：转终态）

- 方法改名为 **`markEngagementEnded(UUID)`**，语义与实现一致：
  **把该伙伴的有效记录转为 `INVALIDATED` 终态，绝不删除**。
- **已经终态的条目不动**：删掉它们会丢掉「阻止被重新建立」的那层保护。
- 选择**转终态**而非「保留原归因」的理由：转终态**不依赖代次比较是否发生**，
  即使将来某场战斗复用了相同代次也照样拒绝 —— 两者都安全，这是更强的那个。
- `CompanionCombatController.disconnect/forget` 也改用同一方法：绑定解除时其箭可能仍在空中，
  删掉会让它们看起来像从未见过的新箭。
- **内存不因此增长**：条目仍由**真正销毁**投射物时释放（见第三节）。
- 注释、方法名、报告三者已与实现对齐；已无任何「删除后允许重建」的路径。

---

## 二、修复错误测试（**先确认旧实现上失败，再修到通过**）

`oldArrowIsNotRevivedByANewEngagement` 原先**允许重新归因**，还用测试自己记下的 `GEN_ONE` 检查命中，
**绕开了实际错误**。已重写为严格执行你指定的顺序：

**首次归因 → 生产交战清理 → 同目标新交战 → 同一旧箭再次赋主 → 从存储读取实际状态 → 按生产命中路径判断**

- 断言**读取存储里的实际状态**，不使用测试预先保存的旧代次；
- 断言旧箭**不可归因**；
- 断言**记录仍然存在**（不是被删除 —— 这正是旧实现的破口）；
- 断言命中**不刷新 `lastProgressTick`**（前后各读一次，这才是「命中是否计入」的真实含义）；
- **删除**了「重新归因也可接受」的分支；
- 新增 `2c`：**新创建的另一支箭仍可正常刷新进展**，确保修复没有误伤正常远程攻击。

### 先确认失败（关键证据）

临时把清理改回「删除」行为后，新测试**确实失败**，且失败信息正是该缺陷：

```
ProjectileOwnerLifecycleTest > 2. engagement ends, is cleaned, the fight restarts: ...
  an old arrow must not be attributable after its own fight ended and a new one began
  expected: <null> but was: <State[ownerId=aaa...a, targetId=ccc...c, generation=3, launchedTick=140, valid=true]>
ProjectileOwnerLifecycleTest > the engagement cleanup INVALIDATES live records instead of deleting them
  the record must still EXIST after the cleanup
2 tests completed, 2 failed
```

**旧箭被重新归因，并且拿到了新交战的代次 3。** 恢复修复后全部通过。

---

## 三、核对移除与重载边界

`Entity.remove` **不等于永久销毁**。已核对 `Entity$RemovalReason` 的真实取值与语义：

| 取值 | `shouldSave()` | 含义 |
|---|---|---|
| `UNLOADED_TO_CHUNK` | true | **区块卸载，实体保留待重载** |
| `UNLOADED_WITH_PLAYER` | true | **随玩家卸载，实体保留** |
| `KILLED` / `DISCARDED` / `CHANGED_DIMENSION` | false | **真正销毁** |

**修复**：移除钩子改为按 `shouldSave()` 分流：

- **保存型移除（卸载）→ 保留归因**。若在这里忘掉，重载后的箭就会变成「从未记录」，
  下一次 `setOwner` 就可能把它归因到当时的战斗 —— 与前面同一类缺陷。
  它是**同一实体在继续**，保留是正确的；若期间代次已变，**代次校验仍会拒绝**它。
- **销毁型移除 → 释放状态**，从而内存与**仍有意义的投射物**成正比。

分流决策 `shouldReleaseOnRemoval(boolean)` 是**纯函数**，由普通测试覆盖，
而不是只能靠实机观察。新增测试：`unloadKeepsTheAttribution`、`destroyReleasesTheAttribution`。

---

## 四、报告纠正

- **撤回**上一版「正式结束交战并清理后，旧箭不得续期已验证」的结论 ——
  该结论当时建立在错误测试之上。现在改述为：
  **规则与接线测试已通过（含先失败后修复的证据）**，**实机仍属未验证**。
- **`NO_PROGRESS` 不再一概视为修复失败**。判据改为：

  | 情形 | 判定 |
  |---|---|
  | **持续有效命中**或**持续接近**时仍被判停滞 | **异常** —— 说明续期链路没生效 |
  | **既无命中也无接近进展**时退出 | **正常行为**（这正是停滞机制的设计目的） |
  | 目标死亡 / 模式切换 / 召回 / 目标变为受保护 / 跨维度 | **合法结束原因，不是失败** |

  **不要求日志里完全没有 `engagement ended`。**

---

## 服务器加载验收记录（本批）

```
Mixing EntityAffinityMixin                    into net.minecraft.entity.Entity
Mixing ProjectileRemovalStampMixin            into net.minecraft.entity.Entity
Mixing CompanionAttackStampMixin              into net.minecraft.entity.mob.MobEntity
Mixing MobGoalAccess                          into net.minecraft.entity.mob.MobEntity
Mixing PersistentProjectileLaunchStampMixin   into net.minecraft.entity.projectile.PersistentProjectileEntity
Mixing ProjectileLaunchStampMixin             into net.minecraft.entity.projectile.ProjectileEntity

注入失败: none        本模组 ERROR: none        服务器: Done (0.265s)!
```

---

## 五、继续进行的实机验收（需人工）

启动：`gradle runClient --args="--quickPlaySingleplayer devworld"`
（或把 rc.19 的 jar 放进**测试用**实例 `mods/`；需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。
**不要**使用正式存档。`config/noncreatureaffinity.json` 设 `"debugLogging": true`。
本模组的行以 `[NoN Affinity]` 开头。

1. **狼近战**
   - **持续有效命中**（目标存活、距离合法）超过 `companionEngageStallTicks`（默认 200 刻）
     → **仍正常战斗**；只有这种情形下出现停滞才算异常。
   - **既无命中也无接近进展** → **正常停滞退出**（预期出现一次 `NO_PROGRESS`）。
   - 切换为**等待** → **停止追击**。
   - 目标死亡 / 模式切换 / 召回导致的 `engagement ended` **属正常**，请把**结束原因行**一并贴回。
2. **骷髅远程** —— 持续远程命中时**不因命中被判停滞**（赋主接线与规则测试已完成，**实际命中未验证**）。
3. **等待模式低氧** —— 无目标 / 已有自卫目标两种情形：能上浮、**不追敌、不跟随**；
   检查是否反复回到水下锚点形成「下潜—低氧—上浮」循环。
4. **超过 10 格安全召回** —— 9.9 与 10 格**不召回**、10.1 格**安全召回**、召回后**不马上**冲回旧目标；
   等待 / 漫游 15 格**不召回**（**漫游仍应保护主人并协助攻击已有敌意的目标**）。
5. **客户端加载** —— 本版本能进入世界、本模组零 ERROR。

---

## 待实机验证的声明（不得当作已完成）

1. **「正式结束交战并清理后，旧箭不得续期」** —— 规则与接线测试已通过（含先失败证据），**实机未验证**。
2. **「换主后双方都无法计入」** —— 同上。
3. **远程实际命中续期**（骷髅射箭 → 伤害回调 → 续期）—— **未验证**。
   远程描述分三层：**赋主接线（已实现）/ 规则接线测试（已通过）/ 实际命中验证（未验证）**。
   `setOwner` 以外的发射路径（三叉戟投掷形态、药水等）：**不误续期，但也不续期**。
4. **近战真实异常场景** —— 清理逻辑已实现、异常路径有测试、包装范围已由加载与结构性测试确认，
   **但未在实机构造真实异常**。

---

## 剩余限制

1. **实际行为验收：全部未完成**（上列 1–5）。
2. **战斗能力是「代码白名单」，不是「行为已验证」**：`hasCombatCapability` 对敌对生物 / 狼 / 猫
   返回 true 是**代码事实**；实机追击并命中**未验证**。不得写成「行为已验证」，
   不得把不支持战斗归因于玩家操作。
3. **低氧让路后由谁接管移动**只能实机确认（字节码已确认 `GoalSelector` 占用/替换规则）。
4. 原地自卫命中判定、NeedsOfNature 动画占用、原版 owner-assist/报复目标重置：未实机确认。
5. **卸载的投射物会保留状态直到其被销毁或服务器停止** —— 这是「保留归因」的代价，
   换来的是「重载后不被当成新箭」。属于有意的取舍，不是泄漏到无界。

---

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.19+1.21.11.jar` |
| 体积 / SHA256 | 258887 字节 / `B25167F8EF65D83497EC4DCC57A12C9B437FE8A72E9BABDA13E359C6B201F332` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 正式版前置

**四模式、召回、持续近战与远程行为验收完成前，保持候选版本，不发布正式 1.0.0。** 当前：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
