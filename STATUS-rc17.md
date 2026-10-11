# 1.0.0-rc.17 状态摘要

> 更新：2026-09-21。**版本 1.0.0-rc.17，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc16.md`、`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` … `STATUS-rc6.md`(**不可交付**)。
> **rc.16 不作为近战行为验收基线**：其攻击包装范围有误，本批已修复。

## 验证分层（本批最终记录）

| 类别 | 状态 |
|---|---|
| **自动测试** | **192 用例 / 0 失败 / 0 错误 / 28 个测试类** |
| **服务器加载** | **通过**（本批已复跑，见下） |
| **客户端加载** | **本版本未复验。rc.15 曾通过**（进入 devworld、玩家登录、本模组零 ERROR）。**不能沿用旧版本结论标记本版本通过。** |
| **实际行为验收** | **未完成** —— 见「最小实机验收清单」 |

**四者互不替代。**

---

## 第 1 项：修复攻击包装范围（**本批最重要的修复**）

### 缺陷（rc.16 的实际错误）

rc.16 的 `@Redirect` 只替换了 `getVelocity()` 这一个调用：

```java
@Redirect(method = "tryAttack",
        at = @At(value = "INVOKE",
                target = "Lnet/minecraft/entity/Entity;getVelocity()Lnet/minecraft/util/math/Vec3d;"))
