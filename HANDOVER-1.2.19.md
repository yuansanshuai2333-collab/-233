# Natural Relationships 1.2.19 交互请求门槛与失败原因

## 本次要做的两件事（用户原话）

1. 当生物没有对应的互动动画时，不触发深入交流请求。
2. 当生物请求深入交流、玩家点同意、但无法触发时，根据原因提示为什么无法同意（如：没有动画、空间不足等），并且**不减好感**。

## 结论先说

两项都已实现，构建通过，规则层与源码层有测试覆盖，启动层有一次针对**交付 jar 本体**的实测启动到已加载世界。

**尚未做**：游戏内实际操作验证（点同意按钮、观察动作是否开始、观察好感数值是否不变）。
本环境没有输入自动化能力，无法代替玩家点击。第 2 条的核心行为属于交互层，因此**本次交付不声称两条行为已在游戏内验收**。

## 一、改动内容

### 1. 无动画不请求

`service/RequestSystem.java` 新增纯决策：

```java
public enum RequestVerdict { ALLOWED, NO_ANIMATION, REFUSED }

public static RequestVerdict requestVerdict(boolean hasAnimation, boolean otherRulesAllow)
```

两个请求入口都改为走这一个决策，因此不会各说各话：

- `tickRequests`（周期扫描）：在掷 `requestChance` **之前**先判定，不是 `ALLOWED` 就 `continue`。
- `canRequest`（"好友自己跑来找你"入口 `requestNow` 也走它）：末尾用同一个决策返回。

判定依据是 `RequestSystem.hasAnyAnimation(...)` → `NonBridge.hasMatchingAnimation(...)` → `NonIntegration.listCandidates(...)` **是否返回至少一个非空候选**。

### 2. 一个刻意的设计选择：判"有没有动画"，不判"现在能不能启动"

`listCandidates` 的候选里带 `startEligible()`（当前位置是否满足空间/支撑/墙面/水域等条件），本次**故意没有**用它做门槛。

理由：地面不合适时动画依然存在，生物仍然可以开口问；玩家同意后再把**真实原因**告诉玩家。
如果按 `startEligible` 卡请求，那么"旁边地面不平"就会让生物永远不请求，这不是用户要的。

代价（如实说明）：`listCandidates` 每次都会调用 NON 的 `findCandidates`，内部会求值启动条件。
它现在被放在一个每 `requestCheckIntervalTicks`（默认 100 tick）才跑一次的扫描里，且前面已经过滤掉非好友、好感不足、冷却中、能源不足、玩家已有待答请求的生物，因此命中集合很小。
本次**没有做缓存**：缓存会引入"内容包重新加载后旧答案还留着"的过期风险，而正确性优先于这点开销。这是一处已知的、未实测的开销。

### 3. 同意后无法启动时告知原因，且不减好感

`accept(...)` 里原来只发一句笼统的 `cannot_start_now`。现在改为：

```java
try (var feedback = InteractionStartFeedback.begin()) {
    NonBridge.clearMatingRefusal();
    started = NonBridge.startVoluntaryMating(world, player, pair.mob());
    if (started) { ... accepted ... } else { reportStartFailure(player, pair.mob(), feedback.reason(), world); }
}
```

`reportStartFailure` 用的是**手动菜单那条路已经在用的同一套原因词汇**：

- 文本键 `message.noncreatureaffinity.interaction_failure.<reason>`，`reason` 取自
  `InteractionStartFeedback.Reason`：`space / support / wall / water / busy / no_match / requirements / unavailable / engine_error / unknown`。
- 动作栏（`sendMessage(msg, true)`）与聊天栏（`false`）各发一次。
- 同时 `LOGGER.warn("[NoN Affinity] accepted request could not start: creature={} player={} reason={}", ...)`。
- 引擎没有报告具体原因时落到 `unknown`，键的文本是"引擎未提供具体原因，请记录时间并查看日志"——**不猜**。

#### ⚠ 更正（写《方案报告》时重新核查代码发现，优先级高于上面几行）

**这条路目前大概率只会显示"未知原因"，不是"空间不足"。上面"用真实原因"的说法只对了一半，特此更正。**

