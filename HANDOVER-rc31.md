# 交接说明 · 1.0.0-rc.31

> 一页式索引。本批针对 **rc.26 启动阻塞** 与 **源码/发布产物一致性**，并在发布包上跑通了
> 旧存档补齐与模式落盘；历史见 `HANDOVER-rc26.md`、`STATUS-rc26.md`。
> **版本 1.0.0-rc.31，候选状态，未发布正式 1.0.0。**
> **rc.26 及 rc.27–rc.30 均为中间/坏包，不要使用。**

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.31+1.21.11.jar` |
| 体积 / SHA256 | 276381 字节 / `17BF0CB62076BC256BF42FFDCA6A37D811C5FD70C2BC61CE45783C01108C1BE7` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |
| 版本号 | `fabric.mod.json` → `1.0.0-rc.31+1.21.11`；mixin 配置 10 条、`defaultRequire: 1` |

## 验证状态

| 类别 | 状态 |
|---|---|
| **自动测试** | **215 用例 / 0 失败 / 0 错误 / 29 个测试类** |
| **发布包注入目标** | **通过**（直接反汇编 JAR，见下） |
| **发布包服务端加载** | **通过** —— 生产命名空间、10 个 mixin 全应用、零注入失败、`Done (0.665s)!` |
| **发布包服务端行为** | **通过（仅限下列已执行场景）** —— 旧存档补齐 / 坐下→等待 / 骏马**回填**路径 / 重载幂等 / 持久化 / 配置开关双向 |
| **客户端加载** | **未复验**（需人工，见文末清单） |
| **实机交互验收** | **未完成**（F 键、驯服、模式循环等需人工） |

> **范围更正（rc.33 复核后追加）**：上表「骏马路径」指的是**旧存档补齐**（用 `Tame:1b` + 主人召唤后加载），
> **不包含正常驯马入口**。经复核，rc.31 的 `VanillaHorseTameRelationshipMixin` 挂在 `setOwner` 的 TAIL 并检查
> `isTame()`，而原版 `bondWithPlayer` **先写主人、后设驯服标志**，首次驯服必然提前返回 ——
> **正常驯马从未同步**，rc.31 因此不成立「正常驯马已通过」。该缺陷已在 **rc.33** 修复
> （锚点改为 `bondWithPlayer` 成功返回），详见 `HANDOVER-rc33.md`。rc.31 的启动修正与旧宠物补齐证据继续有效。

## rc.26 启动阻塞：根因与证据

**结论：`Entity#readCustomData` 是 `abstract`，没有 RETURN，`@At("TAIL")` 必然注入失败，整个 mixin 应用中止。**

映射文件（yarn 1.21.11+build.6）原文：

```
m  (Lfnq;)V  a  method_5749  readCustomData      <- 抽象
m  (Lfnq;)V  d  method_5651  readData            <- 具体（唯一正常 RETURN 在末尾）
```

| 证据 | 结果 |
|---|---|
| 旧发布包 `rc.26` 常量池 | 含 `method_5749`、**不含** `method_5651` → 与报告的启动失败一致 |
| 新发布包 `rc.31` 注解 | `@Inject(method=["method_5651"], at=@At("TAIL"))`；无 `require=0`、无 `HEAD` |
| 我们的类里是否还有 `method_5749` | 仅 `ProjectileStorageStampMixin` / `PersistentProjectileStorageStampMixin`，它们的宿主类 `ProjectileEntity`(class_1676)、`PersistentProjectileEntity`(class_1665) 上 `readCustomData` 是**具体**方法，TAIL 合法（服务端已实际应用）。**`method_5749` 本身不是坏名字，坏的是打在 `Entity` 的抽象声明上。** |

## 时机是否正确：不靠「方法具体」推断，直接看字节码顺序

1. **原版顺序**（反汇编 `Entity.readData`）：偏移 `583` 调 `readCustomData`，`597` 跳转，唯一 `return` 在 `633`；
   异常表 `0..597 → 600`（异常走崩溃报告，不会经过我们的钩子）。
2. **Fabric 附件顺序**（反汇编 `fabric-data-attachment-api-v1` 的 `EntityMixin`）：
   `readEntityAttachments` 注入到 **`method_5651`** 的 `@At(value="INVOKE", target="class_1297;method_5749(...)")`
   —— 即 **`readCustomData` 调用之前**。所以到我们的 TAIL 时，**Fabric 附件已恢复、原版主人/驯服标志也已恢复**。
3. **运行时实证**（发布包 + 生产命名空间 + 开 `-Dmixin.debug.export=true` 导出被改写的类）：
   导出 `net/minecraft/class_1297.class` 的 `readData` 实际字节码为

   ```
   586  handler$…$fabric-data-attachment-api-v1$readEntityAttachments
   589  invokevirtual method_5749         (原版 readCustomData：主人 + 驯服标志)
   595  handler$…$cardinal-components-entity$fromTag
   648/654/660/666  handler$…$needsofnature$…  (性别/能量/怀孕/体液)
   672  handler$zng000$noncreatureaffinity$backfillRelationshipOnLoad   <- 我们的补齐
   675  return
   ```

   **三项（Fabric 持久化挚友数据、原版主人、原版驯服状态）都在我们之前恢复完毕**，不是推测。

