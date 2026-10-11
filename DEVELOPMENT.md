# NoN Creature Affinity — 开发与构建说明

以 **NeedsOfNature（Fabric / MC 1.21.11）** 为基础的附属 mod：为生物加入「与玩家的关系」系统。

## 已实现的功能（对应需求 1–6）

| # | 需求 | 实现位置 | 状态 |
|---|---|---|---|
| 1 | 按键主动与生物触发 NON 交配 | `net/AffinityNetworking` + `client/NonCreatureAffinityClient`（默认 **F** 键，可在按键绑定中自由修改） | ✅ 已实现 |
| 2 | 交配后（无论主动被动）增加好感 | `service/AffinityService#recordMating`，由 `non/NonIntegration` 监听 NON 的 `STOPPED` 事件触发 | ✅ 已实现 |
| 3 | 好感达阈值 → 自动驯服 / 转为会保护玩家的友方 | `AffinityService#checkFriendThreshold` + `service/FriendlyDefenderSystem` | ✅ 已实现 |
| 4 | 友好生物性欲达标→主动求欢，可同意/拒绝 | `service/RequestSystem` + `NonEvents` 对应的 NON 动画事件 | ✅ 已实现 |
| 5 | 拒绝 → 降好感 + 触发一次强制交配（保留脱身） | `RequestSystem#reject` → `NonBridge#startForcedMating` | ✅ 已实现 |
| 6 | 后代自动满好感并归属 | `service/InheritanceService` + `AffinityService#bindOffspring` | ✅ 已实现 |

全部阈值与增减量都在 `config/noncreatureaffinity.json` 中可配置（见下）。

## 项目结构

```
I:\DSH\work\noncreatureaffinity\
├─ build.gradle              构建脚本（Fabric Loom 1.15.4）
├─ gradle.properties         版本与依赖坐标
├─ settings.gradle
├─ libs\
│  └─ needsofnature-1.5.11.21.11.jar   本体（仅编译期 + 本地运行测试）
└─ src\main\
   ├─ java\com\nonid\affinity\
   │  ├─ NonCreatureAffinity.java        服务端入口：注册 / tick / 实体加载
   │  ├─ client\NonCreatureAffinityClient.java   客户端入口：按键绑定
   │  ├─ command\AffinityCommand.java   /nonaffinity 命令
   │  ├─ config\AffinityConfig.java     全部可配置项
   │  ├─ data\
   │  │  ├─ AffinityAttachments.java    实体附着（Fabric Attachment API）
   │  │  ├─ AffinityHolder.java         Mixin 注入的接口
   │  │  ├─ CreatureAffinityData.java   单生物的关系集合 + NBT
   │  │  ├─ PlayerAffinity.java         单条关系记录
   │  │  └─ NbtCompoundCodec.java       NBT ↔ Codec 适配
   │  ├─ mixin\EntityAffinityMixin.java 让所有实体实现 AffinityHolder
   │  ├─ net\                           2 个 payload + 网络处理
   │  ├─ non\                           NON 适配层（隔离类加载）
   │  │  ├─ NonBridge.java              对外唯一入口，NON 缺失时安全降级
   │  │  └─ NonIntegration.java         实际调用 NON API
   │  └─ service\
   │     ├─ AffinityService.java        好感读写与阈值结算
   │     ├─ RequestSystem.java          求欢与同意/拒绝
   │     ├─ FriendlyDefenderSystem.java 友方单位保护玩家
   │     └─ InheritanceService.java     后代继承
   └─ resources\
      ├─ fabric.mod.json
      ├─ noncreatureaffinity.mixins.json
      └─ assets\noncreatureaffinity\lang\{zh_cn,en_us}.json
```

## 数据模型

**每对（玩家 × 生物）一条独立关系**（按你的选择）：

