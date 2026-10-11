# 交接说明 · 1.0.0-rc.33

> 本批：**修复正常驯马入口漏同步** + **兼容性/故障处理核查**。
> 保留 rc.31 的启动修正与旧宠物补齐证据，但把「服务端行为通过」限定为已执行的具体场景。
> **版本 1.0.0-rc.33，候选状态，未发布正式 1.0.0。**
> **rc.26–rc.32 均为坏包或中间包，不要使用**（rc.32 与 rc.33 只差依赖声明，未交付）。

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.33+1.21.11.jar` |
| 体积 / SHA256 | 276874 字节 / `564AE8BA50677B08C7C359C0DBA117C4034A7C0E298783A406C6676C5257E8EC` |
| 元数据 | `version=1.0.0-rc.33+1.21.11`；`depends.fabric-api>=0.141.6`；`recommends.needsofnature/geckolib` |
| mixin 配置 | 10 条，`defaultRequire: 1`（无 `require=0`） |
| 自动测试 | **219 用例 / 0 失败 / 0 错误 / 30 个测试类** |

## 2. 修复：正常驯马入口漏同步（审查发现，已复核确认）

**根因**：`AbstractHorseEntity.bondWithPlayer(PlayerEntity)` 的写入顺序与猫狗相反 ——
先写主人，后设驯服标志。旧钩子挂在 `setOwner` 的 TAIL 上并检查 `isTame()`，首次驯服时该标志仍为
`false`，于是每次都提前返回，`vanilla_tame_horse` 从未产生。rc.31 发布包内是同一逻辑。

**验证（不是推断）**：

| 证据 | 内容 |
|---|---|
| 原版字节码 | `bondWithPlayer`：偏移 2 `setOwner` → 偏移 7 `setTame(true)` → 偏移 39 单一 `ireturn` |
| 新锚点 | `bondWithPlayer(...)Z` 的 TAIL + `cir.getReturnValueZ()`（成功返回处），再做三项检查 |
| 三项检查（保留） | `isTame()`、**实际主人**（`getOwnerReference().getUuid()`）、逻辑服务端；由纯规则 `RelationshipRules.isTameCompleted(tamed, ownerId, serverSide)` 表达 |
| 覆盖核查 | `bondWithPlayer` 只在 `AbstractHorseEntity` 声明一次；`HorseEntity`、`AbstractDonkeyEntity`、`Donkey`、`Mule`、`Llama`、`TraderLlama`、`Camel`、`SkeletonHorse`、`ZombieHorse` **均无覆写**；唯一调用方是 `HorseBondWithPlayerGoal` |
| 运行时（生产命名空间、用户模组组合、rc.33 导出类） | `class_1496.method_6752`：偏移 2 `setOwner`(method_66323) → 偏移 7 `setTame`(method_6766) → **偏移 55 我们的 `afterHorseBonded`** → 偏移 58 `ireturn` |

**回归验证是有效的，不是字符串断言**：
- 新增 `HorseTameEntryTest` 直接读取**原版字节码的调用序列**并逐项固定
  （`setOwner, setTame, TameAnimalCriterion.trigger, getEntityWorld, sendEntityStatus`），
  专门编码「先写主人、后设驯服标记」这一事实。
- `isTameCompleted(false, owner, true) == false` —— 正是旧锚点当时观察到的状态；服务端/主人缺失同样拒绝。
- 断言编译后的 mixin **引用 `bondWithPlayer`、不再出现 `setOwner`**。
- 断言 9 个马类都未覆写 `bondWithPlayer`（覆盖声明变成可执行检查）。
- **反证**：临时把旧锚点放回 mixin 后测试失败
  （`the anchor must not go back to setOwner … expected: <false> but was: <true>`），随后已还原。

**同时修正的注释/覆盖**：`VanillaTameRelationshipMixin` 现在写明两个家族的顺序**相反**、为何不能共用一个锚点；
并记录猫狗侧的实测顺序（`setTamedBy`：偏移 3 `setTamed(true,true)` → 偏移 8 `setOwner`）。
`createChild` 直接写主人引用（不经驯服路径），后代由 `InheritanceService` 处理。

## 3. 兼容性核查（按追加要求逐项）

### 3.1 注入面与冲突面

全部 10 个 mixin **只观察、不取消**：源码扫描确认**没有** `cancellable = true`、`ci.cancel()`、
`setReturnValue`、`require = 0`。因此本模组不会吞掉喂食、骑乘、装备或任何交互/伤害调用。

| mixin | 目标 | 注入点 | 守卫/范围 |
|---|---|---|---|
| `EntityAffinityMixin` | `Entity` | `readData` TAIL | 仅 `MobEntity` 且非客户端；齐备即提前返回 |
| `VanillaTameRelationshipMixin` | `TameableEntity` | `setOwner(LivingEntity)` TAIL | 需 `ServerPlayerEntity` + `isTamed()` |
| `VanillaHorseTameRelationshipMixin` | `AbstractHorseEntity` | `bondWithPlayer` TAIL | 成功返回 + `isTame()` + 实际主人 + 服务端 |
| `CompanionAttackStampMixin` | `MobEntity` | `@WrapMethod tryAttack` | `original.call` **恰好一次**，无 `catch`，异常原样抛出 |
| `ProjectileLaunchStampMixin` | `ProjectileEntity` | `setOwner(Entity)` TAIL | 仅登记归属；非伙伴记终态，防止日后被“复活” |
| `PersistentProjectileLaunchStampMixin` | `PersistentProjectileEntity` | `setOwner(Entity)` TAIL | 同上（子类覆写必须单独注入） |
| `ProjectileStorageStampMixin` | `ProjectileEntity` | `readCustomData` TAIL | 存档读取时失效旧归属 |
| `PersistentProjectileStorageStampMixin` | `PersistentProjectileEntity` | `readCustomData` TAIL | 同上 |
| `ProjectileRemovalStampMixin` | `Entity` | `setRemoved` HEAD | 仅 `instanceof ProjectileEntity`；释放幂等 |
| `MobGoalAccess` | `MobEntity` | `@Accessor` | 纯访问器，无行为改写 |

伙伴 AI/目标选择/移动只作用于**已绑定的伙伴**，且这部分是**有条件的行为介入**，如实记录如下：

- 入口先在 `CompanionFollowService.tick` 里判断归属：**未绑定、已死亡、归属记录矛盾**的生物直接
  `disengage` 并跳过，`CompanionMovementGoal.install` 不会执行 —— 因此普通生物、未绑定生物与其他模组实体
  **不会**被加入本模组目标，其原版目标也不会被包装。
- 对已绑定伙伴：`install` 新增一个优先级 0、占用 `MOVE`+`JUMP` 的目标；并把原版
  **`FollowOwnerGoal`**（目标选择器）与 **`TrackTargetGoal`**（目标选择器）替换为
  `ConditionalGoal(original, () -> !isManagedCompanion(mob))` —— 即**仅在「本模组正在管理它」时让位**，
  其余情况原版逻辑照常。这是有意为之的窄范围改写，不是「完全不动机器人 AI」。
- 已知控制位冲突：新目标占用 `MOVE`+`JUMP`，与 `SwimGoal`（JUMP）、`BreatheAirGoal`（MOVE）相撞；
  由 `AirSupply` 低氧让位处理——让位时**只放开移动**，包装门仍然有效，`WAIT` 依然不追、`WANDER` 依然不跟。
- 与外部占用协调：`isExternallyOccupied` 经 `NonBridge.isActorBusy` **询问 NON**，被占用时所有模式让位；
  驾驶记录有上限（`MAX_RUNTIME_RECORDS = 512`），超限清空，避免长期驻留。
- 与**原版坐下**并存：不覆写坐姿标志，坐下宠物接入伙伴时映射为 `WAIT`（见 `RelationshipRules.initialMode`）。

### 3.2 依赖与可选集成

- 必需：Fabric Loader、Minecraft、Java 21、**Fabric API ≥ 0.141.6**（本轮补上版本下限，
  避免版本不匹配时给出含糊崩溃）。
- 可选：`needsofnature`、`geckolib`（`recommends`）、`trinkets`/`modmenu`（`suggests`）。
  NON 全程经反射适配层调用（`NonBridge`/`NonAnimationApi`），**没有对 NON 类的直接引用**。
- **实测**：环境 A（只有本模组 + Fabric API）正常启动，日志给出明确提示
  「NeedsOfNature not found; running in degraded mode.」并「initialized (NeedsOfNature present: false)」，
  无类加载崩溃；Loader 提示缺少推荐项 `needsofnature`、`geckolib`，措辞清楚。
- 未添加任何「冲突模组黑名单」，也没有为未知模组写特例。

### 3.3 运行时失败处理（交流动画）

- 失败按原因分类：`NO_ANIMATION` / `CONDITIONS` / `BUSY` / `ERROR`；**无可用动画时按 F 给出明确提示**，
  不启动、不加好感、不记录成功、不占用；日志保留原因与上下文（定义 id、候选数、异常文本）。
- **不吞异常**：`startMating` 只捕获启动期的 `RuntimeException`，记录为 `ERROR` 并写明 `failure.toString()`，不伪造成功。
- 临时状态：`ACTIVE` 记录**只在动画真正启动后**写入，失败路径没有本模组状态需要回滚；
  正常结束/中断由动画停止事件移除，服务器停止时清空。
- **诚实说明**：本模组**没有调用 NON 的取消/停止接口**（该接口未在适配层暴露），
  因此若 NON 在自身内部已部分启动后抛出，我们只能记录原因、不写入 `ACTIVE`，不能声称已替 NON 清理。
  自有请求状态（`PENDING_REQUESTS`/`AWAITING_ANSWER`）有独立清理路径（`abortRequest`）。

## 4. 兼容性验证记录（两组环境，均为服务端）

均为**最终发布 JAR**，隔离目录、独立世界，未使用玩家实例或其存档；Minecraft 1.21.11、Fabric Loader 0.19.5。

| 环境 | 组成 | 实测内容与结果 |
|---|---|---|
| **A 最小依赖** | 本模组 + Fabric API 0.141.6 | 启动 `Done (0.892s)`；无注入失败；NON 缺席时降级并明确提示；坐下宠物回填 → `WAIT`、马 → `FOLLOW`；`/nonaffinity stampcounts` = `melee=0 projectile=0 projectileUuidIndex=0` |
| **B 用户现有组合** | 本模组 + Fabric API 0.141.6 + NeedsOfNature 1.5.11 + GeckoLib 5.4.5 + Trinkets 3.11.3(+cardinal-components 7.3.0) + placeholder-api 2.8.2 + wildfire_gender(Female-Gender-Mod) 5.0.0-Beta.3 | 启动 `Done (0.791s)`，49 个模组，零注入失败；`readData` 内顺序实测为 Fabric 附件(586) → 原版 `readCustomData`(589) → cardinal-components(595) → NON 性别/能量/怀孕/液体(648–666) → **本模组回填(672)** → return(675)；回填与模式落盘同 A；**他人 UUID 的宠物绑定到其本人**（`00000005…`），不夺取 |

**已确认冲突：无。** 明确限定：这只覆盖上述服务端组合中的启动、注入共存、回填与模式落盘，
**不等于「兼容所有模组」**，也不等于客户端侧已验证。

**用户组合中的客户端专属模组**（`CustomSkinLoader`、`entity_model_features`、`entity_texture_features`、
`modmenu`）在专用服务器上被 Loader 按 `environment: client` 跳过，**因此其与本模组的客户端共存未在本轮验证**。

## 5. 未验证范围与需人工完成的步骤（**不得写成通过**）

1. **客户端**：把 rc.33 放进实例后能启动、进世界、退出重进；日志无注入报错；
   环境 A 与环境 B 的**客户端**组合均未验证（本轮只做服务端）。
2. **正常驯马实机**（本次修复的直接对象）：**从从未驯服的马开始**正常驯服，
   **不退出、不重载、不使用 `friend` 命令**，立即检查 `/nonaffinity companion info`
   的挚友、伙伴主人与模式，然后测骑乘与装备。
3. 骨头驯狼等猫狗侧入口、无动画动物按 F 的提示与「原版驯服仍可用」。
4. 四模式行为：等待原地自卫、漫游保护主人并协助攻击已有敌意目标、跟随/主动 >10 格召回、等待/漫游不召回。
5. rc.25 遗留：近战持续续期（超停滞阈值）、远程（骷髅）续期、双伙伴互不干扰、低氧等待上浮、
   `/nonaffinity stampcounts` 计数回落。

`run/config/noncreatureaffinity.json` 的 `debugLogging` 仍为 `true`（项目内开发目录，未触碰玩家存档）。