## 本批修了什么（在发布包上实测出来的真实缺陷）

1. **坐下宠物没有落到 WAIT（原代码是死分支）**。原判据 `data != null && getCompanionOwner() == null && isSitting(...)`：
   老存档第一次加载时 `data` 必为 `null`（正是为它写的场景），且 `CreatureAffinityData.getCompanionMode()`
   把 `null` 强制成 `FOLLOW`，`sync` 里 `getCompanionMode() == null` 永远为假 —— **两处都使「未选过模式」不可表达**。
   实测证据：rc.30 之前，召唤的坐下狼落盘为 `companion_mode: "FOLLOW"`。
   修法：新增 `chosenMode()`（可为 `null`）与纯规则 `RelationshipRules.resolveInitialMode(chosen, preferred)`，
   `sync` 增加 `initialMode` 形参；**玩家已选模式永远优先**，未选过才用状态推断（坐下→WAIT）。
   落盘只在真正选过模式时写 `companion_mode`，缺省即「没选过」，老存档不被改写。
2. **逐实体遍历里用了「会创建」的访问器**，导致世界第一 tick 就给**每只无关生物**挂上并持久化空记录
   （实测：野生狼 `{"entries": []}`）。已改为 `peekAffinityData()`：
   `RequestSystem.tickCooldowns`（每 tick 全量）、`NonCreatureAffinity.tickDailyDecay`、`AffinityCommand.countTracked`。
   修复后实测：野生狼 **无任何附件**。
3. **回归测试是真守卫**：新测试在旧代码上确实失败
   （`expected: <true> but was: <false>`，`backfillOnLoad must pass RelationshipRules.initialMode(...)`）。

## 发布包服务端实测（隔离环境，未动玩家实例、未动存档）

环境：`.codex-temp/rc27-server/`（Fabric Loader 0.19.5 + Fabric API 0.141.6 + NeedsOfNature 1.5.1 + GeckoLib 5.4.5 +
Trinkets 3.11.3 + **rc.31 发布包**），控制台经 RCON 驱动，`pause-when-empty-seconds=0`，仅作验证、可随时删除。
**全部结论来自 `data get entity … "fabric:attachments"` 与服务器日志，不是推断。**

| 场景 | 实测结果 |
|---|---|
| 发布包启动（生产命名空间） | `noncreatureaffinity 1.0.0-rc.31+1.21.11`，10 个 mixin 全应用，零注入失败，`Done` |
| 原版已驯服「坐下」的狼（有主人） | 落盘 `companion_mode: "WAIT"`，`source=load_backfill` |
| 原版已驯服「站立」的狼 | 落盘 `companion_mode: "FOLLOW"` |
| 已驯服骏马（`Tame:1b` + 主人） | 同样补齐，`FOLLOW` |
| 未驯服对照狼 | **不建立关系**，且**无任何附件** |
| 重载（重启服务端）后 | 模式/归属完整保留；**不再重复补齐**（本次运行 0 条 `load_backfill`） |
| 模拟旧存档（先关 `allowVanillaTamedCreatures` 生成无附件宠物，再开开关重载） | 开关关闭时**不认领、不写附件**；开启后重载触发 `load_backfill`，坐下者 `WAIT`、站立者 `FOLLOW` |

## 需人工完成（客户端相关，**未通过，不得写成通过**）

1. 把 `build/libs/naturalrelationship-1.0.0-rc.31+1.21.11.jar` 放进你的 Fabric 实例（**不要**用 rc.26–rc.30）。
2. **客户端启动**：能进主菜单、能进世界、日志无 mixin/注入报错（开发环境 `runClient` 与编译成功**不能**替代这一步）。
3. F 键与模式：四模式循环（跟随/漫游/等待/主动）与召回；**等待**原地自卫、**漫游**保护主人并协助攻击已有敌意目标；
   跟随/主动 **>10 格召回**，等待/漫游**不召回**。
4. 驯服入口：骨头驯狼、正常驯马（骑乘与装备不受影响）；**已确认无可用交流动画**的动物按 F 时提示
   「该动物暂无可用的深入交流动画」，不崩溃、不占用、不加好感。
5. 关系与模式跨「退出重进」保持；坐下宠物接入后模式与实际行为一致。
6. rc.25 遗留：近战持续命中续期（超过停滞阈值）、远程（骷髅）续期、双伙伴互不干扰、
   低氧等待上浮循环、`/nonaffinity stampcounts` 计数回落。

`run/config/noncreatureaffinity.json` 的 `debugLogging` 目前为 `true`（仅项目内开发目录，未触碰玩家存档）。