- 存储：Fabric **Attachment**（`noncreatureaffinity:affinity`）挂在实体上，自动写入实体 NBT，`copyOnDeath` 死亡保留。
- 玩家侧存"生物 → 好感"的反向索引，便于查询。
- 单生物最多保留 64 条关系，超限时优先裁剪"未成为密友且好感最低"的条目，防止存档膨胀。
- 记录字段：好感值、是否密友、是否由本模组驯服、上次交配时间、交配次数、高潮次数、被拒次数、世代、求欢冷却。

## 配置项（`config/noncreatureaffinity.json`）

| 组 | 关键项（默认） |
|---|---|
| 好感基础 | `maxAffinity=100`、`minAffinity=0`、`initialAffinity=0` |
| 增减 | `affinityPerMating=10`、`affinityPerPeak=5`、`affinityBonusPlayerInitiated=3`、`affinityLossOnReject=15`、`affinityDecayPerDay=0` |
| 密友/驯服 | `friendThreshold=100`、`autoTameOnFriend=true`、`tameOwnerIsTriggerPlayer=true` |
| 友方保护 | `friendlyMobsDefendPlayer=true`、`friendlyDefendRadius=12`、`friendlyAttackDamageMultiplier=1.0` |
| 求欢 | `requestEnergyThreshold=150`、`requestAffinityThreshold=60`、`requestCheckIntervalTicks=100`、`requestChance=0.25`、`requestRange=8`、`requestTimeoutTicks=200`、`requestCooldownTicks=1200` |
| 拒绝惩罚 | `forceMatingOnReject=true`、`forcedMatingEscapable=true`、`forcedMatingGraceTicks=60` |
| 后代 | `offspringAffinityPercentOfParent=100`、`offspringBefriended=true`、`offspringInheritGeneration=true`、`offspringParentSearchRadius=16` |
| 交互/界面 | `interactRange=6`、`hudEnabled=true`、`notifyOnAffinityChange=true` |
| 调试 | `debugLogging=false`、`debugShowAffinityInName=false` |

## 命令

```
/nonaffinity accept [生物]     同意求欢
/nonaffinity reject [生物]     拒绝求欢（降好感 + 触发强制交配）
/nonaffinity info              查看你注视生物的关系
/nonaffinity set <值>          设置好感（需 OP）
/nonaffinity friend            直接变密友（需 OP）
/nonaffinity reload            重载配置（需 OP）
```

## 构建

### 环境（都已就绪，无需重复下载）

| 组件 | 位置 |
|---|---|
| JDK 21 | `I:\DSH\work\tools\jdk21\jdk-21.0.2` |
| Gradle 9.2.0 | `I:\DSH\work\tools\gradle-9.2.0` |
| 反编译器（可选） | `I:\DSH\work\tools\vineflower.jar` |

> 注意：Loom **1.15.4** 是能同时满足「Java 21」与「依赖 mod 由更新 Loom 构建」的最低可行版本；
> Gradle 必须 ≥ 9.2（Loom 的插件元数据要求）。

### 构建命令

```powershell
$env:JAVA_HOME = "I:\DSH\work\tools\jdk21\jdk-21.0.2"
Set-Location "I:\DSH\work\noncreatureaffinity"
& "I:\DSH\work\tools\gradle-9.2.0\bin\gradle.bat" --no-daemon build
```

产物：`build\libs\noncreatureaffinity-0.1.0+1.21.11.jar`

### ⚠️ 依赖缓存位置（需要注意）

Gradle 默认把依赖缓存放到了 **`C:\Users\Administrator\.gradle`（约 450 MB）**，不在工作区内。
如果希望把缓存也限制在工作区，构建前设置：

```powershell
$env:GRADLE_USER_HOME = "I:\DSH\work\gradle-home"
```

代价是首次构建需要重新下载全部依赖。

## 与 NeedsOfNature 的对接方式（关键设计）

1. **只使用 NON 的公开 API**（`com.nonid.api.*`），不碰它的内部实现。
2. **不覆盖任何原版方法**：唯一的 Mixin `EntityAffinityMixin` 只是新增接口实现，不注入/覆写方法，
   因此不会与 NON 的 72 个 Mixin 冲突（NON 的 mixin 配置是 `defaultRequire=1`，覆写宿主方法会直接崩游戏）。
