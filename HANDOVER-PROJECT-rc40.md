# 项目工程交接说明（面向接手者）· 当前版本 1.0.0-rc.40

> 本文是**工程交接**：仓库结构、构建/测试/验证工装、产物事实、混入与可选集成、已知风险、中性待办、文档索引。
> **范围声明（重要）**：本文**不包含**本模组中"玩家与生物的性互动"子系统的需求说明、验收标准或后续路线图，
> 也不包含任何"如何让内容通过其他助手审查"的建议。需要那部分内容时，请由项目负责人或接手的执行者自行编写——
> 该子系统现有实现的状态由仓库中的 `HANDOVER-rc39.md`、`HANDOVER-rc40.md`、`PROGRESS-REPORT-rc40*.md` 记录。
> 本文只描述与题材无关的工程事实，供任何人（人或其他执行者）在此基础上继续。

---

## 1. 项目与技术栈

| 项 | 值 |
|---|---|
| 工作目录 | `I:\DSH\work\noncreatureaffinity` |
| 语言/工具 | Java 21、Gradle 9.2.0、Fabric Loom 1.15.4 |
| 目标 | Minecraft 1.21.11、Fabric Loader 0.19.5、Fabric API 0.141.6+1.21.11 |
| 编译期依赖 | Yarn `1.21.11+build.6`、GeckoLib 5.4.5、Trinkets 3.11.0（`libs/modmenu-compile-only.jar` 仅编译用） |
| 运行期可选依赖 | `libs/needsofnature-1.5.11.21.11.jar`（NON，本体 jar 也在 `libs/`，可反编译对照） |
| 入口 | `com.nonid.affinity.NonCreatureAffinity`（main）、`...client.NonCreatureAffinityClient`（client）、`...integration.ModMenuIntegration` |

## 2. 构建 / 测试 / 打包（可直接复制的命令）

```powershell
$env:JAVA_HOME='I:\DSH\work\tools\jdk21\jdk-21.0.2'
$env:GRADLE_USER_HOME='I:\DSH\work\noncreatureaffinity\.codex-gradle-home'
& 'I:\DSH\work\tools\gradle-9.2.0\bin\gradle.bat' --offline --console=plain `
    -p I:\DSH\work\noncreatureaffinity clean test build
