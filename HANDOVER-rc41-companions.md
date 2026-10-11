# rc.41 伙伴四模式交接

日期：2026-09-22。用户确认的范围：现有跟随、漫游、等待、主动四种普通伙伴模式。

## 使用与行为

主人潜行（默认 Shift）+ 主手空手右键伙伴，按 **跟随 → 漫游 → 等待 → 主动 → 跟随** 切换。

| 模式 | 移动 | 战斗 |
|---|---|---|
| 跟随 | 默认超过 6 格开始靠近，至 3 格停止；严格超过 10 格且有安全落点才召回 | 协助主人的合法攻击、保护主人及自身 |
| 漫游 | 保留生物原有漫游，不跟随、不召回、不限制在旧等待点附近 | 主人受击时可援助；主人攻击普通中立目标时不参战，攻击已有敌意者时可协助 |
| 等待 | 留在下令位置附近；默认等待半径 4 格；不随主人召回 | 仅对身边攻击者原地反击，不追击离开攻击范围的目标 |
| 主动 | 无战斗时跟随，超距召回规则与跟随一致 | 在跟随助战基础上，响应实际针对主人的敌意，不因生物种类就主动攻击 |

主人明确下达移动模式命令会解除宠物的坐下指令和坐姿。自动绑定、存档加载不会强行唤起坐着的宠物；普通右键仍保留原版交互。其他玩家不能切换或唤起不属于自己的宠物。

低氧期间，普通伙伴移动和本模组的战斗控制让行；其原有上浮行为负责移动。跟随/主动模式的召回也暂缓到让行结束。原版跟随与目标选择的管理门禁仍保留。

## 本次生产代码修改

- `CompanionFollowService.tick`：所有模式及召回分支之前检查共同的低氧判定，只删除当前伙伴的移动记录，不停止上浮导航。这包含上一轮已完成的源码修复。
- `CompanionMovementGoal.mustYieldForAir`：共享同一实体空气判定，避免两条入口条件不一致。
- `CompanionCombatController.tick`：低氧时结束本模组的交战，避免 WAIT 原地反击反复停止导航；退出原地反击也不会停止低氧时的上浮路径。
- `CompanionCombatController.onModeCommand`：清理旧移动记录，并在低氧时保留导航。
- `CompanionService.toggle`：统一执行模式命令；只有 WAIT 保存等待点，其他模式清除旧点；获得授权的移动命令同时清除原版坐下状态和坐姿。
- `CompanionService.clearBond`：清除绑定时也清除旧等待点。
- 模式与交互注释同步实际四模式行为；版本从 rc.40 更新到 rc.41。

本轮未修改其他内容子系统。

## 自动化验证

单元测试：**118 项 / 20 类，0 失败、0 错误**。这是定向相关测试集，不是完整历史 249 项测试集。其中策略规范测试内部另有 70 个断言，计为一个测试方法。

实际服务端验证：**27 项通过，0 失败**。最终日志：`.codex-temp/companion-rc41-regression/console.log`，受测包哈希记录在同目录的 `artifact.sha256`。脚本正常退出（退出码 0），服务器已自行停止。

验证工具新增于 `src/companionTest/`，通过独立 Gradle 初始化脚本编译和重映射为开发探针，不进入发布 JAR。探针仅在显式设置 `-Dnonaffinity.companionProbe=true` 时运行，会修改测试世界并在结束后停服，因此只能通过隔离脚本运行。

测试使用真正的 Fabric 专用服务器、两个内存测试玩家、狼、骷髅、目标生物和实体导航。第一组低氧路径检查注入狼的水下判定；另一个 400 tick 水池试验使用真实水方块和原版水下检测，二者不可混淆。

覆盖：四模式切换、坐姿解除、跨主人拒绝、10 格边界、安全落点及危险落点拒绝、等待/漫游不召回、实际寻路和行走、按模式助战、等待不追击、实际主人敌意、双伙伴隔离、旧事件不重放、友方保护、清除绑定、四模式 NBT 往返、低氧路径不被反击覆盖、持续近战、持续远程、水中上浮。

持续战斗分别运行 260 tick；测试将事件有效期设为 20 tick、停滞阈值设为 120 tick，以核对原事件过期后真实命中仍能续期。最后一轮使用默认跟随更新间隔 10 tick。远程场景使用大型静止掠夺兽作为靶，围墙保持在召回范围内，移开其他狼及阻挡箭道的生物，采用原版困难难度、夜间和固定骷髅随机种子；原版射击、箭碰撞、伤害和本模组归因均照常运行。每次目标真正掉血时立即核对进展时钟；另外要求在初始停滞窗口之后仍有真实命中。测试不再把末尾因连续射偏而正常结束的战斗误报为续期失败。