3. **类加载隔离**：`NonBridge`（不引用 NON 类型）→ `NonIntegration`（引用 NON API）。
   NON 未安装时本模组正常启动，所有功能安全降级，不会抛 `NoClassDefFoundError`。
4. **交配触发链路**：
   `NonAnimationApi.findMatch(...)` → 校验确为「1 玩家 + 1 生物、一主动一被动」→
   `NonAnimationApi.start(...)` → 监听 `NonAnimationEvents.STOPPED` →
   `AffinityService.recordMating(...)` 结算好感。
5. **强制交配保留脱身手段**：强制交配同样使用 `NonAnimationDamageBehavior.STOP_ON_DAMAGE`，
   玩家攻击即可中断（符合你选择的方案）。

## 开发注意事项（踩过的坑）

- **源码必须保持纯 ASCII**：本项目所有 Java 源码的注释与文案都用英文，面向玩家的文本走
  `assets/noncreatureaffinity/lang/*.json` 翻译键。原因：Windows 上 PowerShell 默认编码会把
  含中文的源文件写成乱码，导致 `未结束的字符串文字` 之类的编译错误（本次开发中确实踩到并因此重写了多个文件）。
- 修改源码请使用能保证 UTF-8 的工具（本项目用 Node 脚本 + 编辑器写入）。
- 1.21.11 的 API 与旧版本差异较大，已适配的点：
  `Entity#getEntityWorld`（返回 `World`）、`TameableEntity#isTamed/setTamed`、
  `LazyEntityReference#getEntityByClass`、`NbtCompound#getListOrEmpty` / `NbtList#getCompoundOrEmpty` /
  `NbtList#addElement(int, NbtElement)`、`KeyBinding.Category`、
  `CommandManager.requirePermissionLevel(...)` / `PermissionLevel`。

## 运行时验证结果（真实加载，非推测）

用 Loom 自带的开发服务端实际启动过一次（`gradle runServer`），同时加载
**Minecraft 1.21.11 + Fabric Loader 0.19.5 + Fabric API + GeckoLib 5.4.5 + NeedsOfNature 1.5.1 + 本模组**：

```
- geckolib 5.4.5
- needsofnature 1.5.1+1.21.11
- noncreatureaffinity 0.1.0+1.21.11

[NoN Affinity] NeedsOfNature detected; animation integration enabled.
[NoN Affinity] initialized (NeedsOfNature present: true)
Done (0.293s)! For help, type "help"
```

| 验证项 | 结论 |
|---|---|
| 本模组能否加载 | ✅ 正常加载，入口点执行成功 |
| 与 NON 的集成检测 | ✅ 侦测到 NON 并启用动画联动 |
| Mixin 是否安全 | ✅ 无任何 Mixin 注入失败；世界完整生成（若 Mixin 破坏了 `Entity`，生成阶段就会崩） |
| 类加载隔离是否有效 | ✅ 无 `NoClassDefFoundError` |
| Attachment 注册 | ✅ 启动无异常 |

**单元测试**：19 个测试全部通过（`gradle test`）

- `CreatureAffinityDataTest`（11）：NBT 往返、条目上限裁剪、密友保护、好感裁剪、冷却、校验和
- `AffinityConfigTest`（4）：默认值、边界裁剪、非法值修复
- `AffinityHolderTest`（4）：懒创建、实例稳定、peek 不创建、实体间不串数据

> 测试真的抓到一个 bug：原先用 `MathHelper.clamp(value, min, max)`，但该方法的参数语义与假设不符，
> 导致**负向好感调整被错误处理**。已改为本地显式钳制并加注释，避免依赖易变的原版辅助方法。

