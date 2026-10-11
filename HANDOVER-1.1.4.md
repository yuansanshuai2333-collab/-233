# Natural Relationships 1.1.4：动画调试「下一阶段」也结算好感

> **1.1.4 相对 1.1.3 只改发布工具与文档，没有改动任何模组代码**：JAR 内**全部 class 与 1.1.3 内容完全相同**
> （用下面②的工具按解压内容 SHA-256 逐条目核对），唯一差异是重新构建必有的版本字符串。三件事：
> ① `tools/jar-diff.mjs` 改为比较**解压内容的 SHA-256**，不再用中央目录的 CRC-32——32 位校验码相等并不能
> 严格证明逐字节相同，旧输出"class 字节一致"说得比检查本身更满；措辞随检查一起改；
> ② 把发布门的**真实边界写清楚**：`tools/wip-sources.txt` 只拦得住**已登记**的开发中类，新增类若忘了登记，
> 构建与门禁会一起放行（`VERSIONING.md` 里"写漏了也进不了交付"那句已删除），补偿控制是
> **与上一个已发布 JAR 的逐条目对比**（新增类会逐行念出名字）；
> ③ 修正"成交后各状态写在哪"：`uses` 写在 `TradeOffer`，村民经验与升级计时写在村民实体，
> 玩家经验是世界里新生成的 `ExperienceOrbEntity`；并把 1.2.0 的**治愈入口设计**按复核意见改正
> （从**旧实体**读治愈者 UUID、玩家离线时事实也要保留、**必须等附件转移之后**再应用到关系上）。
> 交付哈希与 1.1.3 不同是**重新构建**的结果（见 `VERSIONING.md` 的交付一致性清单）。
> **发布构建命令是 `-Pwip=false`，不是手工挪文件**；发布门与 jar-diff 的用法见 `VERSIONING.md`。

> 以下正文是 1.1.3 起的既有交接内容（问题、根因、安装位置、实测步骤、工程说明），本轮未改动其结论。


## 问题

用「自然之需 动画调试」的按键 **`下一阶段（调试）`**（`key.needsofnature.animation.next_stage_debug`）
把一次交配动画一路推进到最后一个阶段时，动画正常结束、也没有任何报错，但**好感完全不结算**，
既不增减也没有聊天提示。

## 根因（已定位到具体分支，不是推测）

NeedsOfNature 的 `ServerAnimationController.advanceStage(world, instanceId)` 在
**已经是最后一个阶段**时会调用
`stopInstance(world, instanceId, true, StopReason.NATURAL_END)` 并触发本模组监听的 `STOPPED` 事件——
也就是说，这条路径上动画停下来的位置，就是它自己的**末阶段终态**。

本模组用 `NonNaturalEndMixin` 分别观察 NON 的两条结束路径：

| NON 的路径 | 记录的证据 | 记录位置（1.1.0） | 1.0.45 的结算 |
|---|---|---|---|
| `maybeAdvanceStages`（tick 驱动，末阶段时长跑完） | `NATURAL_END` | **该分支派发停止事件的那次 `fireStopEvent(...)` 调用** | 正常结算 |
| `advanceStage`（显式推进＝调试键 / 第三方 API） | `MANUAL_ADVANCE` | **仅**该方法里"当前已是末阶段 → 结束动画"那一条 `stopInstance` 指令 | **一律不结算** |

> 两处记录位置都必须是"结算**读得到**标记"的那一步：
> `advanceStage` 的末阶段分支在 `stopInstance` **内部**派发停止事件，所以包住该调用即可；
> 而 `maybeAdvanceStages` 是先做挂载还原调度、**之后**才 `fireStopEvent`（NON 1.5.11 字节码里分别在
> 偏移 155 与 239），所以自然结束必须包住 `fireStopEvent`，否则 `finally` 会在事件之前清掉标记
> （这正是 1.0.49 自然结束回归的原因，详见下节）。

而 `MatingSettlementRules.decide` 在 1.0.45 里只承认 `NATURAL_END`：

```java
if (facts.evidence() != Kind.NATURAL_END) return Decision.NONE;   // 1.0.45
```

于是调试键把动画推到末阶段 → 证据是 `MANUAL_ADVANCE` → `Decision.NONE` → 好感不结算。
**这就是「按了下一阶段却不增减好感」的唯一原因**，与动画本身是否正常播放无关。

> 证据的记录位置是关键：`advanceStage` 既能**推进到中间阶段**，也能**在末阶段结束动画**。
> 1.1.0 只在后者的那条指令上记录完成，绝不在"整个调用期间"记录——理由见下节。

### 1.0.45 当初为什么这么写（必须保留的那部分道理）

`advanceStage` 这条入口**同时被本模组自己的「脱身」动作使用**
（`NonIntegration.breakFree`，用循环调用 `NonAnimationApi.advanceStage` 来结束强制交配）。
所以「手动推进」里混着两种性质完全不同的情形：

