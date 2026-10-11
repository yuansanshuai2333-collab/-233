# 技术进度报告与待协助事项（rc.40 · 中性口径）

> 面向外部审查者的**纯技术**交接口径。术语映射同上一份中性报告：
>
> | 文中术语 | 含义 |
> |---|---|
> | 本体 / 上游 | 依赖模组 NeedsOfNature（NON），提供动画引擎与播放控制 |
> | 流程 | 引擎一次播放的动画交互（有唯一 `instanceId`） |
> | 关系值 | 本模组维护的玩家–生物数值（affinity），有上下限 |
> | 奖励物 | 关系值到上限时发放的物品（friendship token） |
> | 中途脱离 | 玩家在流程未自然结束时使其终止（escape/abort） |
> | 强制启动 | 本模组在"请求被拒后"自行启动的流程（`forced=true`） |
> | A/D 提示条 | 上游自带的连点进度界面（用于中途脱离） |
> | 攻击型流程 | 起始上下文声明为 `ATTACK` 的流程；上游只对这类流程下发脱离控制 |
>
> 版本：**1.0.0-rc.40**，候选，未发布正式版。

---

## A. 目前进度

### A.1 产物与质量门

| 项 | 值 |
|---|---|
| 包 | `build/libs/naturalrelationship-1.0.0-rc.40+1.21.11.jar` |
| 体积 / SHA256 | 299559 字节 / `41F490187A6E00B6982775D549F9BFE3A4B1829AFE795D9E923B91C3CAB7DB06` |
| 自动测试 | **249 用例 / 0 失败 / 0 错误 / 33 个测试类**（上一版 246 / 32） |
| 配置 | 无新增；界面说明按源码核实结果补全（见 B4.3） |

### A.2 本批主线：**修掉上一版"从未生效"的功能**

上一版（rc.39）的目标是"强制启动接上上游自带的 A/D 提示条"。**该功能一次都没生效过**，
原因是一处必然抛异常的调用：构造上游的"玩家攻击"载荷时，把其中一个参与者字段传了 `null`，
而上游该记录的构造函数要求三个参与者字段**全部非 null**，否则抛 `IllegalArgumentException`。
异常被兜底分支吞掉 → 退回普通启动 → 只留一条 WARN。**246 个测试全绿，因为没有任何一条真正调用上游的构造函数。**

### A.3 定位方法：反编译上游本体

上一份报告把这条列为 **B2：需要外部协助确认"生物攻击"载荷语义**。本批改为自行核实：
用 Vineflower 全量反编译上游 jar（766 个类），逐条读源码。

| 问题 | 核实结果 | 依据位置 |
|---|---|---|
| 上游自己的"生物攻击玩家"怎么填该载荷？ | **不填**。它用「ATTACK 模式 + 无该载荷」的形状，由 `buildModeContext(true)` 产生（public static） | `NeedsOfNature:2652`、`:1147/5039/5058`、`NonGatherSystem:435`、`NonPlayerAttackSystem:267` |
| 该载荷属于谁？ | **仅用于玩家对玩家**。判据 `isPlayerAttackContext(ctx) = 载荷 != null`；生物攻击用 `attack && !isPlayerAttackContext(...) && hasMobActor(...)` 识别 | `NonGiveUpSystem:71-73/104/118-119` |
| 手造该载荷可行吗？ | **不可行**。构造函数拒绝 `null`；补全后又会让上游认为"**玩家**在攻击该玩家" | `NonAnimationSessionData:111-129` |
| 没有上游的攻击进度对象时，提示条为什么仍会动？ | **进度由客户端本地累积**（该角色标记为 `NONE` 时），满格后客户端请求停止该流程 | `NonHudOverlay:752-786`、`NeedsOfNatureClient:1067-1084` |
| 谁算"脱离成功"？ | 服务端收到该停止请求且**无攻击进度对象**时，直接走它自己的脱离结算，并停止流程 | `NeedsOfNature:6680-6693`、`:6695-6724` |

**→ 正确形状就是上游自己那个"无载荷的 ATTACK 会话"。** 旧文档 `STRUGGLE-FINDING.md` 的相反结论
（"无进度对象时连点会被丢弃"）已证伪，该文档整篇改写并标注修正。

### A.4 改动清单

| # | 内容 | 理由 |
|---|---|---|
| 1 | 强制启动改用上游自己的"无载荷 ATTACK 会话" | 修掉必然异常；并让上游按它自己的生物攻击语义处理，而非伪装玩家间交互 |
| 2 | 阶段列表改取上游自己的定义（不再手写映射） | 手写映射丢失了每个道具的**抑制槽位掩码**（动画期间手持物显隐错误）；现在与上游公开启动入口同源 |
| 3 | 启动日志新增"可脱离阶段计数" | 提示条不出现时，日志可先排除"内容包把阶段标成不可脱离"这一种可能 |
| 4 | 新增契约测试（直连上游真实类）+ 字节码必需/禁止项 | 让"手造玩家间载荷"这类错误在单测阶段失败，而不是实机静默回退 |

### A.5 验证分层（**严禁混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 249 用例全绿 |
| 上游语义核实 | 通过：反编译上游全量源码，逐条对照 A.3 |
| 生产包反汇编 | 通过：5 条调用点在生产命名空间核对；玩家间载荷类型**已无任何引用** |
| 服务端启动（上游 + 完整模组组合 + 370 个定义） | 通过：`Done`、两个混入 `applying`、适配器 `enabled and verified`、零注入失败 |
| **客户端/实机行为** | **仍未验证**（唯一实测来自一轮旧日志） |

### A.6 生产包反汇编证据（可直接核对）