**测试环境位置**（全在 DSH 工作区内）：`I:\DSH\work\noncreatureaffinity\run\`

---

## ⚠️ 重要：需要 NON 的「内容包」才能真正交配

运行时日志显示：

```
[NoN Animation] Loaded 0 animation definition(s) from datapacks
```

这是 **NeedsOfNature 本体的设计**：它**不携带任何动画定义**（已确认 1.21.11 的 jar 内没有
`afw_animdefs`），动画内容全部来自外部「内容包」。因此：

- **没有内容包时**：按键不报错，但会提示「当前条件不满足」（找不到匹配的动画定义）；
  好感的其他机制（数据、配置、命令）仍然正常。
- **要让交配真正跑起来**：把 NON 的内容包放到 `<游戏目录>/needsofnature/` 下（ZIP 或文件夹均可）。

开发环境**已经自带一个测试内容包**（见下节），所以开发环境是自闭环的，不需要官方包也能验证链路。

---

## 测试内容包（已就绪，开发环境自闭环）

位置：`I:\DSH\work\noncreatureaffinity\testpack\`（说明见 `testpack\README.md`）

我用它**验证通过**了内容包加载链路，日志实测：

```
[NoN Animation] Loaded 2 animation definition(s) from datapacks
[NoN Affinity] 2 animation definition(s) loaded: noncreatureaffinity:affinity_test/pig_player, noncreatureaffinity:affinity_test/wolf_player
```

这同时证明了之前那份分析里的关键结论：**动画定义必须放在 `data/<ns>/afw_animdefs/`（数据包侧）**，
而 geckolib 模型在 `assets/` 侧；`<游戏目录>/needsofnature/` 下的包会被同时注册为资源包与数据包。
（只放 `assets/` 的话定义永远不会加载。）

包内含两个「**玩家(passive) + 生物(active)**」形状的定义（狼、猪），模型与动画是**占位资源**
（不可见根骨骼 + 无关键帧），只用于打通逻辑链路，不适合日常游玩。

已投放到开发环境：`run\needsofnature\0001_affinity_testpack\`

---

## 诊断工具

启动时会自动做一次自检并写日志：

- 未装 NON → 提示交配功能禁用（其余功能正常）
- **0 条动画定义** → 明确提示「把内容包放进 `<游戏目录>/needsofnature/`」
- 有定义但没有「玩家+生物」形状 → 提示按键匹配不到任何东西

游戏内命令：

```
/nonaffinity diagnose
```

报告 NON 是否加载、定义条数、是否有可用形状的定义、附近可交互目标及其好感/性欲/是否在忙。

匹配失败时（按了键但没反应），日志会给出**具体原因**：找不到定义 / 找到定义但不满足起始条件 /
定义形状不对 / 动画引擎拒绝启动。这样出问题不用猜。

---

## 实测反馈与修复（v0.1.0 → 本轮）

根据实机反馈做了四项修改：

### ① 「当前条件不满足，要按好几次才触发」——已修

**原因**：你的内容包装了 **370 个动画定义**。我原先用 `findMatch` 取**单个「最佳」候选**，
但同一对演员（玩家+生物）往往有几十个定义，其中很多此刻**不满足起始资格**
（需要 2~3 名参与者、需要靠墙/床/水面、需要特定空间……），一旦选中这种定义就会失败。
概率上就表现为「按几次才成功一次」。

**修复**：改用 `findCandidates` 枚举全部候选，然后**逐个尝试启动，直到有一个真正成功**
（`NonIntegration.startMating` → `startCandidate`）。一次按键即可稳定触发。
若全部失败，日志会说明是「没有匹配定义」还是「有定义但都不满足条件」。

### ② 马没有被驯服——已修

**原因**：马的驯服体系**不属于 `TameableEntity`**。马、驴、骡、羊驼、骆驼、骷髅马等用的是
`AbstractHorseEntity`，方法是 `isTame()` / `setTame(boolean)` / `setOwner(...)`。
我原先只判断 `TameableEntity`，所以对马完全不生效。

**修复**：新增 `AffinityService.tameCreature()` / `isTamedCreature()`，同时覆盖两个体系；
友方保护的目标解析也同步支持马类。

### ③ 新增道具：好感度检测器 —— 已实现

- 物品 id：`noncreatureaffinity:affinity_detector`，中文名「好感度检测器」，在**工具**创造栏。
- **手持时**（主手或副手皆可），附近 32 格内的生物**头顶会显示好感度数字**；
  已成密友的会显示为粉色并带一个 `*` 标记。
- 实现方式：走 `HudRenderCallback` 做屏幕空间投影（自己用 JOML 构造投影/视图矩阵）。
  之所以不用世界空间渲染：1.21.11 移除了 `Camera#getViewMatrix` 与 `RenderSystem` 的矩阵访问器，
  走 HUD 反而更稳定，而且 NON 播放动画时会 cancel 原版 HUD 子方法，走 HUD 能保证提示不消失。
