# 1.0.0-rc.26 状态摘要

> 更新：2026-09-22。**版本 1.0.0-rc.26，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc25.md` 之前各版见 `STATUS-rc23.md`、`STATUS-rc22.md`、`STATUS-rc21.md` … `STATUS-rc13.md`。
> 本批：**统一驯服与挚友关系、无动画时的安全拒绝**。**保留**已修复的战斗、召回、投射物归因逻辑。

## 验证分层（不得互相替代）

| 类别 | 状态 |
|---|---|
| **自动测试** | **210 用例 / 0 失败 / 0 错误 / 29 个测试类**（rc.25 为 203） |
| **服务器加载** | **通过** —— **10 个 mixin** 全部应用，零注入失败 |
| **客户端加载** | **本版本未复验** |
| **实机行为验收** | **未完成**（见第五节） |

---

## 一、实际调用链（第四节第 1 点：先核对，再动手）

| 入口 | 实际位置（反汇编确认） |
|---|---|
| 原版驯服（狼/猫/鹦鹉等） | `TameableEntity#setTamedBy(PlayerEntity)` → `setTamed(true,true)` + **`setOwner(owner)`** |
| 原版驯服（马/驴/骡/羊驼/骆驼） | `AbstractHorseEntity#setTame(boolean)` **只翻标志、不设主人**；主人由 **`setOwner(LivingEntity)`** 写入 |
| 好感达标 | `AffinityService.checkFriendThreshold(...)` |
| 伙伴绑定 | `CompanionService.bindOnBefriend(...)`（原实现，硬编码 FOLLOW） |
| 旧宠物加载 | `Entity#readData(ReadView)` → 抽象的 `readCustomData` |
| F 键 | 客户端按键 → `MatingRequestPayload` → `AffinityNetworking.handleMatingRequest` → `NonBridge.startVoluntaryMating` → `NonIntegration.startMating` |
| 动画可用性 | `NonAnimationApi.findCandidates(..., MatchOptions.startEligible(null))`（**已按本次实际参与者判定**） |

**由此确定的注入点（都在「成功状态与主人数据可用之后」）**：

- `TameableEntity#setOwner(LivingEntity)` **TAIL**，且要求 `isTamed()`；
- `AbstractHorseEntity#setOwner(LivingEntity)` **TAIL**，且要求 `isTame()`（**不能**用 `setTame`，那时刻还没有主人）；
- `Entity#readData(ReadView)` **TAIL**（加载补齐）。

## 二、统一关系规则（第四节第 2 点）

新增两件东西，**三个入口共用**，避免三套实现逐渐分叉：

- `RelationshipRules`（纯逻辑，可测）：归属判定、好感补齐规则。
- `RelationshipSync`（唯一的「成为挚友+伙伴」入口）：幂等、绝不夺取、不伪造交流次数。

**语义**：

| 规则 | 实现 |
|---|---|
| 好感至少达到挚友阈值 | 新建关系用 `friendThreshold` 作初值；已有关系只**上调**、绝不下调 |
| 保留更高的好感 | `affinityRaisedTo(current, threshold) = max(...)` |
| 不伪造交流次数 | 完全不触碰 `matingCount`；只有真实交流成功才计数 |
| 不发放只属于交流的奖励 | 不调用 `recordMating`，不触发好友之证发放 |
| 使用**实际主人** | 全程用 `setOwner` 写入的 UUID；**没有任何「最近玩家」查找** |
| 不覆盖他人 | 见下表 |
| 绑定不得用 `bindOnBefriend`（硬编码 FOLLOW） | 由 `RelationshipSync` 直接处理，**只在完全没有模式记录时**才写 FOLLOW，因此不重置玩家已选模式 |

归属判定（`RelationshipRules.evaluate`）：

| 情形 | 结果 |
|---|---|
| 无主 → 驯服者 | **BOUND** |
| 记录一致（都是同一人） | **BOUND**（重复驯服是幂等，不是拒绝） |
| 伙伴主人是**他人** | `OTHER_PLAYER_COMPANION` —— 不夺取 |
| 原版主人是**他人** | `OTHER_PLAYER_VANILLA_OWNER` —— 不夺取 |
| 两条记录**互相矛盾** | `OWNERSHIP_CONFLICT` —— **不选边**，`WARN` 级日志给出双方短 id 与处理办法 |
| 无法保存关系数据 | `UNSUPPORTED_CREATURE` |

**保留原版数据**：只读 `getOwnerReference()`，**不删除**原版主人、骑乘、装备、物品栏；不给不支持驯服的动物写原版驯服数据。

## 三、旧宠物补齐（第四节第 4 点）

- 时机：`Entity#readData` TAIL —— 此时存档里的驯服标志与主人才是真实值。
- **主人不在线也生效**：全程按**持久化 UUID** 判断，不需要玩家实体。
- **无法确认主人 → 不认领**（`vanillaOwnerUuid == null` 直接返回）。
- **可重复执行**：先做一次廉价检查，已经「关系齐备」时**在写任何东西之前返回**，因此不会每次加载都写、也不会刷屏。
- **不重复奖励、不降好感、不重置模式**。
- 无伙伴模式的旧宠物：**坐下 → WAIT**，其他 → FOLLOW；已有模式保留。

## 四、F 键与动画兼容（第三节）

### 已核实