- 玩家用调试键把动画**播放到结尾** → 应当结算；
- 玩家**挣脱**一场强制交配（本模组正是用同一条 API 把它结束掉）→ 绝对不能结算，还得扣分。

1.0.45 的写法为了挡住后者，把前者一起挡掉了——**挡错了地方**。

## 修改

**结算规则改为承认两条被真实观察到的结束路径**，其余一律不承认：

```java
// MatingSettlementRules
public static Decision decide(Facts facts) {
    if (facts.alreadyHandled() || facts.interrupted()) return Decision.NONE;  // 顺序关键
    if (!observedCompletion(facts.evidence()))   return Decision.NONE;
    return playerInitiated(facts) ? REWARD_PLAYER_INITIATED : REWARD_VOLUNTARY;
}

public static boolean observedCompletion(Kind evidence) {
    return evidence == Kind.NATURAL_END || evidence == Kind.MANUAL_ADVANCE;
}
```

**「挣脱」为什么仍然一分不给**：`breakFree` 在推进之前就先
`SESSIONS.markPlayerEscape(...)` 把该会话标记为「玩家逃脱」，而 `decide` 的
**第一行**（`facts.interrupted()`）在读取证据之前就直接拒绝。所以区分两者的不是证据类型，
而是会话状态——这正是本次修改能够安全放开 `MANUAL_ADVANCE` 的原因。

> 说明：`decide` 里 `alreadyHandled` 与 `interrupted` 两项检查都没有副作用、失败都返回 `NONE`，
> **交换它们的顺序不改变任何结果**。真正必须成立的性质只有一条：**发放奖励之前必须已经检查过逃脱状态**
> （即 `interrupted` 的检查必须出现在任何 `AffinityService` 调用之前）。

- `observedCompletion` 写成「只点名完成项」而不是「非中断即完成」：以后新增 `Kind` 时默认被拒绝，
  必须有人显式决定它的含义。
- **为什么不在"进入 advanceStage 期间"记录**：`advanceStage` 推进到**中间阶段**时会通过
  `advanceToStageIndex` **同步**触发 NON 的阶段推进事件；第三方扩展在该回调里停掉动画是完全可能的，
  那种停止是「中断」而不是「完成」。此外，若在方法入口记录、在返回处清理，一旦方法内部抛异常，
  清理可能被跳过而留下错误的完成标记。把标记放在**真正结束末阶段的那条指令**上，
  以上两种误判都不存在，也不需要任何"调用期间"状态。
- 调试**开始**的动画（`DebugStartAnimationC2SPayload`）本来就只是「本模组之外的自主动画」，
  所以按调试键结束它得到的 `startedByMod=false`，结算为 `REWARD_VOLUNTARY`（基础增幅），
  不会误发「玩家主动发起」的额外奖励。**但友情之证是会发的**——见下节。

### 调试链路的友情之证规则（如实说明）

`AffinityService.addAffinity` 的赠证闸门是「变化量被截断为 0（即好感已封顶）+ 这次是好感**正**变化 +
原因被 `isMatingGainReason` 认作一次完成交配」。调试动画结束走的是**普通完成路径**
（`REWARD_VOLUNTARY`），因此只要**好感已封顶且基础增幅为正**，它**同样**会发放友情之证。

本模组**没有**为调试链路单独设限，这是有意选择：调试链路完整复现"真实交配结算"的行为，
而不是额外的捷径或例外——调试开始本身不发证（它不是一次完成），调试结束则按同一套规则结算。

> 作为对照：**由调试开始触发的动画，在它完成之后**只结算基础增幅（`affinityPerMating`），
> 不给「玩家主动发起」的额外奖励（`affinityBonusPlayerInitiated`），因为 NON 的调试开始事件
> 在外部无法与"生物自主发起"区分。（"调试开始"是启动动作，本身不结算任何好感——
> 好感是在该动画结束时结算的。）

## 村民交易优惠（1.1.0）

### 折扣比例可调（0-100%）

- 新增配置项 **`villagerDiscountMaxPercent`**（默认 **20**，可在配置界面用滑块设为 0-100）。
- 成年村民按 `好感度 / friendThreshold` 线性缩放该折扣；100% 表示挚友可以把价格降到**最少 1 个物品**
  （不会真的免费——那已不是折扣而是另一种交易，原版价格运算也假定至少 1 个）。
- 例：原价 20、好感满：20% → 减 4（20→16）；50% → 减 10（20→10）；100% → 减 19（20→1）。

### 与原版优惠叠加、互不覆盖

折扣**按每笔交易的原价计算**，并把结果加进该交易的 `specialPrice`——这正是原版自己用来记录
"村民 gossip / 治愈僵尸村民 / 村庄英雄 / 需求"优惠的同一个加法位置。已从原版字节码确认显示价公式为：

```
显示数量 = clamp(基础数量 + max(0, 需求加成) + specialPrice, 1, 物品最大堆叠)
```