```

而这个调用在方法体**最开头**。因此包装的真实范围是：

```
打戳 → 读速度 → 【finally 在这里就执行，戳被清掉】 → 计算伤害 → damage() → postHit → ireturn
```

**戳在造成任何伤害之前就被释放了**，近战命中**永远无法被记入进展** ——
即「近战续期」这条链路实际上是断的。而 rc.16 的测试只验证了「finally 存在」，所以全绿。

### 修复

改用 **MixinExtras `@WrapMethod`**（项目已依赖 `mixinextras-fabric:0.5.5`，注解已确认存在）：

```java
@WrapMethod(method = "tryAttack")
private boolean noncreatureaffinity$withAttackStamp(ServerWorld world, Entity target,
                                                    Operation<Boolean> original) {
    MobEntity self = (MobEntity) (Object) this;
    if (!(target instanceof LivingEntity victim)) {
        return original.call(world, target);           // 原方法仍然恰好执行一次
    }
    return AttackStampHandler.runWithStamp(self, victim, () -> original.call(world, target));
}
```

- **真正包住整个方法**：方法体在包装器的 `try` 内运行一次，`finally` 覆盖**全部退出路径**
  （正常返回、返回 false、抛 `Throwable`）。
- **原方法恰好执行一次**：通过 `original.call(...)` 调用，
  **不在重定向内部递归调用同一个方法**。
- **异常保持原样**：MixinExtras 原样向上抛，`AttackStampHandler` 的 `finally` 只做清理，
  **没有 `catch`，不包装、不吞掉**。
- **运行时可验证**：服务器日志出现

  ```
  Renaming synthetic method lambda$noncreatureaffinity$withAttackStamp$0(
      Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;...) to md2c9263$...
  ```

  这条**只会在 `@WrapMethod` 真正应用时产生**，是包装生效的直接证据。

---

## 第 2 项：能识别此错误的接线验证

不再只验证「handler 里有 finally」。新增的验证直指上面那个缺陷的性质：

| 测试 | 断言 |
|---|---|
| `stampSurvivesUntilTheAttackReturns` | **攻击体运行期间戳仍然存在**，且此时按**生产归因规则**判定为可计入 |
| `hitAfterReturnIsRefused` | 攻击返回后戳消失，**此后的命中不被计入** |
| `attributionRuleChecksTickAndGeneration` | 归因规则同时校验**同刻**、**目标**、**代次** |
| `mixinWrapsTheWholeMethod` | mixin **必须**用 `@WrapMethod`；**禁止** `@Redirect` / `@Inject(method`；必须用 `original.call(`；**禁止** `self.tryAttack` 递归 |
| 异常路径（4 项） | 抛 `RuntimeException` / 抛 `Error` / 正常返回 / 返回 false —— 四种都清理，异常原样抛出 |

关键点：`stampSurvivesUntilTheAttackReturns` 用的探针**在攻击体内部**检查戳，
正是 rc.16 会失败的地方（那时戳已被清掉）。

**「狼持续有效命中超过停滞阈值仍能战斗、无进展则正常退出」属于实机验收**，
见下节，本环境无法代跑。

---

## 第 3 项：审查投射物标记生命周期

三处修正：

1. **`setOwner` 不再被无条件当作重新发射**。规则改为：
   - 尚无戳 → 打戳；
   - **同一伙伴的再次赋主 → 保留原戳不变**（连同原始发射刻）。传入的代次描述的是**现在**，
     不是这支投射物发射时的那场战斗；采纳它正是这条机制要防的错误；
   - **换了主人 → 明确丢弃**，不重新绑定（换主不是发射）。
2. **`PersistentProjectileEntity#setOwner` 确实调用父类**（字节码确认 `invokespecial
   ProjectileEntity.setOwner`），因此**两层注入对箭都会触发**。这一点**不再靠猜测**：
   `subclassDelegatesToTheSuperclass` 断言父类方法引用存在于子类常量池中；
   两层的重复触发由**幂等规则**吸收，不会重复建立或覆盖标记。
3. **新增回归测试**：`reOwningInANewFightDoesNotMakeTheArrowCurrent` ——
   旧箭在新交战中被重复赋主，命中**仍不得续期**；`stampIsTakenOnce` 断言戳只取一次、永不改写；
   `changeOfOwnerDropsTheStamp` 断言换主后双方都无法被计入。

**代价（明确）**：真正「重新发射同一支投射物」的场景会保留旧戳，那次命中不续期。
这是**偏安全的方向**；真实重新发射会生成**新的投射物实体**，其自身 `setOwner` 取新戳。

**覆盖范围（严格区分三种，不得混为一谈）**：

| 层面 | 状态 |
|---|---|
| **赋主接线**（`setOwner` 上打戳、父类+子类各一个 mixin） | **已实现**；服务器加载时确认两个 mixin 均应用 |
| **规则测试**（同场续期、旧代次不续期、误中不续期、多枚不覆盖、两伙伴不干扰、重复赋主不推进代次） | **已通过** |
| **实际命中验证**（骷髅射箭 → 伤害回调 → 续期） | **未验证** —— 实机存在路径已确认，端到端**仍需实机** |

因此**不写成「已全面支持远程」**。未覆盖的发射路径（三叉戟投掷形态、药水等不通过 `setOwner`
赋主的路径、模组自定义发射器）**不会误续期，但也不续期**。

---

## 第 4 项：报告修正

- 「攻击异常清理已完成」改为 **「包装范围已修复，异常清理逻辑已实现并有测试；真实异常场景仍需实机」**。
  rc.16 里把它写成「已完成」是不成立的 —— 当时包装范围就是错的。
- **rc.16 客户端加载**：原文沿用了 rc.15 的结论。已改为
  **「本版本未复验，rc.15 曾通过」**。
- **远程描述**已按上表拆成**赋主接线 / 规则测试 / 实际命中验证**三层，不再写成全面支持。

---

## 服务器加载验收记录（本批复跑）

```
Mixing CompanionAttackStampMixin             into net.minecraft.entity.mob.MobEntity
  Renaming synthetic method lambda$noncreatureaffinity$withAttackStamp$0(
      ...Operation;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)...
Mixing PersistentProjectileLaunchStampMixin   into net.minecraft.entity.projectile.PersistentProjectileEntity
Mixing ProjectileLaunchStampMixin             into net.minecraft.entity.projectile.ProjectileEntity
Mixing EntityAffinityMixin / MobGoalAccess    into Entity / MobEntity

注入失败: none        本模组 ERROR: none        服务器: Done (0.406s)!
```

---

## 最小实机验收清单（**需人工执行**）

按交接顺序，**先近战后远程**。启动：

```
gradle runClient --args="--quickPlaySingleplayer devworld"
```

或把 rc.17 的 jar 放进**测试用**实例的 `mods/`（需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。
**不要**使用正式存档。`config/noncreatureaffinity.json` 设 `"debugLogging": true`。
本模组的行以 `[NoN Affinity]` 开头。

### A. 狼持续近战（本批修复的重点）

| 步骤 | 通过判据 |
|---|---|
| 狼在**主动**模式持续近战僵尸，**超过** `companionEngageStallTicks`（默认 200 刻 = 10 秒） | 战斗**不退出**；**不出现** `NO_PROGRESS`、`stalled`、`engagement with ... ended` |
| 打一个**打不到 / 无伤**的目标 | 约 10 秒后**确实放弃**（出现一次 stalled 行） |
| 放弃后继续站在旁边 | **本轮与下一轮都不重开**同一目标 |

**若持续命中仍被判停滞 → 包装范围仍有问题，请把日志发回。**

### B. 骷髅持续射击

| 步骤 | 通过判据 |
|---|---|
| 骷髅伙伴（敌对生物在白名单内）持续远程命中敌人 | 战斗**不因**远程命中被判停滞（说明箭的戳被采纳） |
| 记录 | 是否出现 `NO_PROGRESS` / `stalled`，以及伙伴与目标的大致距离 |

### C. 等待模式低氧

| 步骤 | 通过判据 |
|---|---|
| **无目标**：等待模式下带狼下潜 | 能上浮呼吸，且**不跟随主人** |
| **已有自卫目标**：有敌人的等待狼按进水里 | 能上浮，且**不追击那个敌人** |
| 恢复后 | 回到下令点并保持等待；**检查是否反复回到水下锚点形成「下潜—低氧—上浮」循环** |

### D. 超过 10 格安全召回

| 步骤 | 通过判据 |
|---|---|
| 跟随 / 主动模式走到 **9.9 格**、**恰好 10 格** | **不召回** |
| 走到 **10.1 格** | **安全召回**到主人附近 2–3 格 |
| 召回后立即观察 | **不马上**冲回原来的敌人 |
| 等待 / 漫游走到 15 格 | **不召回**（漫游仍应保护主人并协助攻击已有敌意的目标） |

### E. 顺带确认（加载层面）

客户端启动时确认**本版本**能进入世界、本模组零 ERROR（补上「本版本未复验」这一项）。

---

## 剩余限制

1. **实际行为验收：全部未完成**（A–E）。
2. **近战真实异常场景未实机构造**。清理逻辑已实现并由测试覆盖四种退出路径；
   包装范围已由服务器加载与结构性测试确认。**「实机触发一次真实异常」仍属未做。**
3. **远程实际命中未端到端验证**（赋主接线与规则测试已完成）。
4. **未覆盖 `setOwner` 以外的发射路径**：三叉戟投掷形态、药水等 ——
   不误续期，但也不续期；若要支持需按同样方式接入其真实发射路径。
5. **低氧让路后由谁接管移动**只能实机确认（字节码已确认 `GoalSelector` 的占用/替换规则）。
6. **战斗能力是「代码白名单」，不是「行为已验证」**：`hasCombatCapability` 对敌对生物 / 狼 / 猫
   返回 true 是**代码事实**；实机追击并命中**未验证**。不得写成「行为已验证」，
   不得把不支持战斗归因于玩家操作。
7. 原地自卫命中判定、NeedsOfNature 动画占用、原版 owner-assist/报复目标是否在两次 tick 间重置：
   未实机确认。

---

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.17+1.21.11.jar` |
| 体积 / SHA256 | 255973 字节 / `5B82923EFB50C9E273EF0FCEDAEF1794419F7C75F5CB8247FDDCE6BC906FFA34` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 正式版前置

**四模式、召回、持续近战与远程行为验收完成前，保持候选版本，不发布正式 1.0.0。**
当前状态：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