```

- 产物：`build/libs/naturalrelationship-<版本>+1.21.11.jar`（版本号在 `gradle.properties` 的 `mod_version`）。
- 测试报告：`build/test-results/test/*.xml`（可用一行 PowerShell 汇总 `tests/failures`）。
- **注意（我踩过两次）**：不要用 PowerShell 的 `Get-Content -Raw` + `Set-Content -Encoding UTF8` 改
  `gradle.properties` / 语言 JSON——会用 ANSI 解码破坏中文并写入 BOM，导致构建失败。用编辑器或写文件工具。

## 3. 当前产物与版本事实

| 版本 | 包 | 体积 / SHA256 | 测试 |
|---|---|---|---|
| **rc.40（当前）** | `build/libs/naturalrelationship-1.0.0-rc.40+1.21.11.jar` | 299559 字节 / `41F490187A6E00B6982775D549F9BFE3A4B1829AFE795D9E923B91C3CAB7DB06` | 249 / 33 类 |
| rc.39 | 同目录 rc.39 包（已被 rc.40 取代） | 299763 字节 / `B1DC64A56D4AB8BDA2E4DAA56C3464F646ADE7CC44DE4FA43FC89C87B8C883FF` | 246 / 32 类 |
| rc.33（驯服入口修正） | `...rc.33...jar` | 对照 `HANDOVER-rc33.md` | 219 / 30 类 |

- 版本推进规则见 `gradle.properties` 注释；每批必须换版本号以便区分产物。
- 候选状态，**未发布正式 1.0.0**。

## 4. 代码地图（包 → 职责，中性描述）

| 包 | 职责 |
|---|---|
| `com.nonid.affinity` | 主入口：配置加载、tick 注册、事件注册、共享工具（`dataOf` 等） |
| `.config` | `AffinityConfig`（全部选项与夹取）、配置界面（client 包内）、语言审计测试 |
| `.data` | `CreatureAffinityData`（附件持久化 + NBT 读写）、`PlayerAffinity`（单玩家数值）、`CompanionMode`、`AffinityAttachments`、`AffinityHolder` |
| `.service` | 规则与业务：`RelationshipRules`/`RelationshipSync`（归属与统一入口）、`AffinityService`（数值读写与日志）、伙伴体系（`CompanionService`、`CompanionFollowService`、`CompanionMovementGoal`、`CompanionCombatController`、`CompanionRecall`、`FriendlyDefenderSystem`、`RequestSystem`、`InheritanceService`）、投射物归因（`ProjectileStamps`、`AttackStampHandler`）、`AirSupply`、结算状态机（`MatingSessions`、`MatingStopEvidence`、`MatingSettlementRules`） |
| `.service.policy` | **纯策略**（无 Minecraft 类型，可直接单测）：`CompanionCombatPolicy`、`RecallFlow`/`RecallResultRules`、`StallExitRules`、`ModeRangeRules`、`EngagementRules` 等 |
| `.mixin` | 10 个常驻混入（见 §5） |
| `.non` | 上游（NON）适配层：`NonIntegration`（全部实际调用）、`NonBridge`（对外门面 + 反射/降级）、`NonReflect`、`NoNLog`、`MatingRefusal` |
| `.non.mixin` | **可选集成**：2 个混入 + `NonIntegrationMixinPlugin`（版本门禁与成员核对） |
| `.net` | 网络负载与接收器（`AffinityNetworking` 注册表 + 各 payload 记录类） |
| `.client` | HUD、探测器状态、配置界面、标签渲染、按键绑定 |
| `.command` | `/nonaffinity` 子命令（含 `stampcounts` 等诊断命令） |
| `.item` / `.integration` | 奖励物品；ModMenu 集成 |

## 5. 混入与可选集成

**常驻（`noncreatureaffinity.mixins.json`，`required: true`，`defaultRequire: 1`）**

| 混入 | 目标 | 注入点 |
|---|---|---|
| `EntityAffinityMixin` | `Entity` | `readData` TAIL（附持有人接口 + 加载后回填） |
| `VanillaTameRelationshipMixin` | `TameableEntity` | `setOwner(LivingEntity)` TAIL |
| `VanillaHorseTameRelationshipMixin` | `AbstractHorseEntity` | `bondWithPlayer(...)` TAIL |
| `CompanionAttackStampMixin` | `MobEntity` | `@WrapMethod tryAttack` |
| `ProjectileLaunchStampMixin` / `PersistentProjectileLaunchStampMixin` | 两类投射物 | `setOwner(Entity)` TAIL |
| `ProjectileStorageStampMixin` / `PersistentProjectileStorageStampMixin` | 两类投射物 | `readCustomData` TAIL |
| `ProjectileRemovalStampMixin` | `Entity` | `setRemoved` HEAD |
| `MobGoalAccess` | `MobEntity` | `@Accessor` |

**可选集成（`noncreatureaffinity.non.mixins.json`，`required: false`）**

- `NonNaturalEndMixin` → `ServerAnimationController`：观察"自动完成路径"与"显式推进阶段入口"。
- `NonAttackEscapeMixin` → `NeedsOfNature`：观察"脱离成功"回调，按实例 id 标记。
- `NonIntegrationMixinPlugin`：未装上游 / 版本不在验证前缀（`1.5.1*`）/ 成员缺失 → **整份配置关闭**，
  并在启动日志写明后果；成员核对读的是 Mixin 交给插件的 **ASM 类树（不加载任何游戏类）**——
  早期用 `Class.forName` 会崩服、用 `getParameterTypes()` 会把 `Entity` 提前加载而破坏其他模组的混入，两次都实测过。
- 可选混入的注入使用 `require = 0`：**这是可选集成，不应让上游改动导致玩家崩服**；缺失由插件的核对以 ERROR 报出，
  后果是安全方向（不结算），不是静默错发。

## 6. 验证工装（可复现，接手即可用）

1. **专用服务器隔离环境**：`.codex-temp/rc32-envA`（仅 Fabric API）与 `.codex-temp/rc32-envB`
   （非必要模组组合 + 内容包），各自独立世界，**不触碰玩家实例与存档**。启动命令见目录内 `console-*.log` 的文件头。
2. **RCON 驱动**：`.codex-temp/rcon.ps1`（Source RCON 客户端，PowerShell 单文件）。
   用法：`powershell -NoProfile -ExecutionPolicy Bypass -File rcon.ps1 -Command 'summon ...'`。
   注意脚本文件**不能**用 `. ` 直接点源（执行策略），用 `-File`。
3. **混入生效核对（强烈推荐）**：以 `-Dmixin.debug.export=true` 启动，然后在
   `.mixin.out/class/...` 上 `javap -p -c` 反汇编，**逐条核对注入点偏移**。
   必要时用 `-Dmixin.checks=true` / `-Dmixin.debug.verbose=true`。
4. **探针模组技巧**：`.codex-temp/probe-mod/`（600 字节、只有 `Class.forName` 的临时模组）。
   当某目标类在专用服务器上永远不会被加载（例如没有玩家实体时），用它强制加载，才能看到混入是否真的应用。
   **它只用于验证，绝不随模组发布。**
5. **字节码契约测试**：`src/test/java/.../service/CodeScanner.java`（极简 class 文件读取器）+
   `*ContractTest` / `*WiringTest`。断言"编译产物确实引用了正确的类/描述符"，并**禁止**某些类再被引用。
6. **配置语言审计**：`ConfigLanguageAuditTest` 从 `AffinityConfig` 字段与界面显示列表推导，检查中英双语齐全。

## 7. 已完成的工程里程碑

| 批次 | 内容 |
|---|---|
| rc.31 | 启动阻塞（抽象方法无 RETURN）；注入时机用生产字节码核对；状态映射死分支；逐实体只读访问器 |
| rc.33 | 驯服入口顺序错位（先写归属、后写标志）；马类覆盖核查；驯服入口回归测试 |
| rc.35/37 | 停止原因不可读时的判定策略：改为观察真实代码路径；显式推进阶段不再当作自然完成 |
| rc.36/38 | 中途脱离的数值惩罚；脱离成功不发奖励物；本模组自己的兜底按键（默认未绑定）；日志可读性（跟随调试行降级为 debug） |
| rc.39/40 | 可选集成扩展：启动时的执行路径适配、脱离成功回调按实例标记；**rc.40 修正 rc.39 的载荷形状并补了防回归契约测试** |

> 与题材相关的实现细节与验收步骤，请查 `HANDOVER-rc39.md`、`HANDOVER-rc40.md`；本文不复述。

## 8. 待办（仅列与题材无关的部分，含验收方式）

| # | 待办 | 验收方式 |
|---|---|---|
| 1 | 伙伴四种模式（跟随/漫游/等待/主动）的实机行为与召回距离（>10 格召回、等待/漫游不召回） | 实机 + 模式切换日志 + 距离记录 |
| 2 | 近战持续续期（超出停滞阈值）、远程（骷髅）续期 | 战斗日志中的续期行 + `/nonaffinity stampcounts` |
| 3 | 双伙伴互不干扰；低氧时等待模式的上浮循环 | 两个伙伴同时绑定 + `AirSupply` 相关日志 |
| 4 | `/nonaffinity stampcounts` 长时回落曲线（`melee/projectile/projectileUuidIndex`） | 多小时会话周期采样，确认回落到 0 |
| 5 | 客户端兼容矩阵：仅依赖组 / 完整组合，各跑"启动、进世界、退出重进、日志无注入报错" | 两份客户端日志 + 启动清单 |
| 6 | 发布流程固化（打包、SHA256、清单核对脚本） | 一键脚本 + 产物哈希记录 |
| 7 | 配置界面文案与语言审计随新选项同步 | `ConfigLanguageAuditTest` 全绿 |

## 9. 已知风险与限制（接手前必读）

1. **对上游内部实现的依赖**：可选集成的两个混入 + 一条"按上游启动方式启动"的调用路径，均绑定 NON `1.5.1*`。
   版本不符 → 配置整体关闭 + 启动日志说明；调用失败 → 捕获并回退到公开 API 路径（WARN 日志）。
   **风险**：上游若改内部结构，能力会静默降级（不崩、但行为退回）。接手者应保持"失败必须留日志"的约定。
2. **`catch (Throwable)` 会掩盖缺陷**：rc.39 的载荷形状错误就是这样被吞掉的——所有测试仍绿，
   因为测试只断言"代码提到了正确的类"。**建议**：对这类路径补"运行时契约测试"（直接对上游真实类断言，
   rc.40 的 `NonAttackPayloadContractTest` 是范例）。
3. **停止原因读不到**：`ServerAnimationController$StopReason` 是包私有枚举，以完整描述符注入被 Mixin 拒
   （`Scanned 0 target(s)`）。当前用"代码路径"区分自动完成与显式推进；更可靠的做法是上游暴露该字段。
4. **`require = 0` 的取舍**：可选集成故意不用硬失败（避免上游小改动导致玩家崩服），代价是必须靠
   插件核对 + 日志 + 契约测试兜底。接手者若改变这一取舍，请同时更新 `PROGRESS-REPORT-rc40*.md` 的说明。
5. **我的一处结论已被修正**：rc.35 文档曾断言"没有 Contest 时连点会被丢弃"，rc.40 依据上游反编译源码
   证明该结论错误（客户端进度本地计算，满格后发停止包 → 服务端走 `completeAttackEscape`）。
   接手时以 rc.40 的核实结果为准。
6. **专用服务器无法替代客户端验证**：本机只能起专用服务器（没有玩家实体 → 任何"玩家+生物"流程都起不来）。
   所有涉及客户端的行为必须由有客户端的一方实测。

## 10. 仓库文档索引

| 文件 | 内容 |
|---|---|
| `CHANGELOG.md` | 各版本变更（最新在顶部，含 rc.40） |
| `DEVELOPMENT.md` | 开发环境说明 |
| `HANDOVER-rc15..rc23, rc26, rc31, rc33..rc40.md` | 每批交接：产物、验证分层、人工复测清单 |
| `STATUS-rc6..rc26.md` | 早期状态记录 |
| `STRUGGLE-FINDING.md` | 上游"连点/脱离"机制调研（**§3 已被 rc.40 修正**，文中标注） |
| `PROGRESS-REPORT-rc39.md` / `-neutral.md`、`PROGRESS-REPORT-rc40.md` / `-neutral.md` | 进度报告（后两份为中性口径） |
| `REVIEW-REQUEST-gpt.md`、`REPORT-TO-GPT.md`、`REPORT-CODEX-rc3.md`、`EVIDENCE-rc24-session.md` | 历史审查往来与证据 |
| `MANUAL-TEST-*.md` | 人工测试清单 |
| `.codex-temp/` | 验证环境（专用服务器、RCON 脚本、探针模组）——**临时目录，非交付物** |
| `libs/` | 上游本体与编译期依赖 jar（可反编译对照） |

## 11. 建议的接手顺序

1. 跑一遍构建与测试（§2），确认 249/33 全绿、产物哈希与 §3 一致。
2. 起验证环境 A（仅 Fabric API）与服务端冒烟：确认启动 `Done`、适配器状态日志、无注入失败。
3. 起验证环境 B（完整组合 + 内容包）：核对两个可选混入是否应用（§6.3/6.4 的方法）。
4. 客户端复测（§8 第 5 项的矩阵 + 你关心的功能点）。
5. 再动上游适配层：**先补运行时契约测试，再改代码**（避免 §9.2 的覆辙）。

## 12. 边界声明

本报告由上一个执行者整理，仅覆盖**与题材无关的工程内容**。本文没有、也不应被用来：

- 编写本模组"玩家与生物的性互动"子系统的需求、验收标准或后续路线图；
- 提供任何规避其他助手/平台审查的措辞、代号或流程建议。

需要那部分设计与路线图时，请由项目负责人自行决定与编写；工程侧（构建、持久化、内存边界、混入正确性、
客户端兼容、发布工装）可按本报告直接继续。