于是：**两者相加**——治愈过僵尸村民的村民，其原版折扣照旧存在，不会被本模组覆盖或清零；
本模组的折扣也不会因为原版已打折而被缩小（因为算的是原价，不是显示价）。
撤销的独立性见下文"兼容边界"，那里如实说明了本模组只能还原**自己记录的基准**。

> **原版 `prepareOffersFor` 到底怎么改价（1.1.0 从本地映射 JAR 反汇编确认）**：
> 它用 **`increaseSpecialPrice(...)`（追加）**写入声望优惠与"村庄英雄"加成，**不是**覆盖式赋值；
> 也**不能**假设它在方法开头必定清零——清零发生在**方法之外**（`clearSpecialPrices` 由
> `beginTradeWith` / `sayNo` / `setCustomer` / `resetCustomer` 调用，字节码顺序是先清零再 `prepareOffersFor`）。
> 因此本项目**不再把 `prepareOffersFor` 模拟成"无条件覆盖基准"**，也不再声称它开头必清零；
> 清零只作为**被观察到的显式事件**处理（`clearSpecialPrices` 钩子里清空记账）。
> 相应地，报价流程的模拟顺序是：**HEAD 还原 → 原版方法体 → TAIL 记录并施加**。

**100% 不保证最终价格是 1，也不构成任何无条件的最低价**：折扣只作用于**原价**，而

```
显示数量 = clamp(基础数量 + max(0, 需求加成) + specialPrice, 1, 物品最大堆叠)
```

其中 `specialPrice` 里还叠加着**所有**其他优惠（原版 gossip、治愈僵尸村民、村庄英雄，以及其他模组）。
所以：

- 原价 20、需求 +10、本模组 100%（−19）、无其他优惠 → `20 + 10 − 19 = 11`；
- 同样条件下原版治愈优惠再减 7 → `20 + 10 − 19 − 7 = 4`。

本模组的规则只有一条：**按原价计算、不补贴需求**（需求是原版加的，抵消它等于补贴这笔交易，
原版自己的 gossip 折扣也不抵消它）。**不要**把"最低价 = 1 + 需求"当作保证——那只在"没有其他优惠、
且未触及堆叠上限"的简化情形下成立。代码里的 `firstBuyCountWithDiscountOnly(...)` 同样只描述
"仅计本模组折扣 + 需求"的结果，其注释写明了这个适用范围。

与其他模组的兼容边界（**1.1.0 起改为显式记账，说明随之重写**）：

本模组不再靠"读价格"判断自己的折扣还在不在，而是**记录**它给每个交易写入了什么：

| 时机 | 动作 |
|---|---|
| 每轮报价开始时（`prepareOffersFor` 头部） | 把上一轮记录下来的**基准值**原样写回（真正撤销，而不是"不抬高"） |
| 每轮报价结束时（`prepareOffersFor` 尾部） | 此时字段里是**原版刚算好的值**，把它记为基准，再写入 `基准 − 折扣` |
| `clearSpecialPrices`（原版自己清零）之后 | 丢弃全部记录——原版已经把价格清空，留着记录只会把过期基准写回去 |
| 补货 / 打开交易窗口时 | 撤销 → 重新施加（同一套逻辑） |

这样得到的实际行为：

| 情形 | 结果 |
|---|---|
| 连续刷新 N 次 | 价格**始终是 −4**，不会 −8、−12 地累加（这是 1.0.52 的缺陷，见下） |
| 折扣变小 / 关闭（好感下降或把百分比设为 0） | 旧折扣被**真正撤销**，价格回到基准 |
| 原版治愈折扣存在（基准 −5） | 本模组在其上叠加（−9），撤销后仍保留 −5 |
| 外部把价格重置为 0 | 撤销会写回基准（**不会**出现"基准 + 折扣"式反向加价） |
| 外部在两次刷新之间追加优惠 | 撤销会还原本模组自己的基准，因此**不会**把本模组的折扣叠加两次 |

**策略与代价（1.1.0 明确为"快照优先"，并撤回了所有"不会虚高"的保证）**

本模组选择的策略是：**记录的基准优先**。撤销时无条件写回记录下来的基准，**不检查当前价格**。原因是
一个数值无法区分两种可能——写入 +3 之后看到 +2，既可能是"别人减了 1"，也可能是"整个字段被替换成 2"，
而两者需要的撤销动作不同。因此：

| 情形 | 本模组的行为 |
|---|---|
| 外部在本模组窗口内**追加优惠** | 会被本模组的还原**覆盖**（快照优先），本模组折扣随后重新施加 |
| 外部在本模组窗口内**重置价格** | 同上：**必须由重置方同时清除记账**（原版 `clearSpecialPrices` 的钩子会调 `clear()`）才算"被观察到"，否则也会被覆盖 |
| 重置方未清除记账 | 旧基准会被写回——**这就是"不会虚高"不再成立的地方**，1.0.54 曾试图用一个正数特判绕过它，结果反而重新引入了"撤销失效"（见下） |

