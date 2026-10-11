# 1.0.0-rc.23 状态摘要

> 更新：2026-09-22。**版本 1.0.0-rc.23，候选状态，未发布正式 1.0.0。**
> 历史：`STATUS-rc22.md`、`STATUS-rc21.md`、`STATUS-rc20.md`、`STATUS-rc19.md`、`STATUS-rc18.md`、`STATUS-rc17.md`、`STATUS-rc16.md`、`STATUS-rc15.md`、`STATUS-rc14.md`、`STATUS-rc13.md` …
> rc.22 的**实例归属与清理修复保留**，本批**不扩大生命周期重构**，转入以游戏结果为依据的验收。

## 一、启动方式修正（你定位的原因成立）

**原因**：`build.gradle` 的 `client` 配置**硬编码** `--quickPlaySingleplayer new_world`，
而 `run/saves` 下实际是 `devworld`；在命令行再叠加 `--args="--quickPlaySingleplayer devworld"`
会产生**两个冲突值**。

**已改为按你的要求**：

| `-PdevWorld` | `run/saves/<name>/level.dat` | 行为 |
|---|---|---|
| 未提供 | — | **进入主菜单**（不加快速进入参数） |
| 提供 | 存在 | 快速进入该世界，并打印 `quick-playing into existing world '<name>'` |
| 提供 | **不存在** | **构建失败并给出清楚提示**（列出可用世界），不再静默进主菜单、也不新建同名世界 |

实测三个分支：

```
场景1 未提供          → BUILD SUCCESSFUL（不打印 quick-playing）
场景2 -PdevWorld=does_not_exist → FAILURE:
     [NoN Affinity] -PdevWorld=does_not_exist was given, but ...\run\saves\does_not_exist\level.dat
     does not exist. Available worlds: devworld, 新的世界. Pass an existing world name, or omit
     -PdevWorld to open the main menu.
场景3 -PdevWorld=devworld      → [NoN Affinity] quick-playing into existing world 'devworld'
                                 BUILD SUCCESSFUL
```

检查**只读**：**不删除、不重命名、不覆盖任何现有存档**。

**启动命令**（沿用已配置的 JDK、Gradle 与离线缓存，**不再叠加 `--args`**）：

```
gradle runClient -PdevWorld=devworld
```

## 二、文档修正

`setRemoved` 的「每实例一次」已改为：**清理应可重复调用且保持幂等**；
并明确 **`final` 只表示子类不能覆写，不代表方法只执行一次**。
`releaseInstance` 的注释与 `ProjectileRemovalStampMixin` 的类注释同步改正。

新增回归：

- `cleanupIsIdempotent`：读取 → 清理 → **重复清理 3 次**，存储与 uuid 索引**始终无变化且不抛异常**；
  传 `null` 也被容忍。
- `repeatedCleanupIsScopedToItsInstance`：重复清理一个实例**不影响另一个实例**。

---

## 三、验收记录（按你要求的字段）

> 每项：**版本 / 场景 / 预期 / 实际 / 时间 / 日志**。

### 1. 本版本客户端加载 —— **通过**

| 项 | 内容 |
|---|---|
| 版本 | **`noncreatureaffinity 1.0.0-rc.22+1.21.11`**（日志确认，非沿用 rc.15） |
| 场景 | `gradle runClient -PdevWorld=devworld`，进入 `devworld` |
| 预期 | 加载 rc.22、进入测试世界、无异常 |
| 实际 | **集成服务器启动、玩家登录、进入世界**；**本模组零 ERROR**；8 个 mixin 全部应用；**会话约 9 分钟后正常退出并保存世界** |
| 时间 | 2026-09-22 01:10:25 登录 → **01:19:26 正常退出**（启动 01:10:23） |
| 日志 | `run/logs/latest.log`、`run/logs/debug.log` |

关键日志行：

```
[01:10:23] [Server thread/INFO] (Minecraft) Starting integrated minecraft server version 1.21.11
[01:10:25] [Server thread/INFO] (Minecraft) Player637[local:E:601cd6e3] logged in with entity id 114 at
           (201.77, 73.73, -349.63)
[01:10:25] [Server thread/INFO] (Minecraft) Player637加入了游戏
加载模组列表含: - noncreatureaffinity 1.0.0-rc.22+1.21.11
本模组 ERROR: none
```

**会话正常结束（比「被终止」更强的证据）**：不是被外部终止，而是**正常退出** ——

```
[01:19:26] Player637退出了游戏
[01:19:26] Stopping singleplayer server as player logged out
[01:19:26] Stopping server
[01:19:27] Saving chunks for level 'ServerLevel[新的世界]'/minecraft:overworld
[01:19:28] ... the_end / the_nether   -> 全部区块已保存
```

**本次会话未产生行为数据（如实说明）**：`run/config/noncreatureaffinity.json` 中
**`debugLogging` 为 `false`**（默认值），日志里只有初始化行；
`companion interact`、`engagement`、`PATH_`、`ARRIVED`、`recall`、`scanHostility` 等
关键字的命中数**均为 0**。
**下次实机前请先把它设为 `true`**，否则第二节的验收项无法从日志取证。

**前两次尝试失败的原因（诚实记录）**：
第一次我用了 `--args="--quickPlaySingleplayer devworld"`，**与硬编码的 `new_world` 冲突**；
第二次虽按 `-PdevWorld` 方式启动，但进程在模组初始化后被**外部终止**
（无 `hs_err`、无 crash-report、无 Java 异常，日志停在 `Animation engine initialized`）。
第三次干净复跑**成功进入世界**。