- 注意：数值是**文字标签**，不是画在生物模型表面上的（不同生物的骨骼名各不相同，无法通用）。

### ④ 新增图形化配置界面 —— 已实现

- **ModMenu 集成**：`ModMenuIntegration` 提供配置入口，在 ModMenu 里点本模组的「配置」即可打开。
- **独立入口**：新增按键 **K →「打开配置界面」**（同样可在按键绑定里改）。
- 界面按 `AffinityConfig` 的字段**反射自动生成**：整数/小数是滑块，布尔是开关按钮，
  带翻页。以后往配置里加字段，界面会自动多出一项。
- 关闭界面时自动 `sanitize()` 并写回 `config/noncreatureaffinity.json`。
- ModMenu 依赖用的是**你本机已有的那个 jar**（复制到 `libs/modmenu-compile-only.jar` 作编译期依赖），
  没有额外下载；它是 `modCompileOnly`，运行时不强制要求安装。

---

## 关于开发环境与你的游戏文件

- 我把你复制过来的 `1.21.11-Fabric 0.19.5` 里的 **23 个内容包**复制到了开发环境的
  `run\needsofnature\` 用于验证（只读复制，**没有改动你原始的游戏目录**）。
  实测加载结果：`Loaded 370 animation definition(s)`。
- 我在 `1.21.11-Fabric 0.19.5\mods\` 里的 modmenu jar **只做了复制读取**，没有修改。

---

## 根据实机截图反馈的第三轮修改

### ① 配置界面：补上项目名称、悬停说明、单项重置

之前每行只有一个裸数值，看不出调的是什么。现在每行是：

```
[选项名称]            [滑块: 数值]              [R]
```

- **名称**来自语言文件（`config.noncreatureaffinity.option.<字段名>`），共 34 个配置项全部有中文名。
- **悬停说明**：鼠标移到滑块或开关上会弹出提示框，说明这个数值影响什么
  （例如「成为密友所需好感 —— 好感达到该值时，可驯服生物会被自动驯服」）。
- **`R` 按钮**：把该项恢复为默认值，不用手动回调。

滑块区间也逐项标定了（见 `AffinityConfigScreen.RANGES` / `DOUBLE_RANGES`），
例如好感相关 0–500、概率 0–1、距离 1–32 格，未列出的字段用通用区间兜底。

### ② 好感度检测器：改成平板 + 右键开关 + 开启后不再依赖手持

- **外形**：重新画成一块小平板（外壳 + 屏幕 + 屏幕上的心形标记），并调整了手持/第一人称的
  展示角度，看起来像握着设备而不是工具。
- **右键开关**：右键切换好感扫描。**开关属于玩家，不属于某一件物品**，
  所以任何一块检测器都能开/关它（符合你的要求）。
- **开启后持续显示**：扫描开着就一直显示，把平板收起来也不影响；再右键一次关闭。
- **状态会保存**：写在 `config/noncreatureaffinity_client_state.json`，重开游戏仍保持。
- **自检读数**：开启后屏幕左上角会显示 `Affinity: 已标记数/范围内生物数`。
  这是给你排障用的——如果显示 `0/3` 说明附近确实有 3 只生物但都被投影环节滤掉了，
  如果显示 `0/0` 说明范围内本来就没有生物。

> 顺带修掉一个隐患：物品类原本直接引用了 `@Environment(CLIENT)` 的类，
> 这会让**专用服务端加载时崩溃**。现在改用反射访问客户端状态类，双端都安全。

### ③ 这一轮同样做了真实运行验证

开发客户端载入你的存档进入世界：

```
[NoN Affinity] registered item noncreatureaffinity:affinity_detector
[NoN Affinity] initialized (NeedsOfNature present: true)
[NoN Animation] Loaded 370 animation definition(s) from datapacks
Time elapsed: 2277 ms
```

无异常、无崩溃。**但界面外观与头顶标签的位置我仍然看不到画面**，需要你实测确认。

---

## 根据第二轮截图反馈的修改

### ① 检测器不显示：找到根因了 —— HUD 回调已废弃

之前用了 `HudRenderCallback` 来画头顶数值。它在 1.21.11 上**已被废弃**，实际不再被调用，
所以「开启扫描」的提示能出现（那是聊天栏消息），但头顶数字和自检读数都不会画出来。

现已迁移到新 API：

```java
HudElementRegistry.addLast(Identifier.of(MOD_ID, "affinity_labels"), this::render);
```

并在渲染入口加了一行日志用于自证：
`[NoN Affinity] affinity overlay is drawing (HUD element active)`。
实测启动客户端后这行确实出现，说明 HUD 元素已经被调用。

### ② 配置界面：改成两行布局 + 精简选项

上一版把名称和滑块挤在同一行，且纵向空间被压缩，名称实际没显示出来。
现在**每个配置项占两行**：

```
┌────────────────────────────────────────┐
│ 成为密友所需好感                        │  ← 名称单独一行，带底色
├──────────────────────────────────┬─────┤
│         [滑块:      100       ]  │  R  │  ← 控件 + 单项重置
└──────────────────────────────────┴─────┘
```

同时按你的要求**只保留关键项**（隐藏调试/预留项），界面从 34 项精简到 20 项，
顺序按玩法分组：好感核心 → 密友与驯服 → 求欢 → 保护与显示 → 后代。

### ③ 友好生物不再「没问就强上」

这条是最关键的行为修复，原因比较绕：

NeedsOfNature 的自主交互（`NonGatherSystem` 第 426 行）调用
`startAnimationNowWithContext(...)` 时**没有传 `requester`**，也就是说
「生物主动走上来」和「生物强上你」在 NON 内部是**同一条代码路径**，外部 addon 无法按发起者区分。

所以只能从「启动前拦截」下手。已注册 `NonAnimationEvents.BEFORE_START` 闸门：

- 生物发出求欢 → 标记为「等待答复」
- 只要它还处于等待状态，**任何交配动画都会被拒绝启动**（并写日志说明原因）
- 玩家**同意** → 先清除标记，再开始交配
- 玩家**拒绝** → 清除标记，按配置触发一次强制交配
- **超时不答复** → 清除标记，仅进入冷却（不算拒绝，不会被罚）
- 玩家自己按 F 主动发起 → 发起前无条件清除标记，**绝不会被这道闸门挡住**

> 副作用说明：等待答复期间，该生物对你的其它 NON 交互也会一起被挡住。
> 这是「必须等我回答」的必然代价；超时上限由 `requestTimeoutTicks`（默认 200 tick ≈ 10 秒）控制，
> 不会永久卡死。

---

### ④ 「按几次才触发」的残留问题 —— 是上一轮的闸门自己造成的

加了「等待答复」闸门之后，玩家反馈按键偶尔仍要按几次才生效。原因是**闸门的时序漏洞**：

```
玩家按 F
  → handleMatingRequest
      → startVoluntaryMating
          → NonAnimationApi.start(...)
              → BEFORE_START 闸门：该生物还有「等待答复」标记 → 拒绝启动
  → start 返回 null → 提示「当前条件不满足」