所以：**不要**把本模组的撤销当作"只删自己那一层"的保证，也**不要**指望它能与其他模组的写入互相保留。
若要真正同时满足"任意重置安全 + 外部增量保留 + 精确撤销"，需要一个**可观察的改价/重置协议**
（或明确只支持特定调用入口），这不是靠更多 `min`/`max`、相等判断或正负分支能解决的。该方向列为待确认事项。

> **1.0.54 的一个错误特判（已删除）**：那时在撤销里加了"基准为正且价格已变就跳过还原"。结果是本模组
> 折扣被留在价格里、又当作新基准再减一次：基准 +5、本模组 −2、外部 −1 时，刷新写成 0（应为 3）、
> 关闭为 2（应为 5）。删除该分支后，上面 12 项评审检查全部通过。

- **修改 `VillagerEntity` 自身的定价逻辑、或替换显示价计算的模组**（例如改写 `getDisplayedFirstBuyItem`
  的 Mixin）会改变上面这条公式，本模组无法保证与其叠加——那种情况下应以其行为为准。

### 一次完成只播报一次（且第二次及以后不会漏）

好感已封顶时，同一次完成既发友情之证、又送村民商品，原先会出现两条聊天消息
（"赠送了你一个友情之证" + "赠送了你一个友情之证和他的商品：X"），看起来像拿到两枚（日志只有一枚）。
现在：友情之证的提示**延后**到本次结算末尾，若同一次结算确实交付了商品，就只发**合并后的一句**。

**1.1.0 修正**：此前"是否需要提示"与"是否为首次赠证"共用同一个标志，导致**第二次及以后的赠证完全不提示**
（有商品时那条消息还自称是合并提示）。现在两者分离：

- **每次封顶交配都会发证、也都会提示**（这是既有规则：封顶后每完成一次就赠一枚，面板计数持续增长）；
- 只有**措辞**取决于是否首次：首次是完整说明，之后是"又赠送了你一个友情之证"（新增语言键
  `affinity_capped_gift_repeat`）；
- 同一次结算里"与商品合并"或"单独播报"二选一，绝不会两条。

判定与状态机（含连续两次赠证）已由 `VillagerDiscountTest` 覆盖。

## 影响面

| 情形 | 1.0.45 | 1.1.0 |
|---|---|---|
| 自然跑完末阶段 | 结算 | 结算（不变） |
| 调试键推进到末阶段 | **不结算** | **结算** |
| 调试键只推进到中间阶段（未结束） | 不结算 | 不结算（不变） |
| 推进到中间阶段时被第三方监听器停掉动画 | 不结算 | 不结算（**明确保证**：证据只在末阶段结束时记录） |
| 攻击 / 受伤 / 外部停止 / 参与者消失 | 不结算 | 不结算（不变） |
| 挣脱强制交配 | 不结算＋扣分 | 不结算＋扣分（不变） |
| 同一实例重复结束通知 | 只结算一次 | 只结算一次（不变） |

覆盖面只增加了「被请求的末阶段结束」，其余全部不动；`alreadyHandled` 去重与「一次只结算一个实例」
的会话机制沿用原有实现。

## 验证与边界

- `gradle build` 通过：**269 项测试，0 失败，0 跳过**（含 `NonAdvanceStageAnchorTest` 7 项与
  `VillagerDiscountTest` 9 项；锚点测试需要 `libs/needsofnature-1.5.11.21.11.jar`，本次构建中被实际执行而非跳过）。
- **评审提供的独立检查（`ReviewChecks.java` / `LifecycleChecks.java`，直接运行交付 JAR 的记账逻辑）**：
  两者都对**本版产物**运行通过 —— `ReviewChecks` 12 项全部 PASS（`Failures=0`），
  `LifecycleChecks` 覆盖"非零原版增量 / 补货不重复追加原版增量 / 显式清零 / 下一次不同原版优惠 / 关闭本模组折扣"。
  1.0.54 曾有 2 项失败（正基准 + 外部追加），1.0.55 起为 0 失败。两个脚本与运行输出均已归档进交付目录。
- 产物核对是**直接检查 JAR 字节码**，不是从源码推断：mixin 类里确实有
  `org/spongepowered/asm/mixin/injection/Coerce`（1.0.49 引入的高优先级修复），
  有 `@WrapOperation` 与 `CompletionMarkScope` 引用，**没有** `Inject`（旧的"仅前置注入"写法已彻底移除）；
  另经核对，`VillagerTradeRelationshipMixin.class` 中 `recordedBaseline` **已不存在**（第十一轮要求删除的死代码）。