> 客户端在**本批文档修正之前**按 rc.22 启动，因此上表版本为 rc.22；
> **rc.23 只改了注释、测试与 `build.gradle`**，行为代码未变。
> 后续实机请用 `gradle runClient -PdevWorld=devworld`，会加载 rc.23。

### 2–5. 其余验收项：**未完成，需人工操作**

**不把「等待人工测试」写成通过。** 以下为可直接执行的步骤与判据。

**统一准备**

- 命令：`gradle runClient -PdevWorld=devworld`（**不要**再叠加 `--args`）。
- 若要把 rc.23 放进**测试用**实例 `mods/`：需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib；
  **不要**使用正式存档。
- `run/config/noncreatureaffinity.json` 设 `"debugLogging": true`。
- 记录：**版本 / 场景 / 预期 / 实际 / 时间 / 日志**；本模组行以 `[NoN Affinity]` 开头。

#### 2. 狼近战

| 场景 | 预期 | 实际 |
|---|---|---|
| 目标存活、距离合法、**持续有效命中超过** `companionEngageStallTicks`（默认 200 刻） | **不因 `NO_PROGRESS` 退出** | 待记 |
| **无命中且无接近进展** | **正常退出**（预期一次 `NO_PROGRESS`） | 待记 |
| 战斗中**切换等待** | **停止追击**，不再替主人参战 | 待记 |
| 伙伴**自己受击**（等待模式） | **只原地自卫**，不追击 | 待记 |

> 请同时贴回**结束原因行**。目标死亡 / 模式切换 / 召回 / 受保护 / 跨维度导致的
> `engagement ended` **属正常**，**不要求日志里没有它**。

#### 3. 骷髅远程

| 场景 | 预期 |
|---|---|
| 持续有效命中 | **能刷新进展**（不因命中被判停滞） |
| 交战结束后**旧箭**命中 | **不能给新交战续期** |

#### 4. 四模式与召回

| 场景 | 预期 |
|---|---|
| 主人攻击 | 跟随/主动协助攻击该目标 |
| 主人受击 | 跟随/主动攻击攻击者 |
| 主动模式发现敌意 | 主动接战（无需先被打） |
| **漫游** | **不被拉回**，用自身 AI；仍保护主人、协助攻击已有敌意的目标 |
| **等待** | 原地；**不替主人参战**；允许原地自卫 |
| 跟随/主动 > **10 格** | **安全召回**（9.9 与恰好 10 格**不**召回） |
| 等待/漫游 15 格 | **不召回** |
| 两伙伴（同一主人） | 一个召回/停滞**不影响**另一个 |

#### 5. 低氧与重载

| 场景 | 预期 |
|---|---|
| 等待伙伴入水下潜 | 能**上浮**且**不追敌、不跟随** |
| 恢复后 | 回到等待；**不反复返回水下锚点**形成「下潜—低氧—上浮」循环 |
| **投射物区块卸载 / 重载 / 跨维度** | 无异常；**记录不持续累积**（见下） |

**累积检查方法**：`debugLogging: true` 下反复触发卸载/重载/跨维度，
观察内存与日志是否出现异常；如需精确计数，可在后续补一个只读诊断日志
（**本批不加**，避免扩大改动）。

---

## 四、本批源码改动（范围很小）

| 文件 | 改动 |
|---|---|
| `build.gradle` | `devWorld` 三分支处理（见第一节） |
| `ProjectileStamps.java` | 注释：幂等而非「每实例一次」，`final` ≠ 只执行一次 |
| `ProjectileRemovalStampMixin.java` | 同上 |
| `ProjectileOwnerLifecycleTest.java` | 新增 2 项幂等回归 |

**保留未动**：rc.22 实例归属与清理修复、rc.19 交战终态保护、rc.20 读取失效方案、
rc.17 整方法近战包装（`@WrapMethod`）。

---

## 验证分层（不得互相替代）

| 类别 | 状态 |
|---|---|
| **自动测试** | **203 用例 / 0 失败 / 0 错误 / 28 个测试类** |
| **服务器加载** | **通过**（8 个 mixin） |
| **客户端加载** | **通过**（rc.22 实测进入世界；rc.23 只改注释/测试/build.gradle） |
| **实际行为验收** | **未完成**（第 2–5 项待人工） |

## 待实机验证的声明（不得当作已完成）

1. 第 2–5 项行为验收**全部未做**。
2. **真实移除调用链**（区块卸载 / 跨维度）—— 反汇编结论，**实机未执行**。
3. **重载后终态** —— 规则测试通过，**实机未复现**。
4. **远程实际命中续期** —— 未验证。
5. **近战真实异常场景** —— 未在实机构造。

## 剩余限制

1. **实际行为验收：未完成**。
2. **重载前射出的箭不再贡献进展**（有意取舍）。
3. `setOwner` 以外的发射路径（三叉戟投掷形态、药水等）：不误续期，但也不续期。
4. **战斗能力是「代码白名单」，不是「行为已验证」**。
5. **低氧让路后由谁接管移动**只能实机确认。
6. 原地自卫命中判定、NeedsOfNature 动画占用、原版 owner-assist/报复目标重置：未实机确认。

## 产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.23+1.21.11.jar` |
| 体积 / SHA256 | 261252 字节 / `0ED4FAFE6BCD4FBA83E0BC72DF7D700CE0CECA1AE23B12BBE71A5BB66DC0948E` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 正式版前置

**关键行为验收通过、已发现的问题修复完成前，保持候选版本，不设置正式 1.0.0。** 当前：**不满足**。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未删除、重命名或覆盖任何存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`新的世界`）。