```

也就是说，当某只生物正好在等你答复时，**你自己按 F 发起的交配也被闸门挡掉了**。
多按几次后它可能刚好超时清了标记，于是又成功了 —— 完全符合「偶发、按几次才行」的现象。

**修复**：把清理动作提到启动**之前**，并且语义明确化：

```java
// 玩家显式操作优先于生物尚未答复的请求
NonBridge.abortRequest(target.getUuid());
boolean started = NonBridge.startVoluntaryMating(world, player, target);
```

同时移除了上一版放在 `startMating` 里那次「事后清理」——它执行得比闸门检查还晚，
根本没有作用，只会让行为更难推理。

另外给按键失败补了一条日志，之后如果还有偶发失败，日志里能直接看到具体原因
（是没有匹配定义、不满足起始条件、形状不符，还是引擎拒绝启动）。

---

### ⑤ 检测器显示为「乱码」—— 缺 1.21 的物品模型定义文件

现象：物品图标显示为紫黑格（缺失纹理）。

根因：**1.21 起物品模型定义搬到了 `assets/<ns>/items/`**，与旧的 `models/item/` 是两份不同的东西：

| 文件 | 作用 | 我当时的状况 |
|---|---|---|
| `assets/<ns>/items/<id>.json` | **物品模型定义**（把物品指向一个模型） | ❌ **漏了** |
| `assets/<ns>/models/item/<id>.json` | 模型本体（parent + 贴图） | ✅ 有 |
| `assets/<ns>/textures/item/<id>.png` | 贴图 | ✅ 有 |

没有第一项，游戏找不到这个物品该用哪个模型，于是渲染成缺失纹理。

补上的定义文件（格式参照 NeedsOfNature 自己的物品）：

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "noncreatureaffinity:item/affinity_detector"
  }
}
```