- 新增 / 改写的断言：
  - `MatingSettlementTest#manualAdvanceToTheFinalStageCounts`：`MANUAL_ADVANCE` 三种事实组合的结算结果，
    以及它仍然不承担逃脱扣分。
  - `MatingSettlementTest#anEscapeIsRefusedBeforeTheStopEvidence`：对**所有** `Kind` 取值遍历断言
    「已逃脱」与「已处理」都必须结算为 `NONE`——这是本次改动的安全性主证据。
  - `MatingSettlementTest#advancingIsNotRestartingAndTheCallIsNotEvidence`：中间阶段推进不留完成标记；
    已决定的实例在 `forget` 之后不留残余证据；晚期标记无法复活已处理的实例；
    并且断言证据存储**不再对外暴露**「是否处于推进调用中」这一状态。
  - `NonAdvanceStageAnchorTest`（7 项）：读真实 NON class 断言**两个**锚点、描述符与参数布局；
    反向断言**错误所属类**、**错误重载**、**仅推进调用**、**自然结束锚点缺失**、**参数布局位移**都不会通过；
    **实际执行包装逻辑**（`CompletionMarkScope`）验证"正常返回 / 返回 false / 抛异常"三种结果下标记都被清除、
    动作只执行一次、异常原样传播；并断言**停止事件执行期间标记仍可读**（1.0.49 自然结束回归的直接守卫）、
    以及"自然结束锚点必须是 `fireStopEvent`、只有挂载还原调用不算"。
  - `VillagerDiscountTest`（9 项）：折扣上限可调（20% 保持旧数值、100% 可降到 1 个物品）、随好感线性缩放、
    边界（1 个物品 / 0 上限 / 负上限）不产生异常值、**按原价计算从而与原版优惠相加而非互相抵消**、
    **折扣只作用于原价、不补贴需求**、赠礼门槛、以及"每次封顶赠证都播报、仅措辞区分首次"的状态机。
    记账相关四项：**"原版准备报价"（含原版自身的增量）与"补货/打开窗口"两条流程各自成环、状态逐轮传递**
    （正/零/负基准、折扣变化、显式清零、两个不同交易都覆盖）；
    **生命周期测试**（清零 → 原版自身优惠 → 本模组折扣 → 再次清零）；
    **no-op 移除变异测试**（证明刷新测试不是空断言：把撤销改成什么都不做时，5 轮后必须漂移到 −20 而不是停在 −4）；
    以及记账策略本身（记录基准优先、显式清零才丢弃记录）。
  - 原有 `escapeEarnsNothingAtAnyTime`、`breakFreeTargetsOnlyThePlayersForcedMating` 保持通过。
- **实机已验证部分（由你在 2026-09-23 的日志确认）**：启动与进入世界正常、无注入签名或类加载错误；
  调试推进结束三次均成功结算；满好感赠证三次各一枚（累计 1/2/3）；未发现重复结算。
  > 那三次是本模组发起的**自愿**动画（`playerInitiated=true`、请求增幅 15），因此**不能**替代
  > "NON 调试开始"的基础奖励分支验证；自然完成、未满好感增幅、脱身扣分仍待实测。
- **边界（务必如实对待，验证范围要分清）**：
  - 1.0.49 的自然结束回归是**由代码检查 + 针对交付 JAR 的直接验证**确认的（按 NON 的实际执行顺序驱动
    JAR 内逻辑，得到"自然结束 → `INTERRUPTED` → `NONE`"），**不是**日志复现的；此前文档误写为"实机确认"，已更正；
  - 该修复由单元测试守卫（"事件处理期间仍可读"），但**修复后的自然结束以及后续版本的结算链路仍需一次实机确认**；
  - 1.0.49 的实测通过项（启动、手动推进结算、满好感赠证）**不能算作后续版本已验证**；
  - 尚未实测的项：真实 Mixin 装配、自然完成结算、未满好感时的实际增幅、脱身扣分、"调试开始"（非本模组发起）
    的基础奖励分支、连续两次赠证的提示、以及折扣相关的"交易窗口保持打开时刷新报价"。
- **证据缺口不止"实测"一项**（1.1.0 更正此前"唯一缺少的证据是实测"这一过于绝对的表述）：
  - 折扣记账本身除了单元测试与评审的 JAR 级检查外，**没有游戏内验证**；
  - 原版 `prepareOffersFor` 的增量行为是**反汇编确认**的，但"保持交易窗口打开时补货"这条路径的**实际扣款**
    仍需实机核对；
  - 赠证提示、自然结束、逃脱扣分等在真实客户端中的表现同样未验证。
- 未添加物品；新增 1 个配置项（`villagerDiscountMaxPercent`，默认 20，可 0-100）；
  依附版本规则 1.1.0（`VERSIONING.md`：**新增功能或新增物品**增第二位，修复/优化只增第三位）。
1.0.50–1.0.56 是这一轮开发中用过、但**从未对外发布**的过程编号，本轮功能与全部修复统一以 **1.1.0** 交付；
其后的修订依次为 1.1.1、1.1.2、1.1.3、1.1.4（均只改流程、工具与文档，不改玩法代码）。

## 1.1.4：工具与文档的三处纠正（本轮复核意见的处理）

