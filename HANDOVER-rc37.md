# 交接说明 · 1.0.0-rc.37

> 本批：**根据你提供的 14:24–14:28 实例日志定位并修复一个真实误判**——
> 用「下一阶段（调试）」按键把动画推过最后阶段，被当成「正常完成」并发放了好感。
> 同时按你的要求补上：**挣脱强行交流额外扣好感**、**挣扎成功不给友情之证**（这两项在 rc.36 已实现）。
> **版本 1.0.0-rc.37，候选状态，未发布正式 1.0.0。** rc.34 已作废；rc.35/36 请以本版为准。

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.37+1.21.11.jar` |
| 体积 / SHA256 | 294117 字节 / `8172BC3B85602050D59879532F44DE7B36DC6C883E9FFE274469AC2D75D874D0` |
| 自动测试 | **242 用例 / 0 失败 / 0 错误 / 32 个测试类** |

## 2. 日志核对（`C:\Users\Administrator\AppData\Roaming\.minecraft\versions\1.21.11-Fabric 0.19.5\logs\latest.log`）

我读到了这份日志（64512 字节，14:28:50 结束）。核对结果：

| GPT 的观察 | 我复核后的结论 |
|---|---|
| 版本 rc.36、14:24:43 进世界、14:28:50 正常退出 | 一致 |
| 6 次中断不奖励、15 次未跟踪实例不奖励、3 次完成结算 | 一致（未跟踪实例的行文为 `stopped but was never observed running`） |
| 没有玩家挣脱标记、没有额外扣分 | 一致：**本轮你并没有「攻击挣脱」**，所以挣脱扣分与「不发友情之证」在本轮**根本没有被触发**，不能算通过 |
| 14:26:21 拒绝扣 15 → 好感 70；14:26:40 判为完成 +10 → 好感 80 | 一致，**并且我找到了原因（见下）** |

### 2.1 误判的根因：那是「下一阶段（调试）」按键，不是自然完成

同一时间窗内还有三行被忽略的关键日志（客户端聊天）：

```
[14:26:18] 马想和你继续一次深入交流 [同意] [拒绝]
[14:26:21] request rejected by yuan_shao to 马: configuredPenalty=15 applied=-15 affinity=70 rejections=2
[14:26:21] started mating animation christmaspuffin:horsefplayeroral (instance dca0c0ad-…, forced=true)   ← 强制交流
[14:26:39] [NoN Animation] 已请求下一阶段（服务端）。   ← 连续 4 次
[14:26:40] [NoN Animation] 已请求下一阶段（服务端）。
[14:26:40] mating dca0c0ad-… (christmaspuffin:horsefplayeroral) completed … forced=true -> playerInitiated=false
[14:26:40] mating settled for yuan_shao + 马: requested=10 applied=10 affinity=80 count=2
```

- 该聊天行对应 NON 的键位 `key.needsofnature.animation.next_stage_debug`（**「下一阶段（调试）」**），
  也就是**显式推进阶段**；服务端执行的是 `ServerAnimationController.advanceStage(...)`。
- 这个入口在**最后阶段**被调用时会以 `NATURAL_END` 停止动画，而 rc.35/36 的适配器把
  「停在这个入口里」也算作**自然完成** → 于是被跳过/提前结束的强制交流拿了 +10。
- **所以回答你的问题**：从日志看，14:26:40 这次结束**不是**动画自然跑完，而是
  **紧接着 4 次「下一阶段」请求**发生的；也就是说交互是**被手动推过最后阶段而结束的**。
  这与你描述的「取消被识别为完成」在现象上一致。

**修复（rc.37）**：适配器现在把两个入口分得很清楚——

| 入口 | 含义 | 结算 |
|---|---|---|
| `maybeAdvanceStages`（tick 自动推进、最后阶段时间到、**内联**发停止事件） | 唯一的**自动完成** | `evidence=NATURAL_END` → 按规则结算 |
| `advanceStage(...)`（显式推进：调试键 / 第三方 API） | **手动推进**（可能跳过了未跑完的阶段） | `evidence=MANUAL_ADVANCE` → **不结算**，且**不算挣脱**（不扣挣脱分） |

其余停止（伤害、外部停止、演员不可用、放置失败、加入替换、世界清空）仍为 `INTERRUPTED` → 不结算。

## 3. 本轮你要求的两项（rc.36 起有效，rc.37 保持）

1. **挣脱强行交流 → 额外扣好感**：新增 `affinityLossOnEscape`（默认 **25**，0 = 关闭）；
   仅当「玩家自己挣脱」+「该次为强制交流」+「配置 > 0」时扣，**每个动画实例最多一次**，
   自愿交流中途放弃不扣。日志：`forced mating escaped by …: configuredEscapeLoss=25 applied=-25 affinity=…`。
2. **挣扎成功不给友情之证**：友情之证只在「正向变化 + 原因=完成的交流 + 好感已到上限」时发放，
   门槛提为共享判定 `AffinityService.isMatingGainReason(reason)`；挣脱走 `escape` 原因且为负向变化，
   **结构上不可能**拿到证。
3. **本轮新增可核验性**：
   - 每次发放友情之证都会打日志：`friendship token handed to <玩家> for <生物>: reason=… tokens=… (first=…)`
     —— 这样「是否多发证」不再依赖背包快照。
   - `PATH_ACCEPTED / PATH_REJECTED / ARRIVED` 这类跟随调试行改为**仅在 `debugLogging=true` 时输出**，
     本轮日志里它们刷了上百行，正是这次核对困难的原因之一。

## 4. 关于 NON 自带「挣扎」（未变，仍是上游限制）

挣扎控制只对**起始上下文为 attack** 的动画下发：
`resolveEscapeSettings` 的第一条即 `if (!isAttackSnapshot(snapshot) || …) return none();`，
而 `isAttackSnapshot = startContext().sessionData().isAttack()`；公开 API 的 `StartOptions`
**没有**任何声明该上下文的入口。强行交流目前仍以「攻击生物」作为挣脱手段
（`STOP_ON_DAMAGE`、`ignoreAttackers=false`，并发送 `escape_hint` 提示）。
建议向 NON 请求：① `StoppedContext` 暴露 `StopReason`；② `StartOptions` 增加 `escapable` 选项。

## 5. 验证分层（**不混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | 242 用例 / 0 失败（新增：手动推进不算完成、且不扣挣脱分；证据分类含 `MANUAL_ADVANCE`；友情之证门槛；挣脱扣分规则） |
| 发布包接线（字节码） | 引用 `escapeLoss`/`recordForcedEscape`/`markPlayerEscape`；不含 `recordRejection` |
| 服务端启动（NON + 你的模组组合 + 默认内容包） | `Done (0.903s)`，适配器 `completion path + manual-advance path both observed`，零注入失败 |
| 既有行为回归（服务端） | 旧宠物回填 `WAIT`/`FOLLOW` 正常 |
| **实机：跳过阶段不结算 / 攻击挣脱扣分 / 无友情之证 / 正常完成仍给证** | **需人工，未完成** |

**请这样复测（每条记录好感与是否收到友情之证）**：

1. **跳过阶段**（14:26 那次的复现）：拒绝 → 强制交流开始 → 按「下一阶段（调试）」把它推完 →
   期望日志为 `settled as NONE … evidence=MANUAL_ADVANCE`，**好感不变**（拒绝那次扣分保留）。
2. **攻击挣脱**：拒绝 → 强制交流 → **攻击生物**打断 →
   期望 `settled as NONE … escapedByPlayer=true` + `forced mating escaped by … applied=-25`，**无友情之证**。
3. **正常跑完**（不跳过、不挣脱）：期望 `completed … evidence=NATURAL_END` + `mating settled … applied=+N`，
   好感满时出现 `friendship token handed …`（正常路径的证**不能**被本批改坏）。
4. 顺带确认：强行交流里仍**没有** NON 的挣扎界面（上游限制），攻击可中断、提示正常。

客户端启动与兼容、F 键四模式、无动画动物按 F、驯马驯狗实机（rc.33 结论未在 rc.34–37 复测）仍待验收。