```
com/nonid/internal/animation/data/AnimationDefinitions.getDefinition:(Lnet/minecraft/class_2960;)Lcom/nonid/internal/animation/data/AnimationDefinitions$Definition;
com/nonid/internal/animation/data/AnimationDefinitions$Definition.stages:()Ljava/util/List;
com/nonid/NonAnimationSessionData.attack:()Lcom/nonid/NonAnimationSessionData;
com/nonid/internal/animation/server/ServerAnimationController.startNowWithContext:(Lnet/minecraft/class_3218;Lnet/minecraft/class_3222;Lnet/minecraft/class_2960;Ljava/util/List;Ljava/util/List;Ljava/util/List;Lcom/nonid/api/animation/NonAnimationDamageBehavior;ZLjava/util/UUID;Lcom/nonid/internal/animation/server/AnimationStartContext;ZZ)Ljava/util/UUID;
com/nonid/internal/animation/data/AnimationGameplayMetadata.escapable:(Lnet/minecraft/class_2960;)Z
```

`grep PlayerAttack` 在生产包里为空。

---

## B. 难以独立完成、需要协助的事项

### B1（最高优先，未变）实机验证只能在客户端做

- **卡点**：提示条是否出现、连点是否推进、成功脱离的上游副作用、按键模式、驯服流程，都需要真实客户端。
  本机只能启动专用服务端（没有玩家实体 → 任何"玩家 + 生物"流程都无法启动）。
- **本批的改善**：过去"提示条没出现"只能猜；现在启动日志会先给出"可脱离阶段数 / 总阶段数"。
  该计数为 0 时，直接说明内容包不允许脱离，无需再查。
- **需要协助**：用客户端跑 `HANDOVER-rc40.md` §7 的 5 条，按**实例**回传日志行 + 关系值 + 是否收到奖励物。
- **验收标准**：日志行与预期标记完全对应；任何不符，把原文回传定位。

### B2（**已由本批自行解决**，不再需要外部协助）

原问题："上游自己的生物攻击流程怎么填该载荷（尤其 `finishOwnerUuid` 是否必须为 null）？"
→ **答案：不填。** 该载荷仅用于玩家间交互，且其构造函数拒绝任何 `null` 参与者。
代码现使用上游自己的形状，**不再依赖这一确认**；若你仍有上游作者渠道，值得顺带确认一次。

### B3 停止原因读不到 —— 未变

- **卡点**：停止原因是上游的**包私有**枚举，只作为参数传给内部停止方法；以完整描述符注入被 Mixin 拒
  （`Scanned 0 target(s)`）。
- **现状**：自动完成 = tick 路径内联停止；其余一律不结算（宁可漏奖，不可错奖）。
- **需要协助**：评估更可靠的读取方式，或把"停止事件暴露原因"作为上游请求推进。
- **验收标准**：判定不再依赖"代码路径枚举"，而是直接使用上游报告的原因。

### B4 产品政策需要拍板（不应由实现者单方面决定）

1. **未脱离、自然结束的强制启动是否应增加关系值？** 现行：会（非主动加成）。
2. **用调试键推完流程**：现行"不结算、不惩罚"。是否应视为中途脱离并施加惩罚？
3. **提示条（`nonStruggleScreen`）默认开**是否接受它带来的上游副作用？本批已把清单从"猜测"
   升级为**源码级确认**：成功脱离的伤害、逃脱后短暂无敌、逃脱统计、逃脱后追击、Give Up 链、
   destroyed-skin 累积。
   → **【已于 2026-09-22 拍板：接受】** 默认保持开启；清单已写入配置字段注释与配置界面中英文说明。
4. **本模组自建脱离键**是否保留（默认未绑定）？现在它有明确定位：提示条不可用时的兜底。

### B5 长期运行的状态/内存边界（未变）

- 需要协助：多小时会话中定期执行统计命令，回传三个计数的回落曲线。

### B6 客户端兼容矩阵（未变）

- 仅依赖组与完整组合，我都只测了**服务端**；客户端专属模组的共存**未验证**。
- 需要协助：客户端两组各跑一次"能启动、进世界、退出重进、日志无注入报错"。

### B7 发布门槛的判断

- 本批把"依赖上游内部实现"从**未经验证的猜测**变成了**有源码依据的对接**：
  载荷用上游自己的 public 形状；阶段列表来自上游自己的定义；**没有任何**对玩家间载荷的伪造。
- 仍属内部面（但 public）的只剩两个入口：`ServerAnimationController.startNowWithContext`、
  `AnimationDefinitions.getDefinition`，都在**版本门禁 + 失败大声回退**之下。
- **需要审查者明确表态**：这样的依赖是否可作为正式版发布条件？
  若否决，退回"仅自建脱离键、不依赖内部入口"的形态，改动量小（兜底路径已存在）。

---

## C. 现成核对命令

```bash
# 1) 结算 / 适配器 / 脱离
grep -E "natural-completion adapter|stop-path adapter|mating settlement|settled as NONE|mating settled for|completed with|is breaking free|NON reported a successful struggle|started forced mating|never observed running|ignoring duplicate" logs/latest.log

# 2) 攻击启动是否失败（本批重点）
grep -E "could not start the forced mating as an attack escape|stage\(s\) escapable" logs/latest.log

# 3) 注入报错与回退
grep -E "InvalidInjection|Mixin apply for mod noncreatureaffinity|will NOT be granted" logs/latest.log
```

**字段含义**：证据为 `NATURAL_END` / `MANUAL_ADVANCE` / `INTERRUPTED` / `UNKNOWN`；
`escapedByPlayer` 表示玩家主动脱离；`applied=` 为**实际**变化量（受上下限夹取）；
`<n>/<m> stage(s) escapable` 为本批新增，`n = 0` 表示内容包不允许脱离，提示条不会出现。
（以上 grep 串是程序里的**字面标识符**，必须保持原样才能匹配日志。）