| 复核意见 | 处理 | 可复现的验证 |
|---|---|---|
| **CRC-32 相同不能严格证明逐字节相同**；应比较解压内容或 SHA-256，或把输出改成"条目 CRC 一致" | `tools/jar-diff.mjs` 改为比较**解压后条目的 SHA-256**，输出改为"内容 SHA-256 一致"；说明里写明旧版本用 32 位校验码支撑"字节一致"是说过头了 | `1.1.2 → 1.1.3`、`1.1.3 → 1.1.4` 两次对比都逐条目给出 SHA-256；同一份 JAR 自比输出"identical entry for entry"；`--expect-identical-classes` 只对 class 断言 |
| **清单门禁只能拦已登记的开发中类**；新增类忘登记时构建与门禁会一起漏掉，不能声称"遗漏清单也无法交付" | `VERSIONING.md` 删掉"清单条目写漏了也进不了交付"，改写为**真实边界**（忘写 `-Pwip=false` 会被拦；忘登记新类不会被拦），并给出**补偿控制**：交付前用 `jar-diff` 与上一个已发布 JAR 逐条目对比，新增/删除/变化的 class 逐行念出名字 | 1.1.1 那次泄漏若走该步骤会显示 `added: 5 class`；`RECON` 与 `CHANGELOG` 同步改口径 |
| **成交后的状态写在哪要分开说**：uses 在 offer 上，村民经验与升级计时在村民实体上，经验球是世界实体 | `RECON-1.2.0-free-trades.md` 6.5 改为"落点表"逐项列出（`uses` / `experience` / `levelUpTimer`+`levelingUp` / `ExperienceOrbEntity` / 成就判定），并写明"免费但不消耗 uses"只是其中一项语义，不能顺带改掉其余项 | 见该文档 6.5 的表格与源码引用 |
| （另）**治愈入口设计有缺陷**：`handleInteraction(ZOMBIE_VILLAGER_CURED, …)` 在玩家离线/不在该世界时整段不执行，且发生在附件转移之前，改新村民关系会被覆盖 | 已按复核意见重做设计（**先改设计、不写实现**）：从**旧实体**读 `converter` → 落成**治愈事实**（离线也保留）→ **附件转移之后**再应用到关系上；不依赖同一事件内的监听器顺序；`handleInteraction` 只作**额外证据**、不作唯一入口。并列出 5 项必须补的测试 | 见 `RECON-1.2.0-free-trades.md` 6.2 的"修正后的入口设计"表与测试清单；**该实现尚未编写**，因此 1.2.0 未交付 |

1.1.2 → 1.1.3 与 1.1.3 → 1.1.4 两次对比的结论（原文见交付目录 `verification-jar-diff-*.txt`）：
条目 181 → 181，class 125 → 125，**新增 0 / 删除 0 / 内容变化 0**，唯一变化是 `fabric.mod.json`（版本字符串）。
1.1.1 的 JAR 确实已不存在，因此它那条"玩法未变"的说法仍然**只能靠历史记录**，不由本工具背书。

**仍未验证的项没有变化**：1.1.4 与 1.1.3 的 class 内容逐条目一致，所以下面"证据缺口"一节列出的
游戏内实测缺口（自然完成结算、真实 Mixin 装配、折扣刷新路径等）在 1.1.4 上同样成立。

## 安装位置（提醒：先确认启动的是哪一个实例）

本机存在**两个**已安装本模组的实例，版本必须一致，否则容易测到旧版：

| 实例 | 路径 | 1.1.4 交付时的状态 |
|---|---|---|
| I 盘测试实例 | `I:\DSH\work\noncreatureaffinity\1.21.11-Fabric 0.19.5\mods\` | 已更新到 **1.1.4** |
| C 盘游戏实例（此前日志来源） | `C:\Users\Administrator\AppData\Roaming\.minecraft\versions\1.21.11-Fabric 0.19.5\mods\` | 1.1.4 交付时**同步更新** |
| 散放的副本 | `I:\我的世界\naturalrelationship-1.0.49+1.21.11.jar` | 旧文件，**启动器若从这里加载会测到旧版**，请以实例 mods 目录为准 |

> 实测前请确认启动器实际使用的是哪个实例目录，并删除或更新残留的旧 JAR；
> 日志路径能直接反映这一点（`logs/latest.log` 所在目录即实际运行实例）。

## 实测建议

1. 装好 1.1.4，进游戏把「下一阶段（调试）」绑到一个顺手的键（`选项 → 控制 → 按键绑定 →
   自然之需 动画调试`）。
2. 站到一只好感可见的生物旁（手持好感度检测器，或 `/nonaffinity info`）。
3. 用调试键组合（选择参与者 → 开始动画 → 下一阶段）把动画**一路推到它结束**。
   预期：动画结束时出现好感变化提示，检测器数值上升；日志出现两行——
   - `[NoN Affinity] NON's advance entry is ending the FINAL stage of instance <id>; it will be settled as a`
     ` completion (the escape check still runs first)` ← **这一行证明注入确实生效**
   - `mating <id> (<animation>) completed with stopReason=null evidence=MANUAL_ADVANCE startedByUs=false`
     ` forced=false -> playerInitiated=false`

   > `stopReason=null` 是**正常**的：本模组的适配器观察的是**结束路径**，并没有读取 NON 内部的
   > `StopReason` 枚举字符串（该类型是包级私有，无法可靠注入）。请以 `evidence=MANUAL_ADVANCE` 为准，
   > 不要以 `stopReason=NATURAL_END` 作为通过判据。