原因：`InteractionStartFeedback` 是"观测窗口 + 引擎消息"两段式，而引擎消息**只在 `observingEngine == true` 时才会被记录**；
全代码树里只有 `NonIntegration.startChosen`（第 519 行）会打开它。但 `accept` 走的是
`startVoluntaryMating → startMating → startCandidate`，**不经过 `startChosen`**，所以窗口开着却没人开启观测，
引擎的 `start_cancelled_not_enough_space` 等消息会在 `InteractionStartFeedback.engineMessage` 里被丢弃；
而 `startMating` 全程**一次都没有**调用 `InteractionStartFeedback.report(...)`。

结论：缺失玩家/世界、无候选、忙碌、候选都启动不了、引擎抛异常这些情况，`feedback.reason()` 都保持 `UNKNOWN`。
本次交付因此**尚未达成"根据原因提示"**，只达成了一个会显示"引擎未提供具体原因"的骨架。
详细证据链与三级回落修正方案见 `方案报告-1.2.19-互动请求门槛与失败原因.md` 第四节，建议出修正版 1.2.20。

同时说明这一层的教训：源码层测试当初断言的是"取了 `feedback.reason()` 并用对应键"，它**不能**证明那个 reason 真的被填上——
缺陷正是从这个缝隙漏过去的。修正时必须补一条针对"回落顺序"的规则测试。

**好感不变**：这条路径整段没有任何 `addAffinity` / `recordRejection` 调用，源码里也写了注释说明。
全模块只有两处扣好感：请求超时（玩家没理）与玩家**明确拒绝**。同意之后失败是游戏侧的结果，不罚玩家。

### 4. 关于用户举例里的"没有动画"

`Reason` 枚举里没有 `NO_ANIMATION` 这个值，所以"没有动画"不会作为同意后的失败原因出现——因为第 1 项改动已经让这种生物根本不会发出请求。
两条改动在这一点上是互补的：**没有动画 ⇒ 不请求 ⇒ 也就不会有"同意后发现没动画"**。

## 二、证据分层（请按层理解，不要跨层外推）

### 规则层（纯逻辑单元测试，真实执行）

新增 `service/RequestGateTest`，4 项全通过：

- `a creature with no interaction animation for this player must not ask`
- `having an animation is necessary but not sufficient: the other request rules still apply`
- `the no-animation answer is distinguishable from every other refusal`
- `start eligibility is NOT the request gate: an animation blocked by the ground still permits asking`

**这一层证明了什么**：`requestVerdict(false, *) == NO_ANIMATION`，且它与其他拒绝可区分；`hasAnimation=true` 只是必要条件。
**没有证明什么**：没有证明游戏里某个具体生物真的没有动画、也没有证明它真的不发请求。

### 源码层（断言源码文本的测试）

新增 `service/RequestFeedbackWiringTest`，6 项全通过：

- `the no-animation gate is applied on both request entry points`
- `the gate asks whether an animation EXISTS, not whether it could start right now`
- `an accepted request that cannot start is explained by its real reason`
- `a generic 'cannot start now' is no longer the answer to an acceptance that failed`
- `accepting and then failing costs no affinity`
- `every reason the feedback can report has a message in both languages`

**这一层证明了什么**：接线正确——两个入口都调用了同一个门槛；`accept` 的失败分支确实用 `feedback.reason()` 取原因、确实用 `interaction_failure.` 键、确实走动作栏+聊天栏、`cannot_start_now` 确实已从 `accept` 里消失；`accept` 里确实没有扣好感调用；10 个 `Reason` 在中英两个语言文件里都有键。
**没有证明什么**：源码断言**不等于行为**。它说明代码写了什么，不说明运行时会发生什么。

### 启动层（本机真实启动，本次新增）

用**交付目录里那个 jar 本体**放进 `run/mods/` 启动开发客户端（`runClient -PdevWorld=testworld`），日志 `dev-client-startup-1.2.19.log`：

- 模组列表里 `noncreatureaffinity 1.2.19+1.21.11`。这一次是从 `mods/` 目录加载**交付 jar 本体**，不是从开发 classpath 加载：为区分两者，先前一次对照启动刻意把源码里的版本号临时改成 `0.0.0-CLASSPATHMARKER`，游戏如实报告了 `noncreatureaffinity 0.0.0-CLASSPATHMARKER+1.21.11`；而这一次报告的是 `1.2.19+1.21.11`，说明生效的是 mods 里的交付 jar。对照运行结束后版本号已改回 `1.2.19` 并重新构建，重新构建产物的 SHA-256 与先前构建完全一致（`c46364e9…effd`），构建可复现。
- `[NoN Affinity] NeedsOfNature detected; animation integration enabled.`
- `[NoN Affinity] interaction failure message anchor: found`（本次功能依赖的 mixin 锚点在本次运行时被找到）。
- `370 animation definition(s) loaded`，随后正常进入世界、玩家登录、区块加载。
- 全文检索 `FATAL` / `has crashed` / `Crash report` / `InvalidMixin` / `MixinApply`：**0 命中**。

