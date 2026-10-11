# 交接说明 · 1.0.0-rc.15

> 本文件是一页式交接索引。详细内容在 `STATUS-rc15.md`，历史在 `STATUS-rc14.md`、`STATUS-rc13.md` …
> **版本 1.0.0-rc.15，候选状态，未发布正式 1.0.0。**

## 当前产物

| 项 | 值 |
|---|---|
| 普通包 | `build/libs/naturalrelationship-1.0.0-rc.15+1.21.11.jar` |
| 体积 | 249076 字节 |
| SHA256 | `DE5D36030CAE741F0679EC27C8385B678D7BCDAE59201C77C0D20CF806A9E35A` |
| 构建 | `jdk-21.0.2` + `gradle-9.2.0`，`--offline`，`GRADLE_USER_HOME` = 项目内 `.codex-gradle-home` |

## 四类验证状态（不得互相替代）

| 类别 | 状态 | 证据位置 |
|---|---|---|
| **自动测试** | **165 用例 / 0 失败 / 0 错误** | `gradle test`，`build/test-results` |
| **服务器加载** | **通过** | `STATUS-rc15.md` → 本次运行记录 |
| **客户端加载** | **通过**（首次） | 同上（进入 devworld、玩家登录） |
| **实际行为验收** | **未做** | 步骤见 `STATUS-rc15.md` → C 节 |

## 本批（rc.15）改了什么

1. **拆开呼吸让路与战斗控制** —— `AirSupply.isManaged(...)`（**无空气参数**）驱动闸门；
   `mayControlMovement(...)` 决定本 Goal 是否运行。低氧只释放移动，WAIT 仍不追敌、WANDER 仍不跟随主人。
2. **攻击标记生命周期** —— 删除「RETURN 在抛异常时也执行」的错误描述；
   改用「非当前刻的戳无效」兜底（不用 try/finally、**不吞异常**）；
   修 `clearAll()` 漏清令牌表。
3. **发现并修掉严重缺陷：mixin 打在错误类上** —— 原打在 `LivingEntity`（空壳），
   真身在 `MobEntity`。此前**对任何生物都从未打过戳**，而所有单元测试仍全过。
4. **验收说明修正** —— 模式切换写明「潜行 + 主手空手右键」与真实切换顺序；
   「白名单」与「行为已验证」分开；远程不续期列为**阻塞项**。

## 未完成 / 阻塞项

- **实际行为验收：全部**（四模式、召回、停滞后退出、低氧呼吸、两伙伴、骷髅远程）。
- **远程命中不续期是阻塞项**：若正式版继续支持骷髅等远程伙伴，
  **必须在 1.0.0 之前**为投射物建立可校验的发射戳（在投射物上记录发射时**代次与目标**，命中时校验）。
  **不得**仅凭「列为已知限制」就算完成行为目标。
- **低氧让路后由谁接管移动**只能实机确认：字节码已确认 `GoalSelector` 的占用与替换规则
  （`PrioritizedGoal.canBeReplacedBy` 在优先级相等时返回 false），
  但「让路后 `BreatheAirGoal` / `SwimGoal` 是否真的先于其他 MOVE 目标启动」需实测。

## 如何自行做行为验收

```
gradle runClient --args="--quickPlaySingleplayer devworld"
```

或把 rc.15 的 jar 放进**测试用**实例的 `mods/`（需前置 `needsofnature-1.5.11.21.11.jar` 与 GeckoLib）。

- **不要**使用正式存档；`devworld` 是项目内的开发用世界。
- 在 `config/noncreatureaffinity.json` 设 `"debugLogging": true`，
  日志里本模组的行以 `[NoN Affinity]` 开头。
- **模式切换：潜行（蹲下）＋ 主手空手右键**，顺序 **跟随 → 漫游 → 等待 → 主动 → 跟随**。
- 完整的逐步判据表见 `STATUS-rc15.md` 的「C. 人工行为测试步骤」。

## 边界

- 改动全部在 `I:\DSH\work\noncreatureaffinity` 内。
- **未安装到游戏 mods 目录**；**未修改任何玩家存档**。
- 加载验收会在项目 `run/` 下自动保存**开发用世界**（`devworld`、`world`）。