4. 只按中间阶段（动画还在播）时**不应**出现上面那两行中的任何一行，也不应出现结算提示。
5. 反向验证（关键）：触发一次**强制交配**（让生物求欢后 `/nonaffinity reject`），然后按脱身键。
   预期：好感**下降**（配置的 `affinityLossOnEscape`），且不出现 `completed` 结算日志
   （第 3 步的第一行日志**会**出现，因为挣脱同样走这条推进入口——这正是必须靠逃脱标记拦截的原因）。

## 工程

- `service/MatingSettlementRules`：`decide` 改用新的 `observedCompletion(Kind)`；类注释重写了
  「为什么被请求的末阶段结束现在是完成」与「为什么挣脱仍然不给」。
- `service/MatingStopEvidence`：**移除了"处于推进调用中"这一状态**（原 `enterAdvanceStage` /
  `leaveAdvanceStage` / `isAdvancing` 与 `advancing` 集合全部删除），改为只在末阶段结束时记录的
  `recordFinalStageAdvance(UUID)`；新增窄清理 `clearCompletion(UUID)`（只丢完成分类，不抹掉原因信息）；
  `MANUAL_ADVANCE` 的文档同步更新。
- `non/mixin/NonNaturalEndMixin`：**两个**注入都改为 MixinExtras 的 `@WrapOperation`——
  - 手动路径：包住方法内**结束末阶段的那条 `stopInstance` 调用**（其接收到的
    `Object stopReason` 参数带 **`@Coerce`**；NON 的原因是包级私有枚举，本模组不能引用其类型，
    而 MixinExtras 会按 Mixin 的参数校验规则比对签名，缺该注解会被判为无效签名且 `require = 0` 无效）；
  - 自然结束路径：包住该分支**派发停止事件的那次 `fireStopEvent(...)` 调用**
    （**历史**：1.0.49 曾包住更早的"挂载还原调度"调用，导致标记在事件之前被清掉 → 自然结束一律不结算，
    这是 1.0.49 的回归，1.1.0 修正；`fireStopEvent` 是结算能读到标记的最后一步）；
  - 两条路径的"记录标记 + 日志"都在 `CompletionMarkScope` 内执行，`finally` 收回标记，
    因此"停止失败后残留完成标记"在两条路径上都不可能；手动路径命中时输出一行 INFO 作为"注入生效"的直接证据。
- `non/compat/CompletionMarkScope`（新增）：`run(UUID, Body<T>)` = `try { body } finally { clearCompletion }`。
  它是**可被单元测试直接驱动**的包装逻辑（混入的方法体无法被单元测试执行），用来证明"正常返回 / 返回 false /
  抛异常"三种结果下标记都被清除，以及**执行期间标记仍可读**（后者是 1.0.49 回归的守卫）。
- `non/compat/ServerAnimationControllerBytecode`（新增）：锚点常量（含方法描述符、参数索引）与纯 ASM 校验，
  不依赖 Mixin，因而可被单元测试直接驱动。**刻意放在 `non.compat` 而不是 `mixin` 包**：Mixin 会限制从 mixin
  专用包加载"非 mixin 的普通类"，放在那里会让插件（以及单元测试）在加载时收到 `IllegalClassLoadError`；
  `require = 0` 对此无能为力。
- `non/mixin/NonIntegrationMixinPlugin`：自检升级为逐项核对**两个锚点**——自然结束方法存在**且**参数 1
  仍是实例 id（`@Local(argsOnly = true, index = 1)` 所依赖的布局）**且**含 `fireStopEvent(...)` 调用；
  推进入口按**完整签名**存在**且**含 **NON 自己**的 `stopInstance(..., StopReason)` 调用（**核对所属类**，
  其它类的同名同参调用不算）。任一项缺失都以 ERROR 记录并说明后果（该路径不再结算好感）。
- `service/VillagerRelationshipBenefits`：折扣改为 `discount(baseCost, affinity, threshold, maxPercent)`
  并按 `getOriginalFirstBuyItem()` 计算；新增可测的赠礼门槛 `qualifiesForCompletionGift(...)`；
  赠礼消息在"同一次结算也发了友情之证"时改用合并键。
  （**历史**：1.0.51 曾加过 `canRestoreDiscount(...)`，1.0.52 曾加过 `specialPriceAfterApply/Remove(...)`，
  1.0.53 起因方案失效**已全部删除**，当前实现只走 `VillagerDiscountLedger`。）
