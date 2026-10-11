# 交接说明 · 1.0.0-rc.35

> 本批：**重做 rc.34 的结算修复**。rc.34 因「用时间估计替代完成证据」未通过审查，**已作废**。
> **版本 1.0.0-rc.35，候选状态，未发布正式 1.0.0。**
> rc.33 的驯马入口修复继续有效；rc.34 不要使用。

## 1. 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.35+1.21.11.jar` |
| 体积 / SHA256 | 292667 字节 / `4C54491067AF5C0DC741C5EE3A91F3B79D50CE54719439D39A7C440D45B0E5A1` |
| mixin 配置 | `noncreatureaffinity.mixins.json`（10，`required: true`）+ `noncreatureaffinity.non.mixins.json`（1，`required: false`，可选集成） |
| 自动测试 | **237 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| `fabric.mod.json` | `1.0.0-rc.35+1.21.11`；`fabric-api>=0.141.6` |

## 2. 关于停止原因：查到了什么（按审查要求逐项）

| 问题 | 实读结论 |
|---|---|
| 公开 API 是否给出停止原因？ | **没有。** `NonAnimationEvents$StoppedContext` 只有 `world / instanceId / animationId / actorUuids / actorKeys / stages`。事件在**两条不同代码路径**上被触发。 |
| NON 内部是否有原因？ | **有。** `ServerAnimationController$StopReason`：`NATURAL_END`、`EXTERNAL_STOP`、`DAMAGE_STOP`、`JOIN_REPLACE`、`ACTOR_UNAVAILABLE`、`PLACEMENT_FAILED`、`BLOCK_REQUIREMENT_INVALIDATED`、`STOP_ALL`。 |
| 能否直接读到？ | **不能可靠读取。** 该枚举是**包私有**，只作为参数传给 `stopInstance(ServerWorld, UUID, boolean, StopReason)`。以完整描述符注入该方法被实测拒绝：Mixin 报 `Scanned 0 target(s)`（映射形式与中间名形式各试一次，`require=1` 强制暴露错误），而同一个 mixin 类接受了下述两个注入。 |
| 那完成判定靠什么？ | **靠产生自然结束的两条代码路径本身**（下节），不靠时间，也不假装读到了原因。 |
| 是否需要上游公开？ | 建议向 NON 请求在 `StoppedContext` 增加 stop reason 字段；在此之前本模组按路径判定，且**读不到就不发奖励**。 |

## 3. rc.34 被否决的做法（已删除，不再存在）

- `MatingSettlementRules.expectedStageTicks`、`UNMEASURED_LOOPING_STAGE_MIN_TICKS`、2 tick 容差、
  阶段时长/上一阶段窗口 —— **全部删除**；测试断言这些名字与 `Facts` 里任何时间字段都不存在，
  防止时间猜测再次混入（`MatingSettlementTest` 用例 1b、`MatingSettlementWiringTest`）。

## 4. 现在的判定方式（路径证据，非时间）

NON 只可能从两处**自然结束**动画，两处都已被观察，且**在真实运行时的被改写字节码中核对过**：

| 路径 | 观察方式 | 运行时证据（生产命名空间，实际导出类） |
|---|---|---|
| ① tick 驱动：最后阶段时长走完，在 `maybeAdvanceStages` 内**内联**发停止事件（不带原因） | 锚定只有该分支才调用的 `scheduleMobMountRestores` | `maybeAdvanceStages` 偏移 157 调用 `handler$…$recordNaturalEnd` |
| ② 公开 API：`advanceStage(ServerWorld, UUID)` 在最后阶段以 `NATURAL_END` 停止 | HEAD 标记「正在其中」，各 RETURN 清除 | `advanceStage` 偏移 3 调 `beforeAdvanceStage`，偏移 35/81/112/132 调 `afterAdvanceStage` |

其余所有停止（伤害、外部停止、演员不可用、放置失败、加入替换、世界清空）都走带原因的漏斗，
因此被判为**中断**。若将来 NON 从别处完成动画，结果是**拒绝发奖**（安全方向），而不是错发。

**落地规则**（`MatingSettlementRules.decide`）：

1. 已结算过 / 玩家挣脱 → 不发；
2. 观察到自然结束 → 结算；**加成仅当「由本模组启动」且「非强制」**；
3. 观察到中断 → 不发；
4. **适配器不可用**（未装 NON / 版本未验证 / 成员缺失）→ 证据为 `UNKNOWN` → **不发，并在启动日志明确说明**。

**没有惩罚分支**：拒绝扣分仍只属拒绝路径 `AffinityService.recordRejection`（唯一扣分点，配置为 0 则不扣），
挣脱不会二次扣分——已用字节码断言锁死（`NonIntegration` 不含 `recordRejection` 引用）。