同时把 `display` 里的缩放调小了一点，让平板拿在手里不那么占满屏幕。

**验证**：客户端启动后日志中**没有任何**与本次物品/模型/纹理相关的错误或警告
（唯一一条 `missing pack 'kaceyk:kcow'` 是 NeedsOfNature 自己的内容包问题，与本模组无关）。

---

### ⑥ 检测器「右键没反应」的真正原因：状态被两端各切了一次

排查过程记录（这轮花的时间最多，值得写清楚）：

**现象**：图标修好后，右键开启扫描，聊天栏能出现提示，但画面上什么都没有。

**第一步：确认 HUD 到底有没有在画。**
`HudElementRegistry` 迁移后在渲染入口加了日志，实测出现
`affinity overlay is drawing (HUD element active)` —— **HUD 是好的**，问题不在渲染。

**第二步：查状态。** 打开双方日志后发现：

```
[Render thread/INFO] detector toggled -> true      ← 客户端切成「开」
[Server thread/INFO] detector toggled -> false     ← 服务端也切了一次，切回「关」
```

`Item#use` 在**客户端和服务端都会被调用**（客户端负责预测，服务端负责权威执行）。
两侧各有**独立的内存副本**，于是它们互相翻转，客户端的状态根本稳不住。

**第三步：为什么环境判断没用。**
先试了 `FabricLoader.getEnvironmentType() == EnvType.CLIENT`。
结果在开发环境里**服务端线程也返回 CLIENT**（单进程开发环境只有一套 classpath），
日志显示 Server thread 照样在切。

**最终修法：按线程判定。**

```java
public static boolean isActive() {
    if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
        return false;
    }
    MinecraftClient client = MinecraftClient.getInstance();
    return client != null && client.isOnThread();   // 只有客户端自己的线程算数
}
```

**验证结果**（实测日志统计）：

```
Server thread: 0 次 toggle     ← 服务端不再碰状态
Render thread: 3 次 toggle     ← 只有客户端在切换
affinity overlay is drawing (HUD element active)
```

扫描状态现在是**客户端权威**：服务端只负责播放点击音效，完全不参与状态决定。

> ⚠️ **另一个容易被忽略的点**：排查时发现你游戏目录里的 jar 是 **77 KB / 01:00** 的旧版本，
> 而当时最新构建已经是 **108 KB**。也就是说那几轮的部分现象（紫黑格等）
> 是「测试了旧 jar」造成的。以后请以 jar 体积/时间为准确认已更新 ——
> 我会在每次交付时给出体积。