`startMating` **本来就按本次交互的实际参与者**做可用性检查（`MatchOptions.startEligible`），
**不是**「该物种是否有任意动画」。失败时：不启动动画、不加好感、不记录成功、不占用
（`abortRequest` 在启动成功前不会留下新请求）。

### 本批修正

1. **拒绝原因**：新增 `MatingRefusal`（`NO_ANIMATION` / `CONDITIONS` / `BUSY` / `ERROR`），
   分别来自「该配对无任何定义」「定义存在但形状不是一玩家一生物」「参与者忙」「引擎抛异常」。
   `ERROR` 时**记录日志**，不吞掉。
2. **玩家提示**（此前只有一句笼统的「条件不满足」）：
   - 无可用动画 → **「当前没有条件进行深入交流：该动物暂无可用的深入交流动画。」**
   - 忙 → **「当前没有条件进行深入交流：%s 或你正忙。」**
   - 其他 → **「当前没有条件与 %s 进行深入交流。」**
3. **共用入口一并覆盖**：`AffinityCommand.playAnimation`（命令路径）与 HUD/请求路径**都**经
   `NonBridge.startChosen` → 同一校验；`startChosen` 同样按本次交互匹配定义并返回布尔。

## 五、实机验收（**需人工，未完成**）

自动测试与实机分开：**自动测试 210 / 0 失败**；**实机未做**。

准备：`gradle runClient -PdevWorld=testworld`；`debugLogging: true` 已开。
**记录动物、实际主人、挚友状态、伙伴主人与模式**（`/nonaffinity companion info`）。
**命令创建挚友只用于准备下游测试，不能替代本表入口的验收。**

| 场景 | 预期 | 实际 |
|---|---|---|
| 骨头成功驯服狼 | 自动成为**实际主人**的挚友并绑定伙伴 | 待记 |
| 正常驯服马 | 自动成为挚友；骑乘、装备正常 | 待记 |
| **已确认无可用交流动画**的动物按 F | 提示「该动物暂无可用的深入交流动画」；不崩溃、不占用、不加好感 | 待记 |
| 无动画但可原版驯服的动物完成驯服 | 正常成为挚友，不受动画缺失影响 | 待记 |
| 有可用动画的动物交流达标 | 正常成为挚友；适用时同步原版驯服 | 待记 |
| 动画检查通过后启动失败或目标失效 | 临时状态清理完成，后续操作不被卡住 | 待记 |
| 旧宠物加载、退出后重进 | 正确补齐，不重复奖励、不重置模式 | 待记 |
| 其他玩家操作已归属宠物 | 不夺取原版主人或伙伴归属 | 待记 |
| 原版坐下宠物接入伙伴系统 | 模式与实际行为一致，切回 FOLLOW 后能正常行动 | 待记 |

**入口修复后继续 rc.25 未完成项**：近战续期、远程续期、10 格召回。
**不能把此前缺少挚友前提的观察算作通过。**

## 六、配置语义（第四节第 6 点）

| 配置 | 语义 | 状态 |
|---|---|---|
| `allowVanillaTamedCreatures` | 原版驯服即纳入；旧宠物加载补齐 | **此前是纯预留项**（有界面/说明、无实现）—— 本批**接入**，默认 `true` 即满足要求；说明文案已改正 |
| `autoTameOnFriend` | 好感达标时是否同步建立原版驯服 | 接入（原有），默认 `true` |

## 七、剩余限制与待验证

1. **本批全部实机验收未做**（第五节 9 项 + rc.25 的 3 项）。
2. **`allowVanillaTamedCreatures = false` 的行为未实机验证**：现在它会关闭「原版驯服即纳入」与加载补齐；实测未做。
3. **每加载的廉价检查**：已确认为 O(1) 读取且齐备即返回，但**未实机验证加载期开销**。
4. **马类骑乘状态与伙伴模式**的衔接只做了逻辑处理（不重置模式），**未实机验证**。
5. 战斗、召回、投射物归因的既有未完成项（远程实际命中、跨维度实机等）不变。

## 八、产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.26+1.21.11.jar` |
| 体积 / SHA256 | 275917 字节 / `0004E06309F150F7C111F906433F582F7A32BA739514DA1CFB383D3C7423B3AF` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

### 服务器加载记录

```
Mixing EntityAffinityMixin                   into net.minecraft.entity.Entity
Mixing VanillaTameRelationshipMixin          into net.minecraft.entity.passive.TameableEntity
Mixing VanillaHorseTameRelationshipMixin     into net.minecraft.entity.passive.AbstractHorseEntity
... 共 10 个
注入失败: none        本模组 ERROR: none        服务器: Done (0.339s)!
```

> 加载复跑**再一次抓到真实缺陷**：首次把补齐钩子挂在 `Entity#readCustomData` 上，
> 但它是 **`abstract`、没有 RETURN**，`@At("TAIL")` 无法注入 → 整个 `EntityAffinityMixin`
> 应用失败。改用具体方法 `Entity#readData(ReadView)` 后通过。

## 正式版前置

**关键行为验收通过、已发现问题修复完成前，保持候选版本，不设置正式 1.0.0。** 当前：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内；**未安装到游戏 mods 目录**。
- **未删除、重命名或覆盖任何存档**（`testworld` 是我上一批经用户同意后改的**目录名**，`level.dat` 未动）。
