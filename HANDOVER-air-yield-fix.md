> 后续状态：本修复已并入 rc.41，并补齐战斗入口低氧让行及服务端回归。最新产物和验证以 HANDOVER-rc41-companions.md 为准；下文保留上一轮记录。

# 伙伴低氧让行修复（未打包）

日期：2026-09-22。基于 rc.40，范围仅为普通伙伴移动行为。

## 问题与修改

CompanionMovementGoal 已通过 AirSupply 在低氧时让出 MOVE/JUMP，但 CompanionFollowService.tick 是另一条直接调用导航的入口，没有相同的保护。等待模式仍可能向水下锚点寻路，覆盖原版上浮路径。

- CompanionMovementGoal.mustYieldForAir 改为包内可见，复用现有判定与阈值。
- CompanionFollowService.tick 在各模式分支及召回之前检查同一判定。
- 低氧时仅删除当前伙伴的 DRIVING/PATH_FAILED 记录并跳过本轮；不调用导航 stop，不清理战斗状态，也不解除原版跟随/目标选择的管理门禁。
- 这一优先级意味着 FOLLOW/ATTACK 在低氧让行期间也暂缓召回；恢复原有空气判定后重新执行模式逻辑。

## 验证事实

离线 Gradle 定向 test 成功，生产源码编译成功：

- AirSupplyTest：9 项。
- CompanionRecallTest：2 项。
- ModeRangeRulesTest：8 项。
- RecallFlowTest：14 项。
- 合计 33 项，0 失败，0 错误。

javap 核对 CompanionFollowService.tick：字节码偏移 173 调用共享 mustYieldForAir；低氧分支仅删除两个集合记录，207 跳回实体循环，先于 WAIT、跟随和召回分支。该分支没有导航 stop 调用。

现有测试验证纯策略，不等于真实水下 AI 验收；没有新增运行时游戏测试，没有执行完整测试集，没有启动客户端。现有测试结果目录现在反映本次定向运行，不应当作完整 249 项测试报告。

没有生成新 JAR，没有修改版本号；build/libs 中原有 rc.40 包不包含本次源码修改。不要将其当成修复包分发。

## 实机复测（待完成）

1. 在独立测试世界将可呼吸空气、具有原版上浮 AI 的伙伴设为 WAIT，锚点置于水下，观察低氧时能否上浮。
2. 观察多个跟随更新周期：不能在低氧期间反复被指向水下等待点；空气恢复后应恢复等待行为。是否发生过早返回造成反复下潜，仍需实测。
3. 同场放置另一名岸上 WAIT 伙伴，确认其行为不受前者让行影响。
4. 复测 FOLLOW/ATTACK 的低氧优先级及恢复后召回；普通空气状态下距离恰为阈值不召回，超过阈值召回。
5. 水生伙伴以及岸上伙伴不应因该保护失去正常模式控制。