## 5. 可选集成的隔离与版本限制

- 独立配置文件 `noncreatureaffinity.non.mixins.json`：`required: false`。
- `NonIntegrationMixinPlugin`：
  - **不加载任何游戏类**（实测教训：早期 `Class.forName` 触发 `ServerAnimationController.<clinit>` 直接崩服；
    用 `getParameterTypes()` 又会把 `Entity` 提前加载，破坏 cardinal-components 的 mixin）。
  - 未装 NON → 整份配置关闭，状态记为「未安装」。
  - 版本不在验证范围（`1.5.1*`）→ 关闭，并在日志写明「不会自动发放交流好感」的原因。
  - `preApply` 从 **ASM 类树**核对 `maybeAdvanceStages` 与 `advanceStage` 是否都在；缺失则以 **ERROR** 报出，
    并把结论写入状态供启动日志打印。
- 注入使用 `require = 0`：这是**可选**集成，NON 改内部方法不应让玩家崩服；成员缺失由上面的核对**大声报告**，
  后果是安全方向（不发奖），不是静默错发。

## 6. 新增/可判读的日志（含 instanceId）

| 时机 | 日志 |
|---|---|
| 启动 | `natural-completion adapter target … -> applying`、`… adapter verified against …`、`mating settlement stop-reason status: …` |
| 拒绝请求 | `request rejected by … to …: configuredPenalty=15 applied=-15 affinity=… rejections=…` |
| 挣脱标记 | `player … broke out of N mating animation(s) with …; no affinity for those` |
| 判定为不发 | `mating <instanceId> (<animationId>) settled as NONE: interrupted=… stopReason=… evidence=NATURAL_END/INTERRUPTED/UNKNOWN startedByUs=… forced=…` |
| 判定为完成 | `mating <instanceId> (<animationId>) completed with stopReason=… startedByUs=… forced=… -> playerInitiated=…` 紧跟 `mating settled for …: playerInitiated=… requested=+N applied=+M affinity=… count=…` |
| 未知实例 / 重复停止 | `mating <id> stopped but was never observed running; no affinity granted` / `ignoring duplicate stop for mating <id> (already handled)` |

## 7. 已验证 vs 需人工实测（**不混写**）

**已自动/服务端验证（本轮实际执行）**

| 项 | 结果 |
|---|---|
| 单元测试 | 237 用例 / 0 失败（含审查点名的反例：任何时刻挣脱都不发；每次只结算一次；强制启动不给主动加成；未知停止不发） |
| 发布包接线（字节码） | 引用 `MatingSettlementRules.decide`、`MatingStopEvidence.observedKind`、`MatingSessions.take`；**无** `recordRejection`、**无** `expectedStageTicks` |
| 适配器运行时 | 生产命名空间导出类中，两条自然结束路径的 handler 调用点已逐条核对（见 §4 表） |
| 服务端启动（NON + 你的模组组合 + 默认内容包 76 定义） | `Done (0.801s)`，零注入失败，适配器状态为 `enabled and verified` |
| 非交流路径回归 | 旧宠物回填 坐下→`WAIT`、`FOLLOW` 正常 |

**注意：自动测试不能证明「真实停止原因与事件时序」**。上面的路径注入是实测的，
但**正常完成 / 早期挣脱 / 较晚挣脱**在本模组判定下分别得到什么结果，必须由你在客户端实测确认。

**需你重点复测（三种，请分别记录好感变化）**

1. **早期挣脱**：生物主动发起（或强制发起）后立刻挣脱 → 期望：只有拒绝那次扣分（若发生过拒绝），
   好感**不再上升**；日志应出现 `settled as NONE … evidence=INTERRUPTED`（或 `interrupted=true`）。
2. **较晚挣脱**：等到动画已进行很久再挣脱 → 期望同上；**这是 rc.34 会误发、rc.35 必须不发的关键用例**。
3. **正常自愿完成**：不挣脱、走完整段 → 期望：出现 `completed … -> playerInitiated=…` 与
   `settled … applied=+M`，且**同一 instanceId 只出现一次**。
   若此用例被判为 `NONE`，请把该行日志（含 `evidence=`、`stopReason=`）发我，我按实测证据修正，
   不再用量时间的方式放宽。

**其他仍未验证**：客户端启动与兼容（rc.35 未回归）、F 键四模式行为、无动画动物按 F、
骨头驯狼/驯马实机、rc.25 遗留项。另：**rc.33 的「驯马、驯狗实测暂未发现问题」是上一版结论，rc.34/rc.35 均未复测**。
