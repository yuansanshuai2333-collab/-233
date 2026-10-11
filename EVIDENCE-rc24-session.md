# rc.24 实机会话证据摘录（供外部核对）

> 数据来源：`run/logs/latest.log`（**未改动**）、`run/logs/debug.log`。
> 本次会话：**2026-09-22 01:21:16 启动 → 01:21:29 进入世界 → 01:29:02 正常退出**（约 8 分钟）。
> 加载版本：`noncreatureaffinity 1.0.0-rc.24+1.21.11`；`debugLogging: true`。
> 会话由**人工**操作（我无法操作客户端）。

## 一、可复现的客观事实

### 1. 模式切换链路（证据充分）

`companion interact` 的**实测形态**：

```
[NoN Affinity] companion interact tick=1488 player=<uuid> target=<uuid> hand=MAIN_HAND sneaking=true hitResult=true modeBefore=FOLLOW
[NoN Affinity] companion interact applied tick=1488 target=<uuid> modeAfter=WANDER
[NoN Affinity] companion interact tick=1488 player=<uuid> target=<uuid> hand=MAIN_HAND sneaking=true hitResult=false modeBefore=WANDER
[NoN Affinity] companion interact SUPPRESSED duplicate tick=1488 target=<uuid> hitResult=false totalSuppressed=1
```

- **潜行生效**：`sneaking=true`；普通右键不切换（需潜行才进入该分支）。
- **主手空手生效**：`hand=MAIN_HAND`。
- **同一次物理点击的第二次回调被去重**：`SUPPRESSED duplicate`，全程 18 次。
- **切换顺序为 跟随→漫游→等待→主动→跟随**（实测时序）：

```
01:26:06 -> WANDER   01:26:07 -> WAIT   01:26:07 -> ATTACK   01:26:08 -> FOLLOW
01:26:35 -> WANDER   01:26:36 -> WAIT   01:26:41 -> ATTACK   01:26:42 -> FOLLOW
01:26:46 -> WANDER   01:26:48 -> WAIT   01:27:02 -> ATTACK   01:27:03 -> FOLLOW
01:27:06 -> WANDER   01:27:19 -> WAIT   01:27:20 -> ATTACK   01:27:20 -> FOLLOW
01:27:34 -> WANDER   01:27:40 -> WAIT
```

计数：`WANDER 5 / WAIT 5 / ATTACK 4 / FOLLOW 4`（共 18 次成功切换），与「每次点击一次切换」一致。

### 2. 完成交战全部由**合法原因**结束，无一次停滞

```
[01:25:45] [NoN Affinity] 狼 engagement with 僵尸 ended: TARGET_GONE
[01:26:19] [NoN Affinity] 狼 engagement with 僵尸 ended: TARGET_GONE
[01:27:13] [NoN Affinity] 狼 engagement with 僵尸 ended: TARGET_GONE
[01:28:32] [NoN Affinity] 骷髅 engagement with 僵尸 ended: OUT_OF_RANGE
```

- **`NO_PROGRESS` 行数 = 0**；`stalled` 唯一命中是 NeedsOfNature 的无关行（`Trinkets is not installed`）。
- 即：**没有任何一次战斗因停滞被误判退出**。

### 3. 跟随移动链路

```
[NoN Affinity] PATH_ACCEPTED for 狼 ... mode=FOLLOW ... dist=14.12 start=6.0 stop=3.0
[NoN Affinity] PATH_REJECTED for 狼 ... mode=FOLLOW ... dist=13.74
[NoN Affinity] PATH_REJECTED for 狼 ... mode=FOLLOW ... dist=3.94 navIdle=true
[NoN Affinity] ARRIVED at owner for 狼 ... dist=2.89 / 2.87 / 2.87
```

- 到主人 **14.12 格**时仍在 FOLLOW 模式**寻路**（`PATH_ACCEPTED`），**没有召回日志**。
- 到达阈值 3.0 内正常 `ARRIVED`。

### 4. 其它

- `mating recorded`：狼与骷髅各 5 次，好感 0→100，并在成为朋友时自动绑定。
- 本模组 **零 ERROR**（唯二异常是 Mojang 会话 401 与 NeedsOfNature 缺 `buttplug4j`，与本模组无关）。

## 二、**无法**从本会话得到结论的项（不可当作通过）

| 验收项 | 为什么不足 |
|---|---|
| **狼持续有效命中超过停滞阈值 → 不因 NO_PROGRESS 退出** | 战斗确实未因停滞退出，但**本次日志没有任何命中记录**，「是命中刷新了进展」与「一直在接近而未触发停滞」**无法区分**。 |
| **无命中且无接近进展 → 正常退出** | `NO_PROGRESS` 行数为 0，即**该场景未被触发过**，无证据。 |
| **等待模式：停止追击 / 自己受击只原地自卫** | WAIT 窗口极短（01:26:07→01:26:08 等，1–2 秒），**不足以观察**；无 `mode=WAIT` 路径行、无自卫记录。 |
| **FOLLOW/ATTACK 超过 10 格安全召回** | 无任何 `Recalled`／召回相关日志；14.12 格时仍在 FOLLOW 寻路。**召回未被触发**。 |
| **WAIT/WANDER 不召回** | 同上，未触发，无对照。 |
| **骷髅持续有效命中可刷新进展** | 唯一骷髅战斗以 `OUT_OF_RANGE` 结束，**可能正是跟随范围召回**，恰好说明**未发生远程命中续期**；无命中记录可判读。 |
| **低氧等待、两伙伴互不干扰、世界重载、投射物清理计数** | 未执行（本次未记录 `/nonaffinity stampcounts`，日志中 `stamps` 命中数 = 0）。 |

## 三、本次复盘发现的两个真实缺陷（已修，待复验）

1. **`scan state` 每帧刷屏**：一次 8 分钟会话产生 **13,321 行**同内容日志，淹没了行为证据。
   - 位置：`AffinityDetectorState.isEnabled(UUID)`（渲染路径每帧调用）。
   - 修复：读路径改为**静默**；仅在 `toggle()` 真正**状态变化**时记录一行。
2. **缺少命中遥测**：`debugLogging` 下也**没有任何「命中」日志**，导致「命中刷新进展」无法从实机取证。
   - 修复：`onCompanionHit` 与 `onCompanionProjectileHit` 各加**每条命中一行**的 debug 输出
     （只在**命中**时记录，非逐帧）：
     `... landed a melee hit on <目标> -> progress renewed=<bool>`
     `... landed a projectile hit on <目标> -> progress renewed=<bool>`

## 四、复现方式

```
gradle runClient -PdevWorld=devworld        # 不要叠加 --args
run/config/noncreatureaffinity.json : "debugLogging": true
```

上述两项修复在 **rc.25**；rc.25 的命中遥测与刷屏修复**尚未在游戏中复验**（本文件记录的是 rc.24 会话）。