骷髅原版侧移后退可能越过主人 10 格，此时召回并结束战斗是现有规则的正常结果。围墙试验只验证持续命中续期，不能据此宣称任意室外站位都不会结束战斗。

## 重现

在 PowerShell 中：

```powershell
$projectRoot = 'I:\DSH\work\noncreatureaffinity'
$env:JAVA_HOME = 'I:\DSH\work\tools\jdk21\jdk-21.0.2'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.codex-gradle-home'
& 'I:\DSH\work\tools\gradle-9.2.0\bin\gradle.bat' --offline --console=plain `
  -p $projectRoot -I "$projectRoot\tools\companion-tests.init.gradle" `
  remapJar remapCompanionProbe test `
  '--tests=com.nonid.affinity.*Companion*' '--tests=com.nonid.affinity.*AirSupplyTest' `
  '--tests=com.nonid.affinity.*Recall*' '--tests=com.nonid.affinity.*ModeRangeRulesTest' `
  '--tests=com.nonid.affinity.*Engagement*' '--tests=com.nonid.affinity.*ConfigLanguageAuditTest'
if ($LASTEXITCODE -ne 0) { throw 'Build/tests failed' }
& "$projectRoot\tools\Test-CompanionModes.ps1"
```

隔离脚本从既有 `.codex-temp/rc32-envA` 复制服务端运行库与 Fabric API，复用该测试环境已接受的 EULA，在全新目录创建世界，仅监听 `127.0.0.1:25641`，禁用 RCON。已有测试目录或端口占用会报错，不复用玩家世界。每轮目录记录 `artifact.sha256`、`console.log`、`console.err`。脚本在测试失败、启动失败或超时时返回失败。

## 验证边界与客户端复测

- 未启动客户端，未验证实际 Shift+右键输入、动作栏显示或探测器界面。
- NBT 序列化往返已经验证；用户实例退出重进、真实区块卸载重载尚未手测。
- 测试组合是 Fabric API + 本附属模组；未验证用户完整模组组合。
- 实际移动/近战参考生物为狼，远程为持弓骷髅。马类仍为移动支持，不因此获得攻击 AI；其他模组生物不能一概认为已验收。
- 水池试验持续 400 tick，不能代替任意地形或多小时稳定性测试。
- 未覆盖多小时投射物记录回落曲线、跨维度跟随（本阶段不提供跨维度召回）。

客户端建议依次检查：潜行空手切换一整轮；原先坐下的宠物切移动模式；跟随/主动超 10 格召回；漫游/等待离主人远去不召回；等待点被推离后的返回；狼的原地反击；两位真实玩家分别控制伙伴；保存退出重进。

## 安装包

`build/libs/naturalrelationship-1.0.0-rc.41+1.21.11.jar`

SHA256：`20BD89A238125BE5A59E8FC83C661BD815E806EB9F15737251B9BE2C8B48857A`

299907 字节。包内版本已核对；不含开发探针。`build -x test` 成功，相关测试已按上述命令独立执行。安装使用普通 JAR，不要安装 sources JAR 或 companion-probe JAR。尚未安装到玩家实例，也未发布正式 1.0.0。

## 最终验证记录

```text
COMPANION_PROBE_RESULT passed=27 failed=0
COMPANION_PROBE_RANGED_CREDITS hits=6 lastHitTick=685 allCredited=true
COMPANION_PROBE_SWIM maxAir=209 health=40.0
Verified artifact SHA256: 20BD89A238125BE5A59E8FC83C661BD815E806EB9F15737251B9BE2C8B48857A
```

服务端日志持续记录狼的 `landed a melee hit ... progress renewed=true`，以及骷髅的 `landed a projectile hit ... progress renewed=true`。`COMPANION_PROBE_RANGED_CREDITS` 汇总真实命中次数、末次命中 tick 和逐次续期核对结果；`COMPANION_PROBE_SWIM` 记录水池试验的空气与生命值。

此前基线 `.codex-temp/companion-rc41-baseline/console.log` 为 16 通过、3 失败，证实坐姿、模式残留等待点和低氧反击路径问题存在。中间远程测试的失败日志保留用于说明测试场景中的箭道阻挡、原版避狼、超距召回和小体积目标随机射偏干扰；最终通过不代表修改或取消了十格召回规则。旧场景曾有一次 27 项通过，但重复试验暴露了随机命中率造成的不稳定，因此交付的是修正后的大型靶逐次命中核对测试。