---

### ⑦ 画面上什么都看不到的真正原因：颜色少了 alpha 通道

这是本轮最关键的发现，也解释了**两个看似无关的现象**。

**排查过程**（这次靠日志而不是猜）：

1. 渲染入口的周期日志显示
   `overlay tick: scanEnabled=true world=true` —— **HUD 在画、状态是开的、世界正常**。
   也就是说问题既不在注册，也不在状态。
2. 既然在画却看不见，只剩绘制细节。于是审计了全部颜色字面量，发现问题所在：

```java
context.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFFF55);
//                                                          ^^^^^^^ 只有 6 位
```

**6 位十六进制在 ARGB 里等于 alpha = 0，也就是完全透明。** 写进去等于没画。

**这完美对应了你反馈的现象**：

| 现象 | 原因 |
|---|---|
| 配置界面**说明框能看到**（还截了图给我） | 说明框用 `Tooltip.of(...)`，走 Minecraft 自己的样式，不经过我的颜色 |
| 但配置界面**项目名看不到** | 项目名用我写的 `0xFFE0A0` → 透明 |
| 好感度检测器**数字、读数全部看不到** | 用的是 `0xFFFF55` / `0xFFCC66` / `0xFFFFFF` → 全透明 |
| 数值数字**能看到**（黄色/青色） | 那是 `SliderWidget` 自己渲染的，我没参与 |

**修复**：全部改为 8 位 ARGB（`0xFFxxxxxx`），共 9 处。
同时加了一个一次性「投影探针」日志，输出算出的 NDC 坐标，便于以后核对投影方向：

```
[NoN Affinity] projection probe: nearby=N labels=M w=.. ndc=(x, y) fov=..
```

---

## 按键说明（可自由修改）

- 按键绑定名称：**NoN Creature Affinity → 主动求欢（看向生物后按下）**
- 默认键：**F**
- 修改方式：游戏内「选项 → 控制 → 按键绑定」，找到 *NoN Creature Affinity* 分类即可改键或解绑。

另一个按键：

- **打开配置界面**：默认 **K**（同一分类下，同样可改键）。

> 默认选 F 而非更直觉的 H，是因为 **NON 已占用 M、H、G、1、0**
> （`start_manual_peak=77`、`player_preferences=72`、`give_up=71`、`model_alignment=49`、
> `block_requirement_visualization=48`）。选 F 可开箱即用不冲突。

---

## 尚未实现 / 已预留

| 配置项 | 现状 |
|---|---|
| `friendlyAttackDamageMultiplier` | **只读未用**。1.21.11 的 Fabric API 没有「修改伤害数值」的事件（`ServerLivingEntityEvents.ALLOW_DAMAGE` 只能拦截 boolean），要生效必须 Mixin 注入 `LivingEntity.damage`，会与 NON 的 `LivingEntityDamageMixin` 争用同一宿主，风险高于收益，故暂不实现。 |
| `debugShowAffinityInName` | **只读未用**，预留给调试。 |
| `forcedMatingGraceTicks` | **只读未用**，当前强制交配立即开始（玩家随时可用攻击脱身）。 |
| `allowVanillaTamedCreatures` | **只读未用**，预留给「是否与原版已驯服生物建立关系」的开关。 |

---

## 下一步

- [x] 用测试内容包让开发环境自成闭环 —— **已完成**（`testpack\`，日志实测加载 2 条定义）
- [x] 启动自检 + `/nonaffinity diagnose` 诊断 —— **已完成**
- [ ] 进游戏实测交互流程：按键（F）触发交配、好感增长、阈值驯服、求欢与拒绝、后代继承
      需要图形环境（`gradle runClient`）或装到你的整合包里跑
- [ ] 死亡 / 跨维度 / 多人服务器场景实测
- [ ] （可选）把测试包的占位模型换成真实 geckolib 模型，用于观感验证