- `service/VillagerDiscountLedger`（**1.0.53 新增**）：显式记录"本模组为每个交易记录的
  基准值与其写入后的净值"，撤销 = **无条件写回基准**，而不是从当前价格反推。三轮失败做法（"加回折扣量"会反向加价；
  "数值变了就不撤销"与"用 `min` 钳制"都**撤销不掉**，导致刷新累积 −4 → −8 → −12）全部弃用。
  **1.0.54 加的正基准特判已在 1.1.0 删除**——它重新引入了"撤销失效"：基准 +5、本模组 −2、外部 −1 时，
  刷新写成 0（应为 3）、关闭得到 2（应为 5）。现行策略是**快照优先**：一个数值无法区分"外部追加 −1"
  与"整字段被替换为 2"，因此不做任何猜测。
  它是纯 Java，因此**整轮流程**（还原 → 原版方法体 → 记录并施加，以及补货路径）可以在单元测试里逐轮驱动。
- `mixin/VillagerTradeRelationshipMixin`：改为编排上述记账——HEAD 写回基准、TAIL 记录基准并施加。
  TAIL 的基准**就是 `offer.getSpecialPrice()` 本身**：HEAD 已经把本模组上一次的写入还原掉，此刻该字段
  正是"原版刚算好的值"，直接记录即可，不做任何比较或钳制。
  （**已删除的历史实现**：TAIL 曾取 `min(当前价, 记录基准)` 作为基准，理由是"不把别的 TAIL 处理器更深的一次
  减价一起吃掉"；由于 HEAD 的还原已经消费掉记录，该表达式只能拿价格与自己比较，是**无保护效果的死代码**，
  1.1.0 已连同 `recordedBaseline` 方法一起移除。这是清理误导性代码，并非已复现缺陷；若将来要支持特殊重入，
  需要另行明确调用模型，不能依赖这个钳制。）
  `clearSpecialPrices` 后清空记录；每个 `TradeOffer` 一个稳定 id（计数器而非 identityHashCode，
  避免哈希碰撞让两个交易的记录互相顶替）。**不再假设 `prepareOffersFor` 开头必清零**：清零只在
  `clearSpecialPrices` 钩子里按"被观察到的事件"处理。
- `service/VillagerRelationshipBenefits`：把原先的 `minimumFinalCount` 更名为
  `firstBuyCountWithDiscountOnly(...)` 并在注释里写明它**只**描述"仅本模组折扣 + 需求"的情形
  （不构成任何无条件最低价）；1.0.52 那两个 clamp 辅助函数已随方案失效而删除。
- `service/AffinityService`：一次性结算状态改为同时承载**两件不同的事**——"本次是否发证"（每次封顶交配都为真）
  与"用哪一条措辞"（仅首次不同），并提供 `pendingCapGiftMessage()` / `pendingCapGiftKey()` /
  `isCapGiftFirstEver()`；消息在发证处构建、由结算在末尾发送（可合并、可单独），
  同一次结算绝不会出现两条。新增语言键 `affinity_capped_gift_repeat`。
- `config/AffinityConfig` + `client/AffinityConfigScreen` + 中英文语言文件：新增
  `villagerDiscountMaxPercent`（默认 20，钳制 0-100）与滑块/名称/说明。
- `non/NonIntegration`：结算日志新增 `evidence=` 字段，便于实机核对走了哪条路径。
- **关于 `require = 0`（措辞更正）**：它只能容忍"某个注入锚点在目标里找不到"这一种情况，
  **不能**兜底类加载错误、目标描述符错误、插件异常或其它注入失败。把它当作"NON 升级绝不会崩游戏"
  是不准确的；本项目对它的依赖仅限于"锚点找不到时不会中断整个适配器的装配"。
- 交付物：`I:\DSH\交付-自然关系-1.1.4\naturalrelationship-1.1.4+1.21.11.jar`（附 `.sha256`、
  `HANDOVER-1.1.4.md`、`CHANGELOG-摘要.md`、`评审意见处理-1.1.4.md`、
  评审脚本 `ReviewChecks.java` / `LifecycleChecks.java`、
  发布工具 `tools/release-gate.mjs` + `tools/jar-diff.mjs` + `tools/wip-sources.txt` + `tools/lib/zip-read.mjs`、
  `VERSIONING.md`，以及全部检查对**本次 JAR** 的原始输出 `verification-*.txt`）。
  **发布门与"发布构建"机制见 `VERSIONING.md`**：`-Pwip=false` 构建排除 `tools/wip-sources.txt` 列出的
  开发中源码，构建结束时自动跑门禁，门禁再对产出的 JAR 复核一次；
  两者都不依赖手工挪动文件，也都不以"打印一行警告"代替失败。
  **发布门的边界也要照抄**：它只拦"已登记"的开发中类；忘登记的新类要靠
  `node tools/jar-diff.mjs <上一个已发布.jar> <本次.jar>` 逐条目对比来暴露。