**这一层证明了什么**：这个 jar 在本机能启动、mixin 应用成功、NON 集成开启、没崩。
**没有证明什么**：启动成功与两条交互行为无关。它不能证明"没动画的生物不请求"，也不能证明"点同意后玩家看到了原因且好感不变"。

同时如实记录日志里两条**既有**告警，它们不是本次引入、也未被本次修复：

- `could not find advanceStage(Lnet/minecraft/class_3218;Ljava/util/UUID;)Z in ... ServerAnimationController` —— 下一阶段控制将不授予好感。这是 NON 本次运行版本的接口差异。
- 启动时 `Unable to delete file .../run/logs/latest.log`（上一轮客户端仍占用文件）。

### 交互层（本环境做不到，需要玩家）

以下都**没有**实测，不能算已验证：

- 找一个真的没有动画的生物，确认它**不会**发请求。
- 找一个有动画但地面不合适的生物，确认它**会**发请求，点同意后看到"空间不足"之类的原因。
- 确认这次失败之后好感**没有下降**（需要对比点击前后的数值）。
- 确认动作确实**没有**开始时玩家看到的是原因而不是静默。

## 三、构建与包检查

- `gradle clean build --offline --console=plain --no-daemon` → BUILD SUCCESSFUL。
- 测试总计 **55 个类 / 436 项，失败 0、错误 0、跳过 0**（1.2.18 基线是 426 项；本次新增 10 项）。
- `fabric.mod.json` 的 `version` = `1.2.19+1.21.11`，与文件名、`gradle.properties` 一致。
- 三份 Mixin 配置均为无 BOM 的 JSON（1.2.18 的编码修复未被破坏）。
- 与 1.2.18 jar 逐条目比较（SHA-256 of decompressed content）：

  | 项目 | 数量 |
  |---|---|
  | 移除 | 0 |
  | 新增 | 1 个 class：`RequestSystem$RequestVerdict.class` |
  | 内容变化 | 3 个 class + `fabric.mod.json` |

  变化的 class 是 `NonBridge`（新增 `hasMatchingAnimation`）、`RequestSystem`（门槛与失败提示）、`RequestSystem$Pending`（嵌套类随外围改动重编译）；`fabric.mod.json` 只差版本号。**没有任何资源文件变化**，语言文件与 Mixin 配置均未改动。

## 四、必须告知的一处既有问题：发布门本次仍然 FAIL

`node tools/release-gate.mjs <jar>` 对 1.2.19 报 `FAIL (25 problems)`：jar 内含 24 个"开发中"类。

**这不是本次改动造成的。** 对 1.2.18（玩家手上那一版）跑同一脚本，同样 `FAIL (25 problems)`，且这 24 个类的清单**逐项完全相同**。

原因是 `tools/wip-sources.txt` 这份"开发中清单"没有跟上现实：它仍把已经随 1.2.16/1.2.17/1.2.18 发行、玩家正在使用的功能（限量免费交易、亲属标签、交互失败提示）登记为"开发中"。这些类**在玩家现用的 1.2.18 里就在**。

本次交付的处理方式：**与 1.2.18 保持同一套类，不做单方面的清单改写**。因为 `-Pwip=false` 会把这 24 个类排除，而其中 `InteractionStartFeedback`、`NonStartFailureMessageMixin`、`NonBirthFamilyMixin`、三份 FreeTrade mixin 都已被 `mixins.json` 与初始化代码引用，排除它们等于让玩家失去现正在用的功能，甚至可能启动失败。

因此：本次交付**没有通过发布门**，这是如实结论。清单该不该更新，属于需要与玩家确认的独立决定，我没有代为决定。

## 五、安装

把 mods 里的旧版 naturalrelationship JAR 移出，放入本包这一个；**不要同时放两个版本**。无需删除或重置存档、配置。
1.2.18 及更早的交付目录保留用于复核，不应与 1.2.19 同时安装。

SHA-256：`c46364e9f2395a9114214593a8a0eb59df0e0211dbe7eb62784b9ac22614effd`
