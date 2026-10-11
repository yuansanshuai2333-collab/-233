## 1.4.3
- 挚友监守者不再因无刺激而钻地消失；兼容钻地过程中成为挚友及恢复钻地姿态的情况。
- 挚友恼鬼、末影螨、流浪商人和商人羊驼暂停自身寿命/退场倒计时；普通距离自然消失也按当前挚友关系保护。
- 好感低于实际挚友门槛后恢复原版规则，不写入永久保留标记；保留受伤死亡、和平模式移除与正常区块卸载。
- 嘎吱怪按用户要求暂不修改，等待机制确认。回想中的生物不受本次保留逻辑干预。
- 新增 23 项服务端检查通过；服务端共 259 项、单元测试 548 项通过。

## 1.4.2
- 修复挚友坚守者仍对玩家施加黑暗、挚友远古守卫者仍施加挖掘疲劳及弹脸提示的问题。
- 按来源生物与每位玩家的当前好感及可配置挚友门槛筛选负面范围效果；其他玩家、增益效果、无来源效果及回想隔离保持原规则。
- 不清除已经存在的效果，避免误删其他来源的负面状态。新增 12 项服务端检查通过，服务端共 236 项、单元测试 548 项通过。

## 1.4.1
- 替换重逢之愿有能量、耗尽、未绑定三种状态的图标为用户提供的新图。
- 三种客户端模型与贴图加载验证通过；与 1.4.0 相比全部 class 字节一致，配方和玩法不变。

## 1.4.0
- 新增「重逢之愿」：任意四瓶自然之需精液瓶、四枚友情之证和一颗钻石制作，手持潜行右键绑定挚友后亮起。
- 记录绑定生物死亡时的信息，可主动在附近安全复活，保留身份、名字、好感与归属；死亡正常掉落，复活不复制装备和库存。
- 使用后物品黯淡，中心放该物品、上下友情之证、左右对应物种精液瓶可充能；唯一绑定和存档中的能量记录阻止重复复活。
- 使用用户提供的未绑定、有能量、耗尽三张贴图，已绑定名称显示生物名字；保留原版宠物控制与回想隔离。
- 验证：548 项单元测试、224 项原有服务端回归、35 项功能检查、4 项真实重启检查、3 项客户端贴图加载、5 项无自然之需启动检查通过。

## 1.3.1
- 修复跟随传送距离设为 2 格时，小型飞行伙伴因固定 2.5 格后方偏移而始终无法传送的问题。
- 小距离落点适配实际召回半径，保留体型、碰撞、后方位置和范围检查；大体型放不下时仍拒绝传送。
- 验证：544 项自动化测试、167 项服务端检查、5 项无自然之需启动检查通过。整体检查原始问题清单与修复前失败日志已保留。

## 1.3.0
- 新增飞行伙伴空中传送跟随：优先选择玩家身后的安全空域，检查完整体型、方块、液体、其他实体、区块及高度边界；默认开启，可关闭。
- 新增“防止误伤自己的挚友”设置：默认关闭，开启后按可配置挚友门槛拦截玩家可归属伤害，保留对主动攻击者的还击及回想隔离。
- 保留原版宠物控制；不新增区块卸载召回、物品或配方，留待后续友情之证与新物品方案。
- 验证：544 项自动化测试、162 项隔离服务端检查、5 项无自然之需启动检查通过；未完成完整客户端整合包游玩验证。

## 1.2.44
- 修复战斗目标刚成为挚友或被驯服后，旧攻击记录在下一次扫描前仍可能允许伤害的问题；选敌和伤害入口立即复核现有友方保护。
- 保持原有战斗与好感规则；不扩大未登记原版战斗的限制。
- 验证：542 项单元测试、105 项服务器检查、5 项无自然之需启动检查通过。

## 1.2.43
- 挚友生物停止无故攻击；补齐坚守者特殊选敌、凋灵随机发射与可归因伤害保护，保留合法自卫、保护和战斗目标。
- 请求横幅增加独立编号、服务端定期校验及客户端刷新超时处理，兼容单人暂停。
- 多人需客户端与服务器同步更新，本版使用新的横幅同步协议。
- 验证：542 项单元测试、87 项服务器检查、5 项无自然之需启动检查通过；未完成完整客户端整合包游玩验证。

# 1.2.42

- 取消未接入的过程额外好感设置，删除配置界面选项、语言文本与未使用的奖励入口。
- 旧配置中的该项自动忽略并在正常保存时移除；保留其他好感增减设置和历史存档数据。

# 1.2.41

- 修复召回距离设得较小时，生物被传到范围外并反复触发召回的问题。
- 召回只选择配置距离内的安全落点；范围内没有安全落点时保持原位，保留原有区块、碰撞和危险方块检查。

# 1.2.40

- 修复活动跟随记录超过 512 条后被全部清空的问题，避免跨维度跟随状态丢失；实体卸载时仍逐个清理。
- 修复合法自定义数值超出设置滑块范围时手柄越界的问题；保留真实数值显示，仅在操作滑块时修改配置。
- 修正自动保护玩家及保护半径的中英文说明，使其与熟悉阶段防卫、原版宠物及伙伴控制规则一致。

# 1.2.39

- 修复关闭“包含原版已驯服生物”（allowVanillaTamedCreatures）后，新驯服的狼、猫等可驯服生物及马类仍自动建立挚友关系的问题；新驯服与旧存档加载补齐现在遵循同一开关。
- 保留原版驯服、归属和控制，保留已有好感记录；默认设置及主动互动、子代规则不变。

# 1.2.38

- 修复主动互动准星优先选中实体的分支失效，避免附近多只生物时选错目标；阻止选择隔墙目标，保留可见视锥辅助和配置距离。
- 命令等带自愿启动标记的动画成功启动后，统一清理该生物对该玩家的旧请求，防止请求残留及后续超时扣好感；失败启动不消耗请求，其他玩家请求保持独立。
- 整体检查完成并冻结问题清单后逐项修复。阶段、奖励、交易、子代、原版宠物和怀孕规则不变。

# 1.2.37

- 修复实体好感附件被替换或移除后仍读取旧缓存的问题，确保后续好感增减写入实际持久化数据；只读查询仍不会创建空关系。
- 行为切换的重复回调保护改用服务器统一 tick，防止跨维度世界时钟差异导致同一次点击重复切换；继续按玩家、生物和维度隔离。
- 收紧自然之需内部适配的版本检查，仅识别已验证的 1.5.1 及其构建元数据，避免将 1.5.10 等不同版本误认作已验证版本。
- 不改动阶段门槛、奖励、免费交易、子代、原版宠物控制与怀孕规则。验证详情与限制见本轮整体检查报告。

# 1.2.36

- 整体审查后统一熟悉与亲近阶段的原有战斗能力判断，避免铁傀儡等升阶段后失去保护响应；无近战伤害属性的生物不会被调用伙伴的近战执行入口。
- 探测器限频改用服务器统一时钟，避免不同步维度切换后长时间不刷新。
- 生物卸载时释放熟悉保护的临时目标记录；玩家断线和服务端停止时清理动画列表命令缓存。
- 按用户确认隐藏未生效的驯服归属开关，保留旧配置字段和现有归属规则。

# 1.2.35

- 战斗事件按所属维度的时钟清理，防止不同步维度提前删除其他维度的保护/战斗事件；不改变好感区间、战斗模式或事件有效期。
- 增加跨维度和玩家切换维度后的事件保留回归测试。

# 1.2.34

- 探测器收到同一目标的无关系/无效结果时清除旧阶段和家族标签，避免无关系与旧挚友标记同时显示。

# 1.2.33

- 按用户确认接入五阶段：初次见面 0、陌生 1–24、熟悉 25–49、亲近 50–74、挚友 75–100；亲密与满好感合并，数值上限仍独立。
- 正好感敌对生物不再主动索敌该玩家，保留近期受击的原版自卫；补齐普通目标、Brain 目标及史莱姆碰撞路径。
- 熟悉阶段按实际受袭事件保护玩家，仅沿用已有战斗能力；亲近阶段解锁伙伴控制；降级暂停对应权限而不删除主人或历史。
- 挚友正常完成互动按挚友门槛赠友情之证，不再要求达到数值上限；交易挚友资格、请求资格、探测器及好感通知改用当前阶段。
- 新增熟悉/亲近门槛配置，默认挚友门槛改为 75。已有明确配置保留；需要默认区间时将熟悉/亲近/挚友设为 25/50/75。
- 原版宠物控制、回想成对隔离、生育机制保持既有约定。

# 1.2.32

- 修复探测器开关文件首次读取失败后仍被当成已加载、后续切换可能覆盖其他玩家开关记录的问题。失败时保留文件，显式切换时重试，显示刷新不反复读盘。
- 修复旧互动请求网络入口在动画启动失败前清除待处理请求的问题；成功后只清理该玩家自己的请求，补齐死亡/旁观玩家及幼体检查。
- 本轮先进行模块复核并登记问题，再统一修复；不修改好感、免费交易、子代、原版宠物或回想玩法。
# 1.2.31

- 整体复核完成、冻结问题清单后修复命令目标校验。
- 接受/拒绝命令显式指定玩家或非生物实体时，不再错误回退到另一只生物的当前请求，避免误扣好感或误消费请求。
- 未指定目标及指定有效生物的原有行为保持不变。

# 1.2.30

- 整体检查完成并冻结问题清单后再逐项修复，保留检查前源码与文件指纹。
- 修复专用服务器首次使用好感探测器时加载客户端专用类而抛出异常的问题；已在实际开发服务器复现原故障。
- 探测器开关文件改为原子替换，避免写入失败截断旧记录，保留原文件格式。
- 旧关系防卫分支在生物参与动画时让出控制，仅释放自身设定的目标。
- 不调整好感、交易、子代、回想隔离及原版宠物玩法。

# 1.2.29

- 按用户确认取消每只生物 64 条关系自动裁剪，保留所有玩家的已有与新增历史，修复新建关系刚返回就被淘汰导致进度无法保存的问题。
- 修复请求横幅及文本好感条的大整数溢出；横幅正确处理零/负数请求门槛。好感与奖励规则不变。
- 修复语言审查工具对动态前缀的误报，增加真实失败原因键的展开检查。
- 505 项自动检查通过；有/无回想室的隔离开发服务器均启动、重载和正常保存退出。图形客户端及多人完整整合包仍待验收。
# 1.2.28

- 按服务端 recall_room=true 元数据隔离回放，不新增回想室依赖。
- 回放不进入自然关系请求拦截与动画奖励/惩罚结算；仅保护同一回放中的参与关系，不全局冻结其他玩家。
- 成功进入回放时无惩罚清理参与玩家自己的待答请求，避免超时扣分；结束/服务器停止清理临时状态。
- 498 项自动检查通过；与回想室 1.1.5 共同安装的开发服务器启动、重载及退出通过。实际多人回放待验收。
# 1.2.27

- 六轮独立范围复查；最小化修改，不调整既有玩法。
- 保存与恢复剩余请求冷却，修复实体重载/重进后冷却丢失。
- 修复客户端将零或负数挚友门槛错误替换为 100 的显示问题。
- 485 项自动检查通过；隔离开发服务器两次启动、配置重载及保存重启验证通过。完整整合包与图形客户端尚待验收。
# 1.2.26

- 修复自动驯服先修改原版主人、后检查自然关系归属的顺序问题；现在在任何驯服写入之前检查归属。
- 已驯服但主人信息缺失的生物不再被当作无主生物认领；主人离线但 UUID 存在不受影响。
- 伙伴绑定统一使用归属检查，并完善失败提示。
- 新增 8 项回归检查，总计 480 项通过；未完成多人客户端实机验收。
# 1.2.25

- 修复请求显示名称超过网络字段上限时的编码异常。
- 请求/交易显示名称截断保留完整 Unicode 代理对，不改实体或物品原名。
- 472 项自动测试通过，含原版编码器故障复现与完整请求包编解码；实际整合包待验收。
# 1.2.24

- 配置先写同目录临时文件再原子替换；失败不直接截断原文件。
- 设置保存失败不应用草稿，保留页面提示并提供取消；重载读取失败保留当前配置并报告失败。
- 区分读取 IO 错误与无效 JSON，备份失败不覆盖原文件，null/空内容先备份。
- 466 项自动测试通过；隔离服务端实际锁文件重载失败、解锁重载成功、正常停服验证通过。图形客户端和完整整合包仍待验收。
# 1.2.23

- 村民完成赠礼改为检查当前挚友门槛，防止历史挚友标记让降级后继续赠礼；友情之证和免费交易规则不变。
- 附近同类参考继承增加精确距离与已有归属检查，忽略损坏 UUID，避免玩家与好感配错或无效记录挡住有效候选。
- 458 项测试通过；隔离开发服务端启动/停服通过；实际整合包与图形客户端待验收。
# 1.2.22

- 配置编辑使用独立草稿，保存后在服务端线程发布；重置项目保留其他草稿。
- 接受/拒绝请求统一存活、世界、距离与到期校验；客户端撤下横幅核对请求生物 UUID。
- 全局网络/断线回调仅初始化一次；停服清理原生菜单世界引用。
- 修复非有限浮点配置与极端好感增量溢出。
- 450 项自动测试通过；隔离开发服务端启动/停服通过；完整整合包和图形界面仍待实测。
# 1.2.21

- 出生自动挚友取可配置门槛；关闭时 NoN 玩家子代取初始好感；重载不回填已有出生记录。明确附近同类继承的适用范围。
- 请求由独立世界/接收者记录驱动撤销，补足底层提前清除及跨维度路径；发送与等待过程检查距离。
- 原版可驯服基类保留原生移动/战斗控制，附属关系数据保留。
- 挣脱改用 NoN stop；界面内绑定键可用。原生选择界面代替 G 的自制列表，独立区分附属菜单与调试菜单。
- 设置页增加拖动位置滑块和整页滚动；多人服务端选项禁止客户端误改；单人保存后重新载入。
- 文案区分挚友门槛和好感上限。441 项测试通过；隔离开发服务端启动通过；图形客户端/实际整合包场景待验收。
- 未修改孕期、受孕、牛奶或怀孕效果。
## 1.2.20+1.21.11

- Fix request failure observation, refusal fallback, range checks, dimension ownership and stale HUD cleanup.
- Let explicit NON debug selections bypass only the addon's autonomous-request gate; fix final-stage completion hooks in both namespaces.
- Grant the completion token when affinity first reaches its cap; initialize newborn player offspring at the configured friend threshold.
- Show the trade courtesy panel only for an eligible free offer. Preserve event-earned quotas and refresh relationship discounts conservatively.
- Change the default interaction key to G. No pregnancy mechanics or milk behavior changes are included.
- Restore release gating by removing 14 obsolete WIP exclusions verified as already shipped in 1.2.19.
- Validation: 437 tests passed; release gate passed; isolated development server reached a loaded world. Full modpack interaction acceptance remains pending. See HANDOVER-1.2.20.md.

## 1.2.19+1.21.11

- A creature that has no interaction animation with the player no longer asks for a deep interaction at all.
  The request gate now asks whether any animation matches the pair, so a creature whose species has no
  animation cannot offer a prompt that could never be honoured. Both request entry points -- the periodic
  scan and the "a friend tried to act on its own" path -- go through the same decision.
- The gate asks whether an animation EXISTS, not whether it could start where the pair is standing. A real
  animation blocked by the ground, a wall or water is still something the creature may offer to ask for.
- Accepting a request that then cannot start now explains the actual reason in the action bar and in chat
  ("not enough space", "no suitable wall", "requirements not met", and so on), instead of a generic
  "cannot start now". The reason comes from the same per-attempt observation the manual selection menu
  already uses, so both paths describe one failure the same way, and an unreported failure says so rather
  than guessing.
- Accepting a request that then fails costs NO affinity. Only an unanswered request (timeout) and an
  explicit rejection still charge, exactly as before. Trade and family behavior are unchanged.
- Verification: the full build succeeded and all 436 automated tests passed (55 classes, 0 failures,
  0 errors, 0 skipped; baseline was 426), including 10 new rule and wiring tests for these two rules.
  The dev client was started against this source and reached a loaded world with the NeedsOfNature
  animation integration enabled and no Mixin or load error. These checks do not replace playing the two
  behaviors in game; see the handover for what is still unverified.

To update, remove the previous Natural Relationships JAR from your mods folder and install this version. Keep only one version of the mod installed. You do not need to delete your worlds or configuration files.

## 1.2.18+1.21.11

- Fixed a startup crash caused by a UTF-8 byte order mark (BOM) in the client Mixin configuration on systems using a non-UTF-8 default character encoding.
- Removed the BOM and added regression tests using the Mixin JSON parser with UTF-8, GBK, and Windows-1252.
- Retained the updated mod icon. No trading or family relationship behavior changed in this update.
- Verification: the full build succeeded, and all 426 automated tests passed. Automated tests do not replace in-game testing.

To update, remove the previous Natural Relationships JAR from your mods folder and install this version. Keep only one version of the mod installed. You do not need to delete your worlds or configuration files.

## 1.2.0-dev.16 (development)

- Manual interaction failures now show a localized reason in the action bar and chat.
- Observe the engine's own space, support, wall and water refusal messages only during the selected start;
  preserve every start decision and use an explicit unknown-reason fallback for unreported failures.
- Keep feedback isolated between attempts and candidate enumeration; add five rule tests.
- Trade behavior is unchanged. Runtime and in-game validation are reported in the dev.16 handover.

## 1.1.4

Release tooling and documentation only; **no mod code changed**. The jar's classes are content-identical to 1.1.3
(verified by the tool described below, which now hashes decompressed entries instead of trusting a checksum), so the
only difference inside the jar is the version string that a rebuild necessarily produces.

- **`tools/jar-diff.mjs` now compares SHA-256 of the decompressed entry content, not the central directory's
  CRC-32.** A 32-bit CRC match does not strictly prove two files are byte-identical, so the previous output
  ("every class is byte-identical") claimed more than the check supported. The wording follows the check now.
  1.1.2 -> 1.1.3 still reports every class identical, and this time the claim really is byte identity.
- **The work-in-progress gate's limit is now stated instead of overstated.** `tools/wip-sources.txt` only blocks
  classes that are *registered* in it. A new unfinished class that nobody added to the list is excluded by nobody
  and scanned by nobody, so the build and the gate pass it together. `VERSIONING.md` said the opposite
  ("writing it wrong cannot reach a delivery"); that sentence is gone. The stated compensating control is the
  per-entry diff against the previously released jar -- every added class is printed by name, and 1.1.1's leak
  would have appeared as `added: 5 class` on that line.
- **Corrected where a completed trade writes what**: `uses` goes to that `TradeOffer`; villager experience and the
  level-up timer go to the villager entity; the player's experience is a newly spawned `ExperienceOrbEntity` in the
  world. The previous note collapsed all of them onto the offer.
- **The cure entry point for 1.2.0 was redesigned before any code was written**: the converter UUID is read from the
  *old* entity on the conversion-neutralising path, recorded as a cure fact that survives the player being offline
  or in another dimension, and applied to the relationship only after the attachment transfer -- never as the sole
  entry through `handleInteraction(ZOMBIE_VILLAGER_CURED, ...)`, which silently does nothing when the player cannot
  be looked up and runs before the attachment transfer. Five tests are specified for it. See
  `RECON-1.2.0-free-trades.md` section 6.2.
- Rebuilt and re-verified: 269 tests pass, 0 failures; `ReviewChecks` (12 assertions) and `LifecycleChecks` pass
  against this jar; the gate passes; both installed instances updated to this exact build.

## 1.1.3

Release tooling and documentation only; **no gameplay code changed**. `tools/jar-diff.mjs` makes that a
machine-checked fact rather than a claim: the 1.1.2 and 1.1.3 jars both contain 125 classes, none added, none
removed, and every class is byte-identical -- the only entry that differs is `fabric.mod.json`, whose version
string is the point of the rebuild.

- **The release gate is now fail-closed.** It previously printed `MISSING` for a required entry and could still
  report `PASS`, and an interrupted ZIP-directory walk was never asserted against the declared entry count. Every
  assertion must now actually be evaluated: an unreadable jar, an inconsistent or truncated central directory, a
  missing `fabric.mod.json`, an unreadable or absent work-in-progress list, a `-sources`/`-dev` by-product, and a
  mismatch between the file name and the jar's internal version are all failures. Verified in seven cases: a clean
  jar passes, and a development jar carrying five work-in-progress classes, the sources jar, a renamed jar, a
  truncated jar, a zip with no `fabric.mod.json`, and a run with the list removed all fail.
- **A release build no longer depends on moving sources aside by hand.** `gradle clean build -Pwip=false` excludes
  the sources listed in `tools/wip-sources.txt` -- the single list that both `build.gradle` and the gate read --
  and runs the gate automatically at the end of the build. The default (no property) is a development build that
  still compiles those sources and runs their tests, so a forgotten switch yields a jar the gate rejects instead of
  one that silently omits code. The gate caught a real mistake while this was written: it was first wired to Loom's
  `jar` task, which writes a `-dev` jar into `build/devlibs`, and it refused that jar.
- **`tools/jar-diff.mjs`** compares two delivered jars entry by entry (name plus uncompressed CRC-32) and
  summarises the `.class` subset; `--expect-identical-classes` turns a "no code changed" revision into an
  assertion. It exists because the previous such claim could not be re-checked once the older jar left `build/libs`:
  1.1.1's jar is no longer on disk, so that claim stays unverified by this method, and only 1.1.2 -> 1.1.3 is
  machine-checked.
- **Documentation corrections**: `HANDOVER` no longer describes the deleted `min(currentPrice, recordedBaseline)`
  clamp -- it now states what the tail actually records (`offer.getSpecialPrice()`, taken after the head restored
  it) and keeps the removal as history -- and its artifact path, install table and test steps no longer point at
  1.1.0.
- Rebuilt and re-verified: 269 automated tests pass, 0 failures (the 20 work-in-progress tests are not compiled
  into a release build); the reviewer's `ReviewChecks` (12 assertions) and `LifecycleChecks` pass against this jar;
  the release gate passes; both installed instances updated to this exact build.

## 1.1.2

- **Fixed a shipping error in 1.1.1**: an ordinary later build compiled five classes of an unfinished feature (`FreeTradeQuota` and its nested types) into a jar whose contents were supposed to be frozen. Nothing failed -- the build succeeded and the tests passed -- and it was only visible by comparing the jar's entry list. This version is built with those sources set aside: 181 entries, none of them work in progress.
- **Added a release gate** (`tools/release-gate.mjs`): checks a built jar for classes belonging to work in progress and exits non-zero if any are present. It is a script rather than a unit test on purpose -- during development those sources are supposed to exist, so a test would fail every normal build; the rule belongs at the moment a release is cut. Verified in both directions: a clean jar passes, and the same jar with one work-in-progress entry added fails.
- **Mod metadata now matches the source template**: `authors` is the three credited entries in the repository (the 1.1.1 jar still carried the older single entry, because the template had been updated without a rebuild).
- Rebuilt and re-verified: 269 automated tests pass, 0 failures; the reviewer's `ReviewChecks` (12 assertions) and `LifecycleChecks` both pass against the delivered jar; both installed test instances updated to this exact build.
- No gameplay code changed in this version.

## 1.1.1

- Documentation only; no code or behaviour change. The discount algorithm was explicitly not touched.
- `HANDOVER` no longer describes a `min(currentPrice, recordedBaseline)` clamp on the apply step: that guard was removed as dead code (the restore at the head of the cycle consumes the record, so the clamp could only compare the price with itself).
- The revision record no longer lists per-version test counts that could not be confirmed from the archived reports; only the current build's figure and the two counts the reviewer confirmed (268 for 1.0.54, 269 for 1.1.0) are stated, and the wording for the two processed-number ranges no longer reads as a released version.
- Version metadata regenerated by a real build: `gradle.properties`, the jar's `fabric.mod.json` (`1.1.1+1.21.11`), the jar name, the delivery folder and both installed instances all agree.

## 1.1.0

The villager-trade work that had been shipped as 1.0.50-1.0.56 is consolidated here, because it adds a
FUNCTION (a new configurable benefit), which the project's version rule counts as a second-digit bump even
though no item was added. The fixes that landed on top of it are listed first.

- **Fixed a natural-completion regression shipped in 1.0.49.** The natural branch wrapped the mount-restore call, which runs BEFORE the stop event, so its `finally` cleared the completion mark before the settlement could read it: an animation that finished normally settled to nothing. That branch is now wrapped at the stop-event dispatch itself, the last step before the settlement runs, and a test asserts the mark is readable WHILE the event runs.
- **Configurable villager trade discount, 0-100%** (`villagerDiscountMaxPercent`, default 20). It scales with affinity / friend threshold; 100% means a friend's price can fall to a single item. Slider plus bilingual name and tooltip in the config screen.
- **The discount is a share of the offer's BASE price and is written into the offer's special price** -- the same slot vanilla uses for gossip, curing a zombie villager, "hero of the village" and demand. The two therefore add up. Verified against the vanilla bytecode of `TradeOffer#getFirstBuyItemCount` and of `VillagerEntity#prepareOffersFor` (which APPENDS its own reductions with `increaseSpecialPrice`), rather than assumed.
- **Removal of this module's discount is tracked explicitly** (`VillagerDiscountLedger`): the baseline each discount was computed from is recorded, and the removal writes that baseline back. Three earlier attempts to infer "is my discount still there?" from the price itself failed -- adding the amount back overcharged after an external reset, and the `min`-clamp and "skip if the number changed" variants both failed to remove it at all, so consecutive refreshes accumulated `-4, -8, -12`.
- **The policy is snapshot-priority, and stated as such:** the recorded baseline wins, so an external edit inside the same pricing cycle is overwritten, and a reset is honoured only when the code performing it also drops the records (vanilla's `clearSpecialPrices`, which the mixin hooks). A single observed number cannot distinguish "someone subtracted 1" from "the field was replaced", so no guessing is attempted. Keeping an external delta AND recognising a reset needs a separate price/reset protocol; that remains an open item.
- **One completion produces one chat line.** When the affinity was already capped, the friendship token and a villager's ware were announced in two messages, which reads as two tokens. The token line is deferred and merged into the ware line when both happen -- and since 1.0.51 every capped mating announces its token, not only the first.
- 269 automated tests pass, 0 failures, plus the reviewer's independent `ReviewChecks`/`LifecycleChecks` run against the built jar (12/12 assertions). See HANDOVER-1.1.0.md.

## 1.0.47

- Playing a mating through to its end with NeedsOfNature's animation-debug "next stage" key now settles affinity like a normal completion. NON's explicit advance entry stops the instance at its FINAL stage, so that stop is the animation's own end state; it was being refused as a manual advance.
- The completion is recorded ONLY at the instruction that ends the FINAL stage, never for the duration of the advance call: stepping to an intermediate stage (where NON fires its stage-advance event and a third-party listener may stop the animation) is not a completion, and no "call in progress" state is needed or kept.
- The adapter's compatibility check now verifies the injection anchor itself -- the method must exist AND contain the final-stage stop call -- because a misplaced anchor fails silently under `require = 0`; the anchor is also pinned by a test that reads NeedsOfNature's real class.
- The break-free action ends a forced mating through the same advance entry, and still pays nothing: the escape mark is checked before any reward is granted, so an escaped session is refused whatever the evidence says. Pinned by a test over every evidence kind.
- Debug-started animations settle as an ordinary completion: base increase only, no player-initiated bonus, and the friendship token follows the same cap rule as any other completion (documented, not special-cased).
- Documentation and acceptance criteria corrected: the settlement log reports `stopReason=null` on this path (the adapter observes the completion PATH, not NON's internal reason string), so the pass criterion is `evidence=MANUAL_ADVANCE`; and the three distinct situations "took damage", "the animation actually stopped" and "the player escaped" are now described separately.
- No new item IDs, no config change. See HANDOVER-1.0.47.md for the exact rule, the boundary, and the in-game test steps.

## 1.0.48

- Stability pass on the animation-debug settlement change, from an independent code review. No intended behaviour change.
- Fixed a startup risk introduced in 1.0.47: the bytecode-anchor helper class was placed in the declared mixin package while being loaded as an ordinary class by the mixin config plugin, which Mixin can refuse with `IllegalClassLoadError` (`require = 0` does not cover class loading). It now lives in `com.nonid.affinity.non.compat`.
- The final-stage stop call is now wrapped (`@WrapOperation`) instead of preceded by an injection, so the completion mark is taken back in a `finally`: if the stop throws before firing the stop event, no mark is left behind for a later, unrelated stop to read.
- The compatibility self-check now verifies both anchors by owning class, name and descriptor, and matches the advance entry by its full signature: a same-named call on another class, the wrong overload, a lost mount-restore call, or a name-only match no longer reports success.
- Documentation corrections: "debug start grants a base increase" now says the base increase is granted when the debug-started animation completes; the contradictory `MatingStopEvidence` row in the change list is gone; 1.0.46 is described as a review delivery rather than a release; and `require = 0` is no longer described as making a NeedsOfNature update unable to break the game.
- 256 automated tests pass, 0 failures. No new item IDs, no config change. See HANDOVER-1.0.48.md.

## 1.0.49

- Fixes two defects left by 1.0.48, from a second independent code review. No intended behaviour change.
- **The `@WrapOperation` handler's reason parameter is now `@Coerce`.** NON's stop reason is a package-private enum received as `Object`; without the annotation Mixin judges the handler's parameter types against the target's and rejects the handler, which under `require = 0` fails silently. Verified in the built jar that the annotation is present.
- **The natural-completion path is now wrapped too.** 1.0.48 bounded the mark's lifetime on the advance path only; the tick-driven path still wrote its mark before the mount-restore call with no cleanup. Both completion paths now run through `CompletionMarkScope`, and the record + log on the advance path moved inside that scope, so the implementation matches the documented pseudocode.
- **The cleanup is now tested by executing a wrapper, not by calling the store.** `CompletionMarkScope` is driven with a normal return, a failed return and a thrown exception, asserting the mark is gone in all three and the exception propagates unchanged.
- The completion path's argument layout is verified, not just its name: the plugin and a test check that argument 1 is still the instance id, so a future NON that shifts the parameters is reported instead of silently disabling the injection.
- Documentation: the test guide now quotes the real log line (`is ending the FINAL stage`), and the revision record attributes each review round to the version it reviewed.
- 257 automated tests pass, 0 failures. No new item IDs, no config change. See HANDOVER-1.0.49.md.

> **过程编号说明**：`1.0.50` – `1.0.56` 是"新增村民折扣功能"这一轮开发中使用过、但**从未对外发布**的编号。
> 按版本规则（新增功能增第二位），该轮的功能与全部修复已统一并入上面的 **1.1.0**，此处不再为这些编号保留段落，
> 以免出现无法对应的版本号。其评审与处理过程见 `PLAN-1.1.0-next-stage-settlement.md` 第八节。

## 1.0.45

- Villager-specific affinity discount, capped at 20%, isolated by customer and refreshed on restock.
- Existing friends receive one random sale item for each eligible player-initiated completion, with component-preserving delivery and bilingual actual-reward feedback.
- No new item IDs. See HANDOVER-1.0.45.md for verification limits and exact rules.

## 1.0.44

- Existing Friendship Token is now edible: restores 5 hunger points (2.5 drumsticks), no extra saturation, normal food consumption rules.
- Food and consumption properties live in FriendshipTokenItem for future extensions. Stable item ID retained.

## 1.0.43

- Embedded the selected Natural Relationships icon in Fabric metadata.
- Registered the dedicated Natural Relationships / 自然关系 creative tab with the same image as its icon.
- Moved Friendship Token and Affinity Detector from vanilla Tools to the dedicated tab; item IDs and ordinary item models remain unchanged.
- The tab icon uses an item-model override on its display stack; no extra obtainable icon item is registered.
- Validation: compilation and remapping succeeded; all 5 language audit tests passed; packaged metadata, model definitions and both image resources checked.
- In-game client rendering has not been visually checked in this turn. Prior gameplay test results should not be presented as new 1.0.43 tests.
## 1.0.42

- English display branding: Natural Relationships; updated Chinese and English settings/category titles.
- Retained internal IDs and rc.42 gameplay code. Added bilingual feature/dependency guides and inspection report.
- Validation: 249 existing automated tests passed; release metadata and 117 unchanged compiled classes checked.

## 1.0.0-rc.42

- 补齐原因提示翻译；伙伴命令增加归属、配置及实际绑定结果检查。
- 补充原版驯服与伙伴创建命令的服务器回归检查。

# 更新日志 · 自然关系 (Natural Relationship)

> NeedsOfNature 的附属模组。
> 版本号规则：**修补不新增功能 → 末位 +1**；**新增功能 → 中位 +1**。

---

## 1.0.0-rc.41

- 伙伴模式：移动命令解除旧坐下指令和坐姿；等待点只用于 WAIT，切换其他模式或解除绑定时清除。
- 低氧让行：定时跟随、模式命令与战斗控制共用空气判定；等待反击不再覆盖上浮导航，清理仅影响当前伙伴。
- 增加独立服务端伙伴回归探针与运行脚本，测试源码及探针不进入发布 JAR。
- 验证：118 项相关单元测试通过；隔离 Fabric 服务端 27 项检查通过，覆盖真实移动、近战、箭伤续期及水池上浮。完整客户端模组组合仍待复测。
- 详情与重现：`HANDOVER-rc41-companions.md`。

## 1.0.0-rc.40

候选版本，**未发布正式 1.0.0**。本批：**反编译 NeedsOfNature 本体核实"生物攻击"载荷语义**，
并据此修掉 rc.39 里一个**从未生效**的致命缺陷。**A/D 挣扎界面在 rc.39 中根本不可能出现。**

### 1. 找到的事实（依据：反编译 NON 1.5.11 的 `com.nonid` 全部类）

| 问题 | 事实 | 证据 |
|---|---|---|
| NON 的"生物攻击玩家"到底怎么填 `PlayerAttack`？ | **不填**。NON 自己的生物攻击用**不带 `PlayerAttack` 的 ATTACK 会话**：`NeedsOfNature.buildModeContext(true)` = `NonAnimationSessionData.attack()` | `NeedsOfNature:1147 / 5039 / 5058`、`NonGatherSystem:435`、`NonPlayerAttackSystem:267` 全部用这一形状 |
| `PlayerAttack` 是什么？ | **PVP 专用**。`isPlayerAttackContext(ctx) = playerAttack != null` 才是"玩家在攻击"；生物攻击被判据 `attack && !isPlayerAttackContext(...) && hasMobActor(...)` 识别 | `NonGiveUpSystem:71-73 / 104 / 118-119` |
| 能否手造 `PlayerAttack(victim, null, initiator, attackers)`？ | **不能**。记录构造函数首句即 `victim != null && finishOwner != null && initiator != null`，否则抛 `IllegalArgumentException("Player attack participants must not be null")` | `NonAnimationSessionData:111-129` |
| A/D 进度条究竟靠什么推进？ | 非 PVP 实例（`PvpRole.NONE`）由**客户端本地**累积 `attackProgress`，满格后发送 `StopAttackAnimationC2SPayload`；服务端 `!isContest(id) && isAttackSnapshot && 玩家在 actors 内` → 调用 `completeAttackEscape` | `NonHudOverlay:752-786`、`NeedsOfNatureClient:1067-1084`、`NeedsOfNature:6680-6693` |

### 2. rc.39 的缺陷（**已修**）

rc.39 用 `PlayerAttack(victim=玩家, finishOwner=**null**, initiator=生物, attackers={生物})` 启动强行交流。
构造函数第三参为 null → **每次都抛 `IllegalArgumentException`** → 被 `catch (Throwable)` 吞掉并回退到普通启动。
结果：A/D 界面**从未出现**，只有一条 WARN。而且即便补上非 null 值也不对——那会让 NON 认为"**玩家**在攻击该玩家"
（`isPlayerAttackContext == true`），走上另一条特性、另一套副作用。

**修改**：改为 `NonAnimationSessionData.attack()`，即 NON 自己的生物攻击形状。
顺手删掉手写的 `AnimationStageInfo` 映射，改为直接取 NON 自己的定义
（`AnimationDefinitions.getDefinition(id).stages()`，与 `NonAnimationApi.start` 同一来源），
**恢复了道具抑制槽位掩码**（此前手写映射丢失它，导致动画期间手持物显隐错误）。

### 3. 新增日志（便于实机定位）

- 启动成功：`started forced mating <动画> as a mob-attack escape (instance <id>) so the player gets NON's
  struggle screen (<n>/<m> stage(s) escapable); …` —— 直接说明"这条动画有几个阶段可挣扎"。
  内容包若把阶段标成 `escapable: false`，NON 不会下发挣扎条，这一行会在实机测试前就说明原因。
- 启动失败仍是原样的 WARN：`could not start the forced mating as an attack escape (...)`。

### 4. 新增测试（**防同类回归**）

- `NonAttackPayloadContractTest`（新增，2 例，**直接对 NON 真实类断言**）：
  ① `NonAnimationSessionData.attack()` 是 ATTACK 模式且 `playerAttack() == null`；
  ② `PlayerAttack` 的规范构造函数**拒绝 null finishOwner**（抛 `IllegalArgumentException`），
  而补全后会被接受——即"手造该载荷"这件事本身错误。测试类路径为此加入 NON 本体 jar。
- `MatingSettlementWiringTest` 加强：
  禁止字节码中出现 `NonAnimationSessionData$PlayerAttack` 与 `playerAttack`；要求引用
  `NonAnimationData.attack()`、`AnimationDefinitions.getDefinition`、`AnimationGameplayMetadata.escapable`；
  禁止再手造 `AnimationStageInfo$ActorPropEntry`。

### 5. 验证分层（**不混写**）

| 类别 | 状态 |
|---|---|
| 自动测试 | **249 用例 / 0 失败 / 0 错误 / 33 个测试类**（rc.39 为 246/32） |
| NON 语义核实 | 通过：反编译 NON 1.5.11 全量源码，逐条对照上表 |
| 服务端启动（NON + 你的模组组合 + 368 个动画定义） | 见下（本批已复测） |
| **实机：A/D 界面出现 / 连点推进 / 逃脱扣分 / 不发证** | **仍需人工，未完成** |

### 6. 对 A/D 界面副作用的补充说明（据源码，非猜测）

启动为生物攻击后，NON 会按它自己的"生物攻击"流程处理，**可能**出现：
挣脱成功时按 NON 配置造成**逃脱伤害**（不会低于 1 HP）、给予**逃脱后短暂无敌**、计入**逃脱统计**、
触发**逃脱后追击**（生物可能再次索战）；若 NON 的 destroyed-skin 系统开启，攻击实例开始时还会累积
destroyed-skin 伤害；Give Up 链路也会因 `mobAttack` 为真而激活。
**这些全部是 NON 既有行为**，不是本模组新增。不想要就把 `nonStruggleScreen` 关掉，
强行交流退回"普通动画 + 本模组挣脱键"，代价判定完全相同。

**决定记录（2026-09-22）**：以上副作用经审阅后**确认接受**，`nonStruggleScreen` 保持**默认开启**。
该结论写入 `AffinityConfig#nonStruggleScreen` 注释，并把完整清单补进**配置界面的中英文说明**——
玩家在界面上看到的就是这份清单。

### 7. 产物

| 项 | 值 |
|---|---|
| 包 | `build/libs/naturalrelationship-1.0.0-rc.40+1.21.11.jar` |
| 体积 / SHA256 | 299559 字节 / `41F490187A6E00B6982775D549F9BFE3A4B1829AFE795D9E923B91C3CAB7DB06` |

---

## 1.0.0-rc.39

候选版本，**未发布正式 1.0.0**。本批：**强行交流接入 NON 官方 A/D 挣扎界面与真实逃脱进度**。

### 1. 依据（为什么必须按"攻击交互"启动）

- 逃脱控制仅在攻击实例下发：`escapable = isAttack && isEscapableStage(stage)`；
  `resolveEscapeSettings` 首句 `!isAttack → none()`；`isAttack = startContext().sessionData().isAttack()`，
  而公开 `StartOptions` 无法声明该上下文。
- 连点进度只在 NON 为攻击实例创建的 `NonPlayerAttackSystem$Contest` 中，其创建要求非空 `PlayerAttack`
  且 `victim ∈ actors`（`onAnimationStarted` 首句 `ifnull → return`）。

### 2. 实现

- **强制交流按攻击逃脱启动**：`AnimationStartContext.withSessionData(NonAnimationSessionData.playerAttack(
  PlayerAttack(victim=玩家, finishOwner=null, initiator=生物, attackers={生物})))` +
  `ServerAnimationController.startNowWithContext(...)`，动画定义不变（阶段由公开 `definition.stages()` 映射；
  道具槽位掩码未映射，仅影响动画期间手持物显隐）。失败/成员缺失 → **捕获并回退**普通启动（WARN 日志）。
- **新增混入 `NonAttackEscapeMixin`**（同一版本受限可选配置）：注入 NON `completeAttackEscape` HEAD，
  按实例 id 标记"挣扎成功" → 我方**不奖励 + 扣 `affinityLossOnEscape` + 不发友情之证**；
  已在生产命名空间被改写的字节码中核对该调用点。
- 新增配置 `nonStruggleScreen`（默认开）；关闭则退回"普通动画 + 本模组挣脱键"（rc.38 的按键保留为兜底）。
- 插件新增对 `NeedsOfNature.completeAttackEscape` 的存在性核对（缺失 → ERROR + 状态说明）。

### 3. 副作用（如实说明）

开启后 NON 会把强制交流当作攻击交互：挣脱可能造成伤害、给予逃脱后无敌、计入其逃脱统计，
并可能与 Give Up/defeated 尾巴/逃脱后追击联动。这些都是 NON 既有行为；不想要就关掉 `nonStruggleScreen`。

### 4. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **246 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 混入运行时核对 | 两个混入分别应用到 `ServerAnimationController` 与 `com.nonid.NeedsOfNature`；`completeAttackEscape` 内已调用我们的处理器 |
| 服务端启动（NON + 用户组合 + 默认内容包） | 通过：`Done`，适配器 `enabled and verified`，零注入失败 |
| **实机：A/D 界面、进度推进、逃脱扣分、不发证** | **需人工，未完成** |

---

## 1.0.0-rc.38

候选版本，**未发布正式 1.0.0**。本批：**给强行交流补上"能拒绝"的手段**，并说明 NON 的 A/D 挣扎界面为何接不到交配动画上。

### 1. NON 挣扎界面的门槛（字节码实读）

- `sendEscapeControlState` 中：`escapable = isAttack && isEscapableStage(stage)`；
  `resolveEscapeSettings` 第一句 `if (!isAttack || snapshot == null || player == null) return none();`。
- `isAttack = startContext().sessionData().isAttack()`；公开 `NonAnimationApi.StartOptions` **没有**声明该上下文的入口，
  攻击上下文由包私有 `NonPlayerAttackSystem` 构造。
- 连点进度在包私有 `NonPlayerAttackSystem$Contest`；且 `onAnimationStarted` 首句 `if (PlayerAttack == null) return;`
  —— 伪造攻击上下文只会得到"有界面、点了没反应"的假挣扎，故**不做**。
- 上游请求稿见 `STRUGGLE-FINDING.md` §5（`StartOptions.escapable` / `StoppedContext` 暴露 `StopReason` / 公开 `stop`）。

### 2. 本模组自建"挣脱"（默认未绑定按键）

- 新增 `key.noncreatureaffinity.break_free`（默认未绑定，玩家在"控制"里自行设置）。
- 服务端判定：只认**本人 + 强制**的会话（客户端不能指定目标）；命中后先标记"玩家挣脱"，
  再用公开 API `NonAnimationApi.advanceStage(...)` 有界推进直到实例结束。
- 结果：**不奖励** + 按 `affinityLossOnEscape`（默认 25）扣好感 + **不发友情之证**（正/负与原因判定已锁死）。
- 日志：`is breaking free of forced mating <instanceId>` → `settled as NONE … escapedByPlayer=true` →
  `forced mating escaped by … applied=-25`。

### 3. 修正与保留

- `escape_hint` 由错误的"（攻击它即可挣脱）"改为"（在"控制"里设置"挣脱"按键即可挣脱）"（中英同步）。
- 保留 rc.37：手动推进阶段记为 `MANUAL_ADVANCE`、不结算、不扣挣脱分；`PATH_*`/`ARRIVED` 仅 `debugLogging` 输出。
- 保留 rc.36：挣脱扣分与友情之证门槛。

### 4. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **244 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 服务端启动（NON + 用户组合 + 默认内容包） | 通过：`Done`，适配器两条路径均已观察 |
| **实机：挣脱键结束 + 扣分 + 不发证** | **需人工，未完成** |

---

## 1.0.0-rc.37

候选版本，**未发布正式 1.0.0**。本批依据用户实例日志（`latest.log`，14:24–14:28，rc.36）**定位并修复一个真实误判**。

### 1. 误判：把「手动推进阶段」当成正常完成

- 日志事实：`14:26:21` 拒绝（-15 → 70）→ `14:26:21` 强制交流开始（`forced=true`）→
  `14:26:39/40` **连续 4 次**「已请求下一阶段（服务端）」→ `14:26:40` 判为完成 +10（→ 80）。
- 该聊天行对应 NON 键位 `key.needsofnature.animation.next_stage_debug`（「下一阶段（调试）」），
  服务端执行 `ServerAnimationController.advanceStage(...)`；它在**最后阶段**被调用时会以 `NATURAL_END` 停止，
  而 rc.35/36 把「停在该入口内」也算作自然完成 → 被跳过/提前结束的强制交流照样发奖。
- **修复**：只有 `maybeAdvanceStages` 的 **tick 自动完成**才算 `NATURAL_END`（结算）；
  `advanceStage`（显式推进）记为 **`MANUAL_ADVANCE`** → **不结算**，且**不算挣脱、不扣挣脱分**。
- 新增用例：手动推进不算完成、且不扣挣脱分；证据分类新增 `MANUAL_ADVANCE`。

### 2. 可核验性改进

- 每次发放友情之证都记日志：`friendship token handed to <玩家> for <生物>: reason=… tokens=… (first=…)`
  —— 不再依赖背包快照证明「是否多发证」。
- `PATH_ACCEPTED / PATH_REJECTED / ARRIVED` 等跟随调试行改为**仅 `debugLogging=true` 时输出**
  （本轮日志被它们刷了上百行，是核对困难的原因之一）。

### 3. 保留（rc.36 已实现，本版维持）

- 挣脱强行交流额外扣好感：`affinityLossOnEscape`（默认 25，0 = 关闭；仅强制 + 玩家自己挣脱 + 每实例一次）。
- 挣扎成功不给友情之证：门槛为 `AffinityService.isMatingGainReason(reason)`，挣脱为负向 + `escape` 原因，
  结构上不可能触发。
- NON 自带「挣扎」仍无法用于 API 启动的动画（`resolveEscapeSettings` 要求 attack 起始上下文，
  公开 `StartOptions` 无此入口），已建议上游暴露；当前挣脱手段为攻击生物。

### 4. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **242 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 服务端启动（NON + 用户组合 + 默认内容包） | 通过：`Done`，适配器两条路径均已观察 |
| 既有行为回归（服务端） | 通过（旧宠物回填 `WAIT`/`FOLLOW`） |
| **实机：跳过阶段不结算 / 攻击挣脱扣分 / 无友情之证 / 正常完成仍给证** | **需人工，未完成** |

---

## 1.0.0-rc.36

候选版本，**未发布正式 1.0.0**。本批：**挣脱强行交流额外扣好感**、**挣扎成功不再给友情之证**，
并记录 NON 自带挣扎机制无法用于强行交流的原因。

### 1. 挣脱强行交流 → 额外扣好感（新配置）

- 新增 `affinityLossOnEscape`（默认 **25**，范围 0–100，**0 = 关闭**；配置界面与中英文本已补齐，
  语言审计测试覆盖）。
- 仅在「玩家自己挣脱」+「该次为强制交流」+「配置 > 0」三者同时成立时扣；**自愿交流中途放弃不额外扣**；
  **每个动画实例最多扣一次**（重复停止/重复挣脱标记都不会二次扣）。
- 与拒绝扣分相互独立：拒绝扣分只发生在拒绝那一刻一次；挣脱是另一笔、可单独配置的代价。
- 日志给出实际扣减：`forced mating escaped by …: configuredEscapeLoss=25 applied=-25 affinity=…`。

### 2. 挣扎成功不给友情之证

- 友情之证只在「正向变化 + 原因为完成的交流 + 好感已到上限」时发放；门槛提为共享判定
  `AffinityService.isMatingGainReason(reason)`。
- 挣脱走 `escape` 原因且为负向变化，**结构上不可能**触发该礼物；单元测试锁定
  `escape`/`rejected`/`peak`/空值都不算完成交流。日志明写 `(no reward, no friendship token)`。

### 3. NON 的「挣扎」为何无法用于强行交流（字节码依据）

- `NeedsOfNature.resolveEscapeSettings(...)` 的首个条件即
  `if (!isAttackSnapshot(snapshot) || snapshot == null || player == null) return EscapeSettings.none();`，
  而 `isAttackSnapshot` = 起始上下文 `sessionData.isAttack()`：**只有以「攻击」上下文启动的动画才会下发挣扎控制**。
- 公开 API 的 `StartOptions` **没有**任何声明可挣脱/攻击上下文的入口；攻击上下文由包私有
  `NonPlayerAttackSystem.buildPlayerAttackContext(...)` 构造。伪造该上下文会让 NON 把交配动画当攻击交互处理，
  风险大于收益，故**不做**。
- 建议向上游请求：① `StoppedContext` 暴露 `StopReason`；或 ② `StartOptions` 增加 `escapable` 选项。
- 现状：挣脱方式仍为「攻击生物」（强行交流以 `STOP_ON_DAMAGE`、`ignoreAttackers=false` 启动，
  并发送 `escape_hint` 提示）。**如实说明**：`forcedMatingEscapable` 目前只控制该提示，不控制能否挣脱。

### 4. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **241 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 发布包接线（字节码） | 引用 `escapeLoss`/`recordForcedEscape`/`markPlayerEscape`；不含 `recordRejection` |
| 服务端启动（NON + 用户组合 + 默认内容包） | 通过：`Done`，适配器 `enabled and verified` |
| 既有行为回归（服务端） | 通过（旧宠物回填 `WAIT`/`FOLLOW`） |
| **实机：挣脱扣分、无友情之证、正常完成仍给证** | **需人工，未完成** |

---

## 1.0.0-rc.35

候选版本，**未发布正式 1.0.0**。**重做 rc.34 的结算修复**：rc.34 用「时间估计」当作完成证据，
经审查否决并作废；本批改为使用可核对的**真实停止路径**，并如实报告读不到原因时的限制。

### 1. 查明的事实（按审查要求）

- NON 公开的停止事件**不含原因**（`StoppedContext` 只有 world/instanceId/animationId/actors/stages）。
- NON 内部**有**原因：`ServerAnimationController$StopReason`（`NATURAL_END` / `DAMAGE_STOP` /
  `EXTERNAL_STOP` / `ACTOR_UNAVAILABLE` / `PLACEMENT_FAILED` / `JOIN_REPLACE` / `STOP_ALL` …），
  但类型**包私有**，只传给 `stopInstance(World, UUID, boolean, StopReason)`；
  以完整描述符注入该方法**实测被拒**（Mixin `Scanned 0 target(s)`，映射与中间名两种写法都试过，`require=1` 暴露）。
- 因此**不再假装读到原因**；改为观察「能自然结束动画」的两条路径，并在**运行时被改写的字节码**中逐条核对：
  tick 驱动的 `maybeAdvanceStages` 内联完成分支、公开 `advanceStage` 的最后阶段停止。
- 无法确认时不奖励，并在启动日志明确写出适配器状态与后果。

### 2. 删除了 rc.34 的时间估计

`expectedStageTicks`、循环阶段下限、2 tick 容差、阶段时长/上一阶段窗口全部删除；
测试断言这些名字与 `Facts` 中任何时间字段都不存在。

### 3. 玩家主动归因修正

「由本模组启动」与「玩家自愿主动」分开：加成 = `startedByMod && !forced`。
**强制启动（forced=true）永远拿不到主动加成。**

### 4. 保留并加强的既有改进

- 按实例保存状态与终态：重复停止不二次结算、未知实例不再落入「自主完成」（新增日志含 **instanceId**）。
- 挣脱标记覆盖该玩家+生物组合的**全部在跑动画**（含非本模组发起），且不再依赖伤害回调时序。
- 拒绝扣分仍只在拒绝路径；结算路径**结构上无法**扣分（字节码断言）。
- 可选集成隔离：独立 `required: false` 配置 + 插件按模组与版本（`1.5.1*`）开关、
  在 ASM 类树中核对成员、不加载任何游戏类（早期 `Class.forName` 会崩服、`getParameterTypes()` 会破坏
  cardinal-components 的 mixin，均已实测并规避）。

### 5. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **237 用例 / 0 失败 / 0 错误 / 32 个测试类** |
| 发布包接线（字节码） | 通过（无 `recordRejection`、无 `expectedStageTicks`） |
| 适配器运行时注入 | 通过（生产命名空间导出类中，两条自然结束路径的 handler 调用点已核对） |
| 服务端启动（NON + 用户组合 + 默认内容包） | 通过：`Done`，零注入失败，适配器 `enabled and verified` |
| 非交流路径回归 | 通过（旧宠物回填 `WAIT`/`FOLLOW`） |
| **早期挣脱 / 较晚挣脱 / 正常自愿完成（实机）** | **需人工，未完成** |
| 客户端启动与兼容 | **rc.35 未回归**；rc.33 的「驯马、驯狗暂未发现问题」不适用于 rc.34/rc.35 |

---

## 1.0.0-rc.34

> **本版本已作废，不要使用。** 审查结论：用时间估计推断「正常完成」不可靠 ——
> 无法区分正常完成与较晚挣脱，放宽阈值会把挣脱一起放进来。请使用 rc.35。

候选版本。曾尝试修复「强行发起深入交流 → 玩家挣脱 → 仍然加好感」：按实例保存状态与终态、
挣脱标记覆盖全部在跑动画、结算不再扣分、拒绝扣分只属拒绝路径；但**完成判定使用了时间估计**
（最后阶段是否跑够预期窗口），因此未通过验收。

### 1. 根因

- **NON 的公开停止事件不携带原因**：`NonAnimationEvents$StoppedContext` 只有
  world/instanceId/animationId/actorUuids/actorKeys/stages；原因（`NATURAL_END`/`DAMAGE_STOP`/`EXTERNAL_STOP`…）
  只在其内部 `ServerAnimationController$StopReason`，未暴露。旧代码把「收到 STOPPED」当成「交流完成」。
- 三处连带缺口：无 `ACTIVE` 记录即按**自主完成**结算（生物自主强行发起正是这条）；
  `markEscaped` 只遍历 `ACTIVE`（自主动画标记不到）；挣脱标记写在伤害回调，
  而 NON 对攻击挣脱是**延迟结算**，STOPPED 可能先到。
- 有用的实读事实：`advanceStage` 仅在**最后一个阶段**跑够时长时以 `NATURAL_END` 停止；
  非循环阶段时长 = `max(1, ceil(cycle/speed))`；循环阶段窗口来自包元数据，公开 API 读不到。

### 2. 修复

- 新增纯逻辑 `MatingSettlementRules`：完成 = 未被打断 + **观测到进入最后阶段** + **最后阶段跑够预期窗口**；
  任何「无法确认」一律不发放奖励；判定**没有惩罚分支**。循环阶段用同一动画**上一阶段实测窗口**，
  单阶段循环退化为保守下限。
- 新增纯逻辑 `MatingSessions`：按实例保存状态与终态集合 —— 重复停止不二次结算，
  未知实例不再落入「无记录=自主完成」，`markInterrupted` 覆盖**该组合全部在跑动画**（含非本模组发起）。
- `NonIntegration` 新注册 `STARTED`/`STAGE_CHANGED`，`STOPPED` 只做「取实例 → 交给规则 → 奖励或拒绝」；
  奖励仍只有 `recordMating` 一个入口；**拒绝不扣分**，拒绝扣分仍只属于拒绝路径（`recordRejection`）。
- 删除 `settleAutonomousMating`、`ACTIVE`、`ESCAPED`、`SETTLED_INSTANCES` 四套旧结构。
- 新增可判读日志：拒绝 `configuredPenalty/applied/affinity`；结算 `completed … playerInitiated=…`
  与 `settled … requested/applied/affinity`；拒绝结算 `settled as NO_RECORD (<原因>)`；
  未知实例与重复停止各有独立行。
- 核查全部正向奖励入口：只有 `recordMating`；`recordPeak` 无调用方，不存在挣脱前提前加好感的路径。

### 3. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **233 用例 / 0 失败 / 0 错误 / 32 个测试类**（新增 14 个结算用例） |
| 发布包接线（字节码） | 通过：引用 `MatingSettlementRules`/`MatingSessions`；**不含** `recordRejection`、**不含** `settleAutonomousMating` |
| 发布包服务端启动（NON + 用户组合 + 默认内容包） | 通过：`Done`，动画集成启用，76 个定义加载，零注入失败 |
| 非交流路径回归 | 通过：旧宠物回填 坐下→`WAIT`、`FOLLOW` |
| **实机五场景（拒绝→强行→挣脱 / 自主挣脱 / 正常完成 / 重复停止 / 目标失效）** | **需人工，未完成** |

---

## 1.0.0-rc.33

候选版本，**未发布正式 1.0.0**。本批修复**正常驯马入口漏同步**，并完成兼容性与故障处理核查。
（rc.32 与 rc.33 仅差依赖声明，未交付。）

### 1. 修复：正常驯马从未同步（审查发现并复核确认）

- **根因**：`AbstractHorseEntity.bondWithPlayer(PlayerEntity)` 字节码顺序为
  偏移 2 `setOwner` → 偏移 7 `setTame(true)` → 偏移 39 单一 `ireturn`（**先写主人，后设驯服标志**）。
  旧钩子挂在 `setOwner` 的 TAIL 并检查 `isTame()`，首次驯服时该标志为 `false`，于是每次都提前返回，
  `vanilla_tame_horse` 从未产生。rc.31 发布包内是同一逻辑。
- **修法**：锚点移到 `bondWithPlayer(...)Z` 的 TAIL（成功返回处，另加 `cir.getReturnValueZ()`），
  保留三项检查 `isTame()` + **实际主人**（`getOwnerReference().getUuid()`）+ 逻辑服务端，
  由纯规则 `RelationshipRules.isTameCompleted(...)` 表达。**没有删除 `isTame` 检查**，
  旧存档补齐仍由 `Entity#readData` TAIL 独立承担。
- **覆盖核查**：`bondWithPlayer` 仅在 `AbstractHorseEntity` 声明；马、驴、骡、羊驼、商驼、骆驼、
  骷髅马、僵尸马**均无覆写**；唯一调用方是 `HorseBondWithPlayerGoal`。
- **回归验证**：新增 `HorseTameEntryTest` 直接读取**原版字节码调用序列**并逐项固定
  （`setOwner, setTame, TameAnimalCriterion.trigger, getEntityWorld, sendEntityStatus`），
  断言编译后的 mixin 引用 `bondWithPlayer` 且**不再出现 `setOwner`**，并断言 9 个马类未覆写。
  临时放回旧锚点后该测试**确实失败**（`expected: <false> but was: <true>`），随后已还原。
- 同时修正 `VanillaTameRelationshipMixin` 的注释：猫狗侧顺序**相反**
  （`setTamedBy`：偏移 3 `setTamed(true,true)` → 偏移 8 `setOwner`），两家不能共用一个锚点。

### 2. 兼容性与故障处理核查

- 10 个 mixin 全部**只观察、不取消**（无 `cancellable`、无 `ci.cancel()`、无 `setReturnValue`、无 `require=0`），
  不拦截交互与伤害，不会吞掉喂食/骑乘/装备。
- 伙伴 AI 只对**已绑定伙伴**生效：未绑定/矛盾/死亡生物在入口即跳过；
  对伙伴新增 `MOVE+JUMP` 目标，并把 `FollowOwnerGoal`/`TrackTargetGoal` 用
  `ConditionalGoal` 按「非本模组管理」包一层；低氧让位只放开移动；被 NON 占用时全部模式让位。
- 依赖声明：必需项补上 `fabric-api >= 0.141.6`；`needsofnature`/`geckolib` 保持推荐。
  NON 全程反射调用，缺席时降级并明确提示（已实测）。
- 故障处理：无可用动画按原因提示，不启动、不加好感、不记录成功、不占用；启动期异常记录原因不吞异常；
  `ACTIVE` 只在真正启动后写入。**如实说明**：适配层未暴露 NON 的取消接口，无法替 NON 清理其内部半启动状态。

### 3. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **219 用例 / 0 失败 / 0 错误 / 30 个测试类** |
| 发布包注入目标 | 通过（`method_6752` = `bondWithPlayer` + `TAIL`；`method_5651` = `readData` + `TAIL`） |
| 服务端环境 A（仅本模组 + Fabric API） | 通过：降级提示、回填、坐下→WAIT、计数归零 |
| 服务端环境 B（用户现有组合） | 通过：49 模组零注入失败；`readData` 内与 NON/cardinal-components 共存顺序实测 |
| 客户端（两组）与实机交互 | **需人工，未完成**（见 `HANDOVER-rc33.md`） |

---

## 1.0.0-rc.31

候选版本，**未发布正式 1.0.0**。本批解决 **rc.26 启动阻塞**，并在**发布包**上跑通旧存档补齐与模式落盘。
**rc.26–rc.30 均为坏包或中间包，不要使用。**

### 1. 启动阻塞根因（rc.26）

- `Entity#readCustomData` 是 **`abstract`**、没有 RETURN，`@At("TAIL")` 无法注入 → 整个 `EntityAffinityMixin` 应用失败。
- 补齐钩子改为具体方法 **`Entity#readData(ReadView)`** 的 TAIL；发布包内已核对注解为
  `method_5651`（= `readData`）+ `TAIL`，且无 `require=0`。
- 说明：`method_5749`（= `readCustomData`）本身不是坏名字，投射物两个 mixin 打的就是它，
  因为 `ProjectileEntity` / `PersistentProjectileEntity` 上是**具体**实现。

### 2. 时机正确性（字节码实证，不再靠推断）

- 原版：`readData` 内先调 `readCustomData`（恢复主人与驯服标志），唯一 `return` 在其后。
- Fabric 附件：`readEntityAttachments` 注入在 **`readCustomData` 调用之前**。
- 运行时导出被改写的 `class_1297`：`586 附件恢复 → 589 readCustomData → 672 我们的补齐 → 675 return`。
  即补齐时 **Fabric 挚友数据、原版主人、原版驯服状态三者均已恢复**。

### 3. 修复：坐下宠物没有落到 WAIT（死分支）

- 旧判据 `data != null && getCompanionOwner() == null && isSitting(...)`：老存档首次加载 `data` 必为 `null`；
  且 `getCompanionMode()` 把 `null` 强制成 `FOLLOW`，使「没选过模式」不可表达，`sync` 的默认分支永不执行。
- 现在新增 `chosenMode()` 与纯规则 `RelationshipRules.resolveInitialMode(chosen, preferred)`，
  `sync` 增加 `initialMode` 形参：**玩家已选模式永远优先**，没选过才按状态推断（坐下 → WAIT）。
- 只有真正选过模式时才写 `companion_mode`，缺省即「没选过」，老存档不被改写。

### 4. 修复：逐实体遍历创建并持久化空记录

- `RequestSystem.tickCooldowns`（每 tick 全量）、`NonCreatureAffinity.tickDailyDecay`、`AffinityCommand.countTracked`
  过去使用会创建的访问器，世界第一 tick 就给每只无关生物挂上 `entries: []` 并落盘。
- 全部改为 `peekAffinityData()`；修复后野外未驯服生物**不再带任何附件**。

### 5. 发布包服务端实测（隔离环境）

| 场景 | 结果 |
|---|---|
| 发布包启动（生产命名空间） | 10 个 mixin 全应用，零注入失败，`Done` |
| 已驯服「坐下」的狼（有主人） | `companion_mode: "WAIT"`，`source=load_backfill` |
| 已驯服「站立」的狼 / 骏马 | `FOLLOW` |
| 未驯服对照 | 不建立关系，且无任何附件 |
| 重载（重启服务端） | 模式与归属保留，且**不重复补齐** |
| `allowVanillaTamedCreatures` 关闭 → 开启并重载 | 关闭时不认领不写附件；开启后补齐，坐下者 WAIT |

### 6. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **215 用例 / 0 失败 / 0 错误 / 29 个测试类** |
| 发布包注入目标 | 通过（反汇编 JAR：`method_5651` + `TAIL`） |
| 发布包服务端加载与行为 | 通过（见上表） |
| 客户端加载 / 实机交互 | **需人工，未完成**（见 `HANDOVER-rc31.md`） |

---

## 1.0.0-rc.26

候选版本，**未发布正式 1.0.0**。本批**统一驯服与挚友关系**并**兼容无动画动物**；
**保留**已修复的战斗、召回与投射物归因逻辑。

### 1. 原版驯服成功即成为挚友（新）

**实际调用链（反汇编确认）**：狼/猫等走 `TameableEntity#setTamedBy` →
`setTamed(true,true)` + **`setOwner(owner)`**；马类的 `AbstractHorseEntity#setTame` **只翻标志、不设主人**，
主人由 **`setOwner(LivingEntity)`** 写入。

**因此注入点选在「成功状态与主人数据都可用之后」**：
`TameableEntity#setOwner` TAIL（要求 `isTamed()`）与
`AbstractHorseEntity#setOwner` TAIL（要求 `isTame()`）。
**喂食或开始尝试驯服不会到达这些点** —— 所以「喂了东西」不可能被当成「驯服成功」。
主人取 `setOwner` 写入的 UUID，**全程没有「最近玩家」查找**。

### 2. 统一关系同步（三个入口共用一套逻辑）

新增 `RelationshipRules`（纯逻辑）与 `RelationshipSync`（唯一入口），
**原版驯服 / 好感达标 / 旧存档补齐**共用，避免三套实现分叉。

- 好感：新建按 `friendThreshold` 起，已有只**上调**、绝不下调；保留更高值。
- **不伪造交流次数**（完全不触碰 `matingCount`），**不发放只属于交流的奖励**。
- **绝不夺取**：他人伙伴主人 / 他人原版主人 / 两条记录矛盾（报告为冲突并给可诊断日志，**不选边**）。
- **不重置玩家已选模式**：只在完全没有模式记录时才写 FOLLOW，因此不再经过硬编码 FOLLOW 的旧绑定函数。

### 3. 旧宠物自动补齐（新）

时机为 `Entity#readData` TAIL（此时存档里的驯服标志与主人已是真实值），
**主人不在线也生效**（按持久化 UUID 判断），**无法确认主人则不认领**，
**可重复执行**（关系齐备时在写任何东西前返回，不会每次加载都写或刷屏）。

### 4. 无动画 / 条件不满足时安全拒绝

- 新增 `MatingRefusal`（`NO_ANIMATION` / `CONDITIONS` / `BUSY` / `ERROR`）区分失败原因；
  `ERROR` 记录日志、不吞异常。
- 玩家提示不再笼统：无可用动画时明确说明**该动物暂无可用的深入交流动画**。
- 核对确认：可用性检查**本来就按本次交互的实际参与者**进行（不是「物种有任意动画」），
  失败时不启动动画、不加好感、不记录成功、不占用。

### 5. 配置语义修正

`allowVanillaTamedCreatures` 此前是**纯预留项**（有界面与说明、**无任何实现**）。
本批接入：开启时原版驯服即纳入、旧宠物加载补齐；默认 `true` 即满足要求。说明文案已改为与实现一致。

### 6. 加载复跑再次抓到真实缺陷

补齐钩子首次挂在 `Entity#readCustomData`，但它是 **`abstract`、没有 RETURN**，
`@At("TAIL")` 无法注入 → 整个 `EntityAffinityMixin` 应用失败。
改用具体方法 `Entity#readData(ReadView)` 后通过。**10 个 mixin 全部应用，零注入失败。**

### 7. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **210 用例 / 0 失败 / 29 个测试类** |
| 服务器加载 | **通过**（10 个 mixin） |
| 客户端加载 | **本版本未复验** |
| **实机行为验收** | **未完成**（9 项入口场景 + rc.25 遗留 3 项） |

---

## 1.0.0-rc.23

候选版本，**未发布正式 1.0.0**。rc.22 的**实例归属与清理修复保留**；本批**只修启动方式与文档**，
**不扩大生命周期重构**，转入以游戏结果为依据的验收。

### 1. 启动方式修正

**原因**：`build.gradle` 的 `client` 配置**硬编码** `--quickPlaySingleplayer new_world`，
而 `run/saves` 下是 `devworld`；命令行再叠加 `--args` 会产生**两个冲突值**。

**改为**：

| `-PdevWorld` | `level.dat` | 行为 |
|---|---|---|
| 未提供 | — | **进入主菜单** |
| 提供 | 存在 | 快速进入，并打印 `quick-playing into existing world '<name>'` |
| 提供 | **不存在** | **构建失败并列出可用世界**，不再静默进主菜单、也不新建同名世界 |

三个分支均已实测。检查**只读**：**不删除、不重命名、不覆盖任何存档**。
启动命令：`gradle runClient -PdevWorld=devworld`（**不再叠加 `--args`**）。

### 2. 文档修正

「`setRemoved` 每实例一次」改为 **「清理应可重复调用且保持幂等」**；
明确 **`final` 只表示子类不能覆写，不代表方法只执行一次**。
新增回归 `cleanupIsIdempotent`（重复清理 3 次存储不变、不抛异常、`null` 被容忍）与
`repeatedCleanupIsScopedToItsInstance`。

### 3. 验收记录

**客户端加载：通过** —— 版本 `noncreatureaffinity 1.0.0-rc.22+1.21.11`（日志确认，**非沿用 rc.15**），
集成服务器启动、玩家登录进入世界、本模组零 ERROR。

前两次失败的**诚实记录**：第一次 `--args` 与硬编码值冲突；第二次按 `-PdevWorld` 启动但进程在模组
初始化后**被外部终止**（无 `hs_err`、无 crash-report、无 Java 异常）；第三次干净复跑成功。

**第 2–5 项（狼近战 / 骷髅远程 / 四模式与召回 / 低氧与重载）未完成，需人工操作**，
步骤与判据见 `STATUS-rc23.md`。**不把「等待人工测试」写成通过。**

### 4. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **203 用例 / 0 失败** |
| 服务器加载 | **通过** |
| 客户端加载 | **通过**（rc.22 实测） |
| 实际行为验收 | **未完成** |

---

## 1.0.0-rc.22

候选版本，**未发布正式 1.0.0**。本批**删除 rc.21 的猜测性保护逻辑**，改为**按实体实例归属**，
并把清理挂到**唯一入口** `setRemoved`。

### 1. 删除「存储加载后跳过第一次销毁」

rc.21 的 `STORAGE_AUTHORIZED` 是猜测逻辑，后果是：**普通重载投射物即使从未再次 `setOwner`，
最终销毁也不释放状态，要等一个永远不会来的第二次移除** → 泄漏。
而且它**本来就多余**（见下）。

**现在**：状态表按**实体实例**（`IdentityHashMap`）存储，uuid 仅用于伤害路径查找
（并校验记录 uuid，复用的 id 不会命中陈旧记录）；**没有跳过分支** ——
条目在**其自身实例的第一次 `setRemoved`** 即释放。跨维度时新旧实体**各持自己的条目**，
旧实体移除**只**释放自己那条，归属靠实例而不是对调用顺序的假设。

### 2. 核对实际移除入口（结论与 rc.21 不同）

| 路径 | 实际入口 | 经过 `remove()` |
|---|---|---|
| 真销毁 | `remove()` → `setRemoved(...)` | 是 |
| **维度切换** | `removeFromDimension()` → **`setRemoved(CHANGED_DIMENSION)`** | **否** |
| **区块卸载** | `ServerEntityManager` → **`setRemoved(UNLOADED_TO_CHUNK)`** | **否** |

**rc.21 挂在 `remove` 上的钩子只覆盖真销毁**，卸载实体的条目**从未被释放**，
强引用表回收不了卸载实体。已改挂 **`Entity#setRemoved`** —— `final`、每实例一次、
三种移除的唯一共同入口，并**同时按实例归属**解决同 UUID 新旧实体问题。
**不是**只改挂钩位置，也**不是**跳过第一次删除。

### 3. 新增回归

`singleRemovalReleasesWithoutResidue`（**读取置终态 → 不再赋主 → 销毁一次 → 状态表与 uuid 索引均无残留、
回到基线**）、`repeatedRoundsDoNotAccumulate`（5 轮每轮回到基线）、
`attributedProjectileReleasesOnFirstRemoval`、`removalIsScopedToItsOwnInstance`（同 uuid 新旧实体互不干扰）。
rc.20 那个「手动调用清理会删除状态」的测试**已删除**——它**不能证明游戏跨维度实际执行了该调用**。

### 4. 测试与报告定位修正

- **分别标注**：规则模拟 / 契约与字节码断言 / **真实生产调用链（未执行，仅反汇编推断）** /
  客户端加载与实机行为（未做）。**不再称规则模拟与真实调用链等价。**
- **保留**：rc.19 交战终态保护、rc.20 读取失效方案、rc.17 整方法近战包装（`@WrapMethod`），本批未改动。

### 5. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **201 用例 / 0 失败** |
| 服务器加载 | **通过**（8 个 mixin 全部应用） |
| 客户端加载 | **本版本未复验**（rc.15 曾通过） |
| 实际行为验收 | **未完成** |

---

## 1.0.0-rc.21

候选版本，**未发布正式 1.0.0**。本批**只修跨维度执行顺序**，未扩大功能。

### 1. 修复同 UUID 新旧实体的清理冲突

**反汇编得到的真实顺序**（`Entity#teleportCrossDimension`）：

```
EntityType.create(world, reason)   // 新实体，新 UUID
newEntity.copyFrom(oldEntity)      // uuid 被复制到新实体上
  ...readData / readCustomData     // 新实体读取 → 存储读取钩子在此触发
oldEntity.removeFromDimension()    // setRemoved(CHANGED_DIMENSION)
```

**新实体先读、旧实体后移除，而两者共用同一 UUID**。读取侧建立终态后，
旧实体的移除按 UUID **删除**了它 → **保护被抵消** → 新实体可被重新归因。

**先在 rc.20 上复现**：
`the old entity's removal must NOT erase the new entity's terminal state ==> expected: not <null>`

**修复**：

- 新增 `STORAGE_AUTHORIZED` 标记：「刚由存储读入」的投射物被标记为存储保护中。
- `onRemoved(id, destroying)` 按**是否销毁**分流：**销毁型移除且仍受存储保护 → 保留终态，只消耗标记**
  （即「旧实体随维度切换销毁」）；其他情形（含**卸载型**移除）正常释放。
- **标记是消耗性的，不是永久保留跨维度 UUID**：实体一经重新赋主即消耗该标记，
  新实体最终被真正销毁时释放状态并清掉标记。

**并由反汇编澄清一点**：`removeFromDimension()` 调用的是 `setRemoved(CHANGED_DIMENSION)`，
**不经过 `remove()`**，而移除钩子挂在 `remove()` 上 —— 因此在**当前映射**下跨维度
**不会触发移除钩子**，这条冲突在本版本里不会发生。测试断言**两种可能都成立**，
使保护不依赖「钩子恰好不被调用」这一巧合。

### 2. 按真实顺序的回归

`crossDimensionOrderKeepsTheNewEntityNonAttributable` 严格按
`create → copyFrom → 读取 → 旧实体移除 → 重复赋主 → 命中` 执行，覆盖：
新实体读取并失效 / 旧实体以 `CHANGED_DIMENSION` 移除后**状态仍在且仍为终态** /
新实体重复赋主**不得重新归因** / **命中不刷新 `lastProgressTick`** /
新实体最终销毁后**状态可释放** / 新发射的其他箭**不受影响**。
**没有**只测「清理后再读取」。

### 3. 报告与注释修正（撤回两处相反描述）

| 我此前写的 | 实际（已核实） |
|---|---|
| `getUuid()` 的引用语义 | **返回 `uuid` 字段**（`getfield uuid; areturn`） |
| `PersistentProjectileEntity.readCustomData` **不**调用父类，只挂父类会静默失效 | **它确实 `invokespecial ProjectileEntity.readCustomData`**，**两个读取钩子对箭都会触发** |

- 保留两个钩子的理由改为**不依赖委托持续存在**；重复触发**由幂等吸收**并已断言。
- 契约测试同步改正：断言子类**确实委托父类**（此前断言的是相反结论）。
- **明确声明**：「读取钩子存在」**不等于**整个跨维度流程已通过。

### 4. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **201 用例 / 0 失败** |
| 服务器加载 | **通过**（8 个 mixin 全部应用） |
| 客户端加载 | **本版本未复验**（rc.15 曾通过） |
| 实际行为验收 | **未完成** |

---

## 1.0.0-rc.20

候选版本，**未发布正式 1.0.0**。本批**只处理投射物重载**，**保留 rc.19 已修好的交战清理规则**。

### 1. 修正卸载状态方案（撤回 rc.19 的错误说法）

rc.19 把「**不主动删除**」写成了「**保证重载后保留**」，这是错的，有两条独立原因：

1. **`WeakHashMap` 不保证存续**：键是 `UUID` 对象，除传入时**无任何强引用**，条目**任意 GC 即可能消失**。
   所谓「不删除」在 GC 面前没有意义。
2. **换成强引用表也仍不成立**：已核实 `ChunkSerializer.write` 只序列化 **`entity.getUuid()`**，
   以 id 为键的归因**无法跟随实体穿过存储**。

**保留重载前归因需要持久化状态 + 处理「重启后代次重新开始」，不是换掉 WeakHashMap。**
已按**保守方案**实现：

| 情况 | 处理 |
|---|---|
| **同次运行内** | 首次归因受保护；换主 / 命中已消费 / 交战结束 → **转终态不可复活**；清理**绝不删除**（rc.19，保留） |
| **区块卸载 → 重载** | 移除时**释放条目**；实体从存储**读回时置为终态** |
| **服务器重启** | 同上；并避免「重启后**代次从 1 重新开始**」导致误判 |

- 存储改为**强引用 `HashMap` + 显式释放**，不依赖 GC 时机。
- 新增**存储读取缝**：`ProjectileStorageStampMixin` + `PersistentProjectileStorageStampMixin`。
  **两个都需要** —— 两个投射物类**各自声明** `readCustomData(ReadView)`
  （1.21.11 的 View 式 API，旧 `readNbt` 不存在），子类**不调用**父类实现，
  只挂父类会**静默失效**。该缝覆盖**区块重载、世界重载、维度切换、服务器重启**。
- `shouldReleaseOnRemoval(boolean)` 现在**两种情况都释放**；
  **保护重载的是读取侧，不是保留条目** —— 注释与报告都写明，避免再次把「不删除」当作「保留」。

**明确代价**：**重载前射出的箭不再贡献进展**，但**不会错误续期新交战**。
取舍理由：漏一次续期只是让战斗**略偏早**结束，**错误续期**会让已结束的战斗继续活着。

### 2. 测试真正的重载边界

新增 `reloadedArrowCanNeverBeAttributed`（**保存 → 清空运行时存储 → 从存储重建（走生产缝）→
同目标新交战 → 重复赋主 → 命中**，断言旧箭不可归因、**不刷新 `lastProgressTick`**、
**新箭仍能刷新进展**）、`reloadedArrowStaysTerminal`、
`restartIsCoveredByTheReadSeam`（代次 11 → 1 也不能让旧箭看起来是当前的）、
`removalsReleaseAndTheReadSeamProtects`。与新方案矛盾的旧测试 `unloadKeepsTheAttribution` **已删除**。

### 3. 跨维度单独核对

`CHANGED_DIMENSION` 的旗标为 **`shouldDestroy = true` / `shouldSave = false`**：原实体被销毁，
目标维度里是**重建**的实体，重建会**读取存储**，因此**同一读取缝已覆盖维度切换**。
**没有**把「`shouldSave() == false`」当作「投射物不可能在别处继续存在」的证明 ——
保护来自**读取侧的显式失效**，与销毁/保存语义无关。

### 4. 报告纠正

- **撤回**「卸载状态保证保留直到销毁或服务器停止」。
- **明确区分**同次运行内的终态保护 / 区块重载 / 服务器重启三种情况。
- `NO_PROGRESS` 判读沿用 rc.19：只有**持续命中或持续接近**时仍判停滞才异常。

### 5. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **200 用例 / 0 失败** |
| 服务器加载 | **通过**（8 个 mixin 全部应用） |
| 客户端加载 | **本版本未复验**（rc.15 曾通过） |
| 实际行为验收 | **未完成**（本批开始，见状态摘要第五节） |

---

## 1.0.0-rc.19

候选版本，**未发布正式 1.0.0**。本批**只修投射物交战清理这一条链路**。

### 1. 修复生产清理：`clearForCompanion` → `markEngagementEnded`

**缺陷（已核实）**：`ProjectileStamps.clearForCompanion()` 用 `removeIf` **删除**有效记录。
删除后的记录与「从未见过」不可区分，于是仍在飞的旧箭在下一次 `setOwner` 时被当成新箭、
**重新建立归因并拿到当前交战代次**。与 rc.18 修的删除缺陷是**同一类**，漏在了清理路径上。

**修复**（采用「转终态」方案）：

- 改名为 **`markEngagementEnded(UUID)`**，语义与实现一致：**把有效记录转为 `INVALIDATED` 终态，绝不删除**。
- **已终态的条目不动** —— 删掉会丢掉阻止被重新建立的那层保护。
- 选**转终态**而非「保留原归因」：它**不依赖代次比较是否发生**，即使将来战斗复用相同代次也照样拒绝。
- `forget(companion)` 同样改为终止而非删除：解绑时其箭可能仍在空中。
- 内存不因此增长：条目仍由**真正销毁**投射物时释放。
- 方法名、注释、报告已与实现对齐；**不再存在任何「删除后允许重建」的路径**。

### 2. 修复错误测试（先确认失败，再修到通过）

`oldArrowIsNotRevivedByANewEngagement` 原先**允许重新归因**、并用测试自记的 `GEN_ONE` 检查命中，
**绕开了实际错误**。重写为严格执行：

**首次归因 → 生产交战清理 → 同目标新交战 → 同一旧箭再次赋主 → 从存储读取实际状态 → 按生产命中路径判断**

- 断言读取**存储里的实际状态**，不用测试预存的旧代次；
- 旧箭**不可归因**、记录**仍然存在**（不是被删除）、命中**不刷新 `lastProgressTick`**；
- **删除**「重新归因也可接受」的分支；新增「**新箭仍可正常刷新进展**」以防误伤正常远程攻击。

**先确认失败（证据）**：临时改回删除行为后新测试失败，信息正是该缺陷 ——
`expected: <null> but was: <State[... generation=3, launchedTick=140, valid=true]>`，
即**旧箭被重新归因并拿到新交战代次 3**。恢复修复后全部通过。

### 3. 移除与重载边界

`Entity.remove` **不等于永久销毁**。已核对 `RemovalReason`：`UNLOADED_TO_CHUNK` /
`UNLOADED_WITH_PLAYER` 的 `shouldSave()` 为 true（**保留待重载**），
`KILLED` / `DISCARDED` / `CHANGED_DIMENSION` 为 false（**真正销毁**）。

移除钩子改为按 `shouldSave()` 分流：**保存型移除保留归因**（否则重载后的箭会变成「从未记录」，
再次可能被当成新箭）；**销毁型移除才释放状态**。分流决策 `shouldReleaseOnRemoval(boolean)`
是**纯函数**，由普通测试覆盖，而不是只能靠实机观察。

### 4. 报告纠正

- **撤回**「正式结束交战并清理后旧箭不得续期已验证」的结论 —— 它当时建立在错误测试之上。
  现述为：**规则与接线测试已通过（含先失败证据），实机未验证**。
- **`NO_PROGRESS` 不再一概视为修复失败**：只有**持续有效命中**或**持续接近**时仍判停滞才是异常；
  无进展时退出是**正常行为**；目标死亡 / 模式切换 / 召回 / 目标变为受保护 / 跨维度都是**合法结束原因**，
  **不要求日志里完全没有 `engagement ended`**。

### 5. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **196 用例 / 0 失败** |
| 服务器加载 | **通过** |
| 客户端加载 | **本版本未复验**（rc.15 曾通过） |
| 实际行为验收 | **未完成** |

---

## 1.0.0-rc.18

候选版本，**未发布正式 1.0.0**。**保留 rc.17 的近战整方法包装**（方向正确），
本批集中修投射物生命周期，未扩大功能范围。

### 1. 区分「从未记录」与「已失效/已消费」（核心修复）

**缺陷**：上一版在**换主**或**命中已消费**时**删除**记录。删除后的投射物与**从未见过**的无法区分，
于是下一次 `setOwner`（原版对同一支投射物会调用多次）把它当成**新箭**，
**赋予当前交战的代次** —— 旧箭的命中可能续期一场它从未参与的战斗。

**修复**：`ProjectileStamps` 改为**单向状态机 + 终态**：

- 首次合法建立后，**发射者、目标、代次不可被普通 `setOwner` 改写**。
- **同主重复赋值保持原记录**（含原始发射刻）。
- **换主 / 命中已消费 / 托管但当时无交战目标 → 终态 `INVALIDATED`，不可复活**
  （包括之后还给伙伴也不重新归因）。
- **交战结束不需要额外失效**：戳保留发射时代次，该代次失效后 `recordHitAtGeneration` 自然拒绝旧箭；
  清理路径只做内存回收，不削弱规则。
- **实体销毁时释放状态**，存储量与**存活投射物**成正比，**不使用无限增长的永久 UUID 黑名单**。
- 非伙伴主人**传入 null 而非提前 return**：跳过会让投射物保持「从未见过」，
  之后交回伙伴就会创建首次归因。

### 2. 由服务器启动抓到的真实缺陷（重要）

移除钩子最初注入 `ProjectileEntity` 的 `remove`，但
**`ProjectileEntity` 与 `PersistentProjectileEntity` 都没有声明该方法**（它来自 `Entity`），
因此注入无目标，`defaultRequire: 1` 让 Mixin **加载时直接报错中止**。
**这正是不复跑服务器就会把「启动即崩」的构建发出去的例子。**

修复：新增 `ProjectileRemovalStampMixin` 混入 `Entity` + `instanceof ProjectileEntity` 判断；
新增断言 `removeIsDeclaredOnEntityOnly`（`Entity` 必须声明、两个投射物类必须没有声明），
避免将来重写后钩子变成静默 no-op。复跑服务器：6 个 mixin 全部应用，零失败。

### 3. 真实调用顺序回归（`ProjectileOwnerLifecycleTest`，12 项）

全部走生产入口 `observeOwnerAssignment`（两个钩子共用的唯一解释器），需要处**先经生产清理**再赋值。
覆盖：换主后连续两次钩子仍失效；正式结束交战并清理后同目标重新接战旧箭不获新代次；
命中已消费后重复赋主不再授权；换玩家/空主人/未托管生物后再换回伙伴不能绕过；
**新箭仍正常归因且该场战斗命中确实计入进展**；实体销毁释放状态；无交战不打戳；多枚互不干扰；误中不计数。

### 4. 报告修正

- 「换主后双方都无法计入」改为**待验证**（规则与接线测试已通过，**实机未验证**）。
- 明确**日志判读要求**：**不要求完全没有 `engagement ended`** ——
  目标死亡、模式切换、召回都是合法结束原因；**只有 `NO_PROGRESS` 才说明修复未生效**。
- 远程描述分**赋主接线 / 规则接线测试 / 实际命中验证**三层；实际命中**未验证**，
  不写成全面支持。

### 5. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **191 用例 / 0 失败** |
| 服务器加载 | **通过** |
| 客户端加载 | **本版本未复验**（rc.15 曾通过） |
| 实际行为验收 | **未完成** |

---

## 1.0.0-rc.17

候选版本，**未发布正式 1.0.0**。**rc.16 不作为近战行为验收基线** —— 其攻击包装范围有误，本批修复。

### 1. 修复攻击包装范围（rc.16 的实际缺陷）

**缺陷**：rc.16 的 `@Redirect` 只替换了 `getVelocity()` 这一个调用，而它在方法体**最开头**。
因此真实包装范围是「打戳 → 读速度 → **finally 立刻执行、戳被清掉** → 计算伤害 → `damage()` → `postHit`」。
**戳在造成任何伤害之前就被释放**，近战命中**永远无法计入进展** —— 近战续期链路实际是断的。
rc.16 的测试只验证「finally 存在」，所以全绿。

**修复**：改用 **MixinExtras `@WrapMethod`**（项目已依赖 `mixinextras-fabric:0.5.5`）：

- **真正包住整个方法**，方法体在包装器 `try` 内运行一次，`finally` 覆盖**全部退出路径**。
- **原方法恰好执行一次**：通过 `original.call(...)`，**不在重定向内递归调用同一方法**。
- **异常原样传播**：`finally` 只清理，**没有 `catch`**，不包装、不吞掉。
- **运行时可验证**：服务器日志出现 `Renaming synthetic method
  lambda$noncreatureaffinity$withAttackStamp$0(... Operation; ...)` ——
  这条**只在 `@WrapMethod` 真正应用时产生**。

### 2. 能识别此错误的接线验证

不再只断言「handler 里有 finally」：

- `stampSurvivesUntilTheAttackReturns`：**攻击体运行期间戳仍存在**，且此时按**生产归因规则**可计入
  —— 这正是 rc.16 会失败之处。
- `hitAfterReturnIsRefused`：返回后戳消失，此后命中不被计入。
- `attributionRuleChecksTickAndGeneration`：归因同时校验**同刻 / 目标 / 代次**。
- `mixinWrapsTheWholeMethod`：**必须** `@WrapMethod`；**禁止** `@Redirect` 与 `@Inject(method`；
  必须用 `original.call(`；**禁止** `self.tryAttack` 递归。

### 3. 投射物标记生命周期修正

- **`setOwner` 不再被无条件当作重新发射**：同一伙伴再次赋主 → **保留原戳不变**
  （连同原始发射刻）；**换主 → 明确丢弃**（换主不是发射）。
  采纳传入的新代次正是这条机制要防的错误。
- **确认 `PersistentProjectileEntity#setOwner` 调用父类**（字节码 `invokespecial`），
  因此**两层注入对箭都会触发**；由**幂等规则**吸收，不会重复建立或覆盖。
  新增断言 `subclassDelegatesToTheSuperclass` 把这一点固定下来。
- **新增回归测试**：`reOwningInANewFightDoesNotMakeTheArrowCurrent`
  （旧箭在新交战中重复赋主，命中仍不续期）、`stampIsTakenOnce`、`changeOfOwnerDropsTheStamp`。
- **代价（明确）**：真正「重新发射同一支投射物」会保留旧戳，那次命中不续期。
  偏安全；真实重新发射会生成**新实体**，其自身 `setOwner` 取新戳。

### 4. 报告修正

- 「攻击异常清理已完成」改为 **「包装范围已修复，清理逻辑已实现并有测试；真实异常场景仍需实机」**。
- **rc.16 客户端加载**不再沿用旧版本结论，改记 **「本版本未复验，rc.15 曾通过」**。
- **远程描述分为三层**：赋主接线（已实现）/ 规则测试（已通过）/ 实际命中验证（**未验证**），
  **不写成全面支持**。

### 5. 验证分层

| 类别 | 状态 |
|---|---|
| 自动测试 | **192 用例 / 0 失败** |
| 服务器加载 | **通过**（`@WrapMethod` 生效，零注入失败） |
| 客户端加载 | **本版本未复验**（rc.15 曾通过） |
| 实际行为验收 | **未完成** |

---

## 1.0.0-rc.16

候选版本，**未发布正式 1.0.0**。本批实现**远程投射物发射标记**与**攻击异常清理的 finally 包装**，
并统一报告口径。**实际行为验收仍未完成**（需人工操作）。

### 1. 远程投射物发射标记（新功能）

此前投射物**没有标记**，其命中**不续期战斗** —— 对骷髅等远程伙伴不可靠，且被列为阻塞项。现已实现。

**发射路径（先确认再实现）**：经映射 jar 确认
`ArrowEntity extends PersistentProjectileEntity extends ProjectileEntity`，
**骷髅射箭走 `PersistentProjectileEntity.setOwner`**。因此新增两个 mixin：

- `ProjectileLaunchStampMixin` → `ProjectileEntity#setOwner`
- `PersistentProjectileLaunchStampMixin` → `PersistentProjectileEntity#setOwner`

**两个都需要**：子类自身实现了 `setOwner`，**只注入父类不会对箭生效** ——
这正是本项目在 `tryAttack` 上已经犯过一次的错误。

**绑定内容**：伙伴 UUID、目标 UUID、**发射时的交战代次** → 具体投射物
（`ProjectileStamps`，以投射物 UUID 为键的弱键表）。

**结算校验**：`onCompanionProjectileHit` 把**投射物上记录的发射代次**交给
`EngagementState.recordHitAtGeneration(...)`，与近战**共用同一处代次判定**。
**不读取命中时的当前代次冒充发射代次**。

**已覆盖路径**：骷髅的箭；其他 `ProjectileEntity` 子类的发射打戳。
**未覆盖**：三叉戟投掷形态、药水等不通过 `setOwner` 赋主的路径、模组自定义发射器 ——
这些**不会误续期，但也不续期**（偏安全，且是明确限制，不是「已全面支持远程」）。

新增测试：同场有效命中续期、旧代次同目标不续期、误中其他目标不续期、
多枚在途投射物互不覆盖、两伙伴互不干扰，以及契约测试确认
**发射代次确实被记录**（而非命中时读取）与两个 mixin 均已注册。

### 2. 攻击异常清理：真正的 finally，并验证异常路径

- 新增 `AttackStampHandler.runWithStamp(...)`，**真正的 try/finally**：
  正常返回、返回 false、抛 `Throwable` 都会清理。
- **不捕获、不包装、不吞掉任何异常**；测试**断言源码里没有 `catch (`**。
- mixin 改为 `@Redirect` 包住 `tryAttack` 方法体起点，因此**生产跑的就是测试驱动的那个 finally**。
- 新增 `AttackStampExceptionPathTest`：**异常路径有测试**（抛 `RuntimeException` / 抛 `Error` / 正常返回 / 返回 false）。
- **删除**「`RETURN` 在抛异常时也执行」的错误描述；`RETURN` 注入不再使用。
- 同刻校验保留为**补充防护**，**不再**声称它是异常路径的解法。

### 3. 报告与验收标准修正

- 统一为 **186 项测试 / 28 个测试类**；rc.15 服务端与客户端加载已完成，**实际行为验收未完成**。
- **修正验收标准**：**漫游仍应保护主人并协助攻击已有敌意的目标**；
  验证「不再替主人参战、停止追击」**必须切换为「等待」**，不能用漫游。

---

## 1.0.0-rc.15

候选版本，**未发布正式 1.0.0**。本批按交接清单完成 1–3 项，并**首次完成客户端加载验收**。

### 1. 拆开「呼吸让路」与「战斗控制」

**缺陷**：`controlsCompanion` 同时承担两件事 —— 本 Goal 能否运行，
**以及**原版 `FollowOwnerGoal` / `TrackTargetGoal` 的闸门条件。
低氧让路使它返回 false，于是**闸门一起失效**：
快淹死的伙伴会**重新获得原版跟随主人、原版报复/选敌**的行为 ——
「让它上浮」变成了「让它变回原版宠物」。

**修复**：拆成两个**纯规则**谓词（可脱离世界、实体与 GoalSelector 测试）：

- `AirSupply.isManaged(...)` —— **签名里没有任何空气参数**，闸门只由它驱动。
- `AirSupply.mayControlMovement(managed, mustYieldForAir)` —— 本 Goal 是否可运行。
- `gate(...)` 改用 `!isManagedCompanion(mob)` 作为 `ConditionalGoal` 许可条件。
- `tick()` 增加显式守卫，让「让路时不操纵移动」不依赖选择器是否先调 `shouldContinue`。

**效果**：低氧时只释放**移动**；WAIT 仍不追敌，WANDER 仍不跟随主人。

### 2. 攻击标记生命周期三处修正

1. **删除错误描述**：`@At("RETURN")` 在**返回指令**上，属正常控制流，
   **异常传播不经过它**。此前注释声称「抛异常也会清除」是**错的**。
2. **不假装解决异常路径**：实测 `MobEntity#tryAttack` **只有 1 个返回指令、没有异常表**。
   本批**没有**用 try/finally 包装，也**没有**吞掉异常。替代做法是新增
   `launchedTick == nowTick` 约束：**非当前刻的戳一律不认**。
   一旦真有异常把戳留下，它**下一 tick 即自动失效**，不必等一个可能永不到来的清理信号。
3. **`clearAll()` 修复**：原来只清标记表，令牌表 `TOKENS` **未清**。已一并清空，
   并新增 `tokenCount()` 与断言。

### 3. **新发现并修掉：mixin 打在错误的类上（严重）**

原 `@Mixin(LivingEntity.class)`，但反汇编显示
`LivingEntity#tryAttack(ServerWorld, Entity)` 是**空壳**（只调 `onAttacking` 后 `return false`），
**真正的攻击主体在 `MobEntity#tryAttack`**。
即：**对任何生物都从未打过戳，迟到命中检查整体失效**，而**所有单元测试仍然全过**。

**修复**：改为 `@Mixin(MobEntity.class)`；新增断言用**字节码长度**区分空壳与真身
（`LivingEntity` 版 6 字节 vs `MobEntity` 版数百字节），使「打在错误类上」不再静默复发。

### 4. 验收说明修正

- 模式切换写明**「潜行（蹲下）＋ 主手空手右键」**，说明**必须潜行**
  （代码在 `!isSneaking()` 时 `PASS`，普通右键保留骑乘/坐下等原版交互），
  以及真实顺序 **跟随 → 漫游 → 等待 → 主动 → 跟随**（按 3 次到「主动」）。
- 「敌对生物 / 狼 / 猫」改为**区分**：「代码白名单返回 true」是**代码事实**；
  「实机追击并命中」**未验证**。**不得**把「在白名单里」写成「行为已验证」。
- 远程命中不续期改为**阻塞项**：若正式版继续支持骷髅等远程伙伴，
  **必须在 1.0.0 之前**为投射物建立可校验的发射戳，**不得**仅凭「列为已知限制」就算完成行为目标。

### 5. 验证分层（本批明确区分）

| 类别 | 状态 |
|---|---|
| 自动测试 | **165 用例 / 0 失败**（rc.14 为 158） |
| 服务器加载 | **通过**（`MobEntity` 注入两端均成功，零失败） |
| **客户端加载** | **首次通过**（进入 devworld、玩家登录、本模组零 ERROR） |
| 实际行为验收 | **未做**（需人工操作） |

**自动测试通过 ≠ 服务器能加载 ≠ 客户端能加载 ≠ 行为正确。**

---

## 1.0.0-rc.14

候选版本，**未发布正式 1.0.0**。本批按交接清单核对并修正四项，**首次启动专用服务器做加载验收**。

### 1. RecallFlow：接口与「是否已返回」的判断都修正了

**缺陷一（接口与文档不符）**：报告写的是「两方法的适配接口」，实际是**单方法** `@FunctionalInterface`。

**缺陷二（真正的逻辑缺陷）**：`attempt` 用**瞬移前**的 `distanceToOwner` 判断 `alreadyBack`，
而 `recallDue` 要求该距离**大于**召回距离 —— 所以 `alreadyBack` **在生产环境永远是 false，是死分支**。
测试替身恒返回 true，把它掩盖了。

**修复**：

- 适配器改为返回 `RecallOutcome(recalled, distanceAfterRecall)` —— **只报告它真正观测到的落点距离**。
- `CompanionRecall.tryRecall` 改为返回**落点 `Vec3d`**（找不到安全点返回 null），
  生产侧由**落点**算「召回后到主人的距离」，不再复用瞬移前的采样。
- 无法测量落点时**按「尚未返回」处理**（保守方向，宁可多返回一段，也不放它立刻冲回去）。
- **统一召回资格规则**：`recallDue(...)` 是唯一实现，生产侧与流程内部都走它，不再各写一份。
  规则含**三项**条件：主人可用（**存活且非旁观**）、同维度、距离**严格大于**召回距离。
- `RecallContext` 在构造时**拒绝**「ownerId 为 null 却说 ownerUsable 为 true」的矛盾组合。

顺带发现并修掉：`recallDue(context)` 只读 `ownerId`、**忽略 `ownerUsable`**，
于是「主人已死亡/旁观」会被当成可召回。这是新测试用例暴露的，不是读代码看出来的。

**保留**：成功恰好一次端口调用、失败不发生任何成功转换。

### 2. 攻击标记生命周期：近战失败不再遗留标记，命中核对伤害来源

**缺陷一（近战失败遗留标记）**：标记只在 `tryAttack` 的 **HEAD** 打上，**没有任何地方在调用结束时清除**。
一次**没打中**的近战会留下标记，之后**任何**落到同一目标的伤害
（典型是它先前射出的箭终于命中）都会被算作那次已结束的攻击，**错误续期**。

**修复**：新增 `MeleeAttackStamps`，并用 **HEAD + RETURN 两端**把标记**限定在攻击调用期间**；
RETURN 是**返回边**，所以**命中、未命中、抛异常都会清除**。

**缺陷二（投射物误用近战标记）**：命中只核对「攻击者 + 目标」，**没有核对伤害来源**。

**修复**：`AFTER_DAMAGE` 现在传入 `source.getSource() == attacker`（是否**直接近战**），
`onCompanionHit` 要求**直接攻击 + 标记存在 + 目标一致 + 代次仍有效**，四项全中才续期。

**新增回归测试（交接点名的两条）**：

- `failedMeleeCannotCreditALaterProjectile` —— **近战失败后，旧投射物命中同目标**。
- `oldProjectileCannotRenewANewFightOnTheSameTarget` —— **同目标重新接战后，旧投射物命中**。

**限制（明确，不声称已全面解决）**：投射物**不走 `tryAttack`**，因此**没有标记**，
它的命中**不会**续期战斗。这是偏安全的方向，但**「远程命中不续期」是当前真实限制**。

### 3. 游泳与呼吸：风险面比报告更大，已按实际控制位修正

反汇编**实际映射 jar** 得到的确定事实（不是只凭报告）：

| 原版 Goal | 占用的控制位 |
|---|---|
| `SwimGoal` | **只有 `JUMP`**（`getJumpControl().setActive()`） |
| `BreatheAirGoal` | **`MOVE`**（+ `LOOK`） |
| 我们的 `CompanionMovementGoal` | **`MOVE` + `JUMP`** |

三者都是 **priority 0**。因此**两个**原版 Goal 都会在被压制：不只 `SwimGoal`，
**`BreatheAirGoal` 才是与我们 `MOVE` 冲突的那个** —— 交接清单这一点是对的。

**修复**：新增纯规则 `AirSupply.mustYieldForAir(...)`，在 `controlsCompanion` 里让路。
规则**刻意收窄**：只在**水中且空气低于阈值**（物种最大值的 1/4 与 100 取小）时让路；
**陆地永不让路**（保住「等待不追击」），**水生生物永不让路**（它需要的是水不是空气）。

### 4. 加载验收：**首次启动专用服务器**

用 `runServer` 启动 1.21.11 专用服务器（项目 `run/` 目录，**未安装到游戏 mods**）。
日志确证：

```
Mixing CompanionAttackStampMixin from noncreatureaffinity.mixins.json into net.minecraft.entity.LivingEntity
  -> @Inject::noncreatureaffinity$stampAttackLaunch(...)  has 0 override(s)
  -> @Inject::noncreatureaffinity$clearAttackStamp(...)   has 0 override(s)
Mixing EntityAffinityMixin ... into net.minecraft.entity.Entity
Mixing MobGoalAccess ... into net.minecraft.entity.mob.MobEntity
```

**零 mixin 失败 / 零注入错误 / 本模组零 ERROR**，服务器 `Done (1.600s)!`。

**这证明了**：两个注入点都在**真实 `LivingEntity#tryAttack`** 上成功应用 ——
即单元测试**无法**覆盖的「锚点是否真实存在」这一刻。**这仍然不是行为验收**：
没有客户端、没有狼、没有实际战斗。

> 说明：为验证加载，服务器在项目 `run/` 目录下启动并自动保存过该开发用世界。
> **没有触碰任何玩家存档，也没有安装到游戏 mods 目录。**

### 测试

- 新增：`MeleeAttackStampsTest`、`AirSupplyTest`；重写 `RecallFlowTest`；
  扩展 `AttackStampMixinContractTest`（HEAD+RETURN 两端、控制位声明）。
- **仍未做的游戏行为验收**：见 `STATUS-rc14.md` 的人工测试清单。

---

## 1.0.0-rc.13

候选版本，**未发布正式 1.0.0**。本批完成 rc.12 摘要中列出的未完成项，并修掉过程中发现的三个新缺陷。

### 1. 停滞退出走完整路径（rc.12 第三步）

**缺陷**：停滞分支只结束交战、**不建立抑制**，而且**本轮已经选出的同一个目标会被立刻写回去**。等于停滞退出没有效果，伙伴会反复走回打不到的战斗。

**修复**：

- 新增 `EngagementState.suppressStalledTarget(...)`：停滞退出走**自己的**抑制路径。
- **它不共用召回的事件水位**。召回是「新决定要回去」，所以更新的敌意事实可以推翻它；停滞**不是决定**，只是这场战斗没打成。而打不到的敌人每秒都会被敌意扫描重新发布一次，**任何基于水位的抑制都会被下一次扫描推翻**。所以停滞窗口只认时钟：窗口内**任何事实都不能重开这个目标**。
- 新增 `selectionSurvivesRun(...)`：**回验本轮已经做出的 Selection**。只丢掉被抑制的那一个目标，**其他威胁照常响应**（不会因为一场陈旧战斗而放弃本轮的新目标）。
- 抑制到期时**连同水位一起清除**——详见下面的新缺陷 (b)。

### 2. 新增缺陷 (a)：停滞退出的「原因」曾把已放弃的战斗复活

第一版实现为了让「本次退出是停滞」可被观测，把交战记录**重新写成一场新交战**。但 `begin()` 会重设进度时钟，于是记录看起来像**一场刚开始且已经停滞**的战斗，而保活分支判定的正是这个字段。

**修复**：退出原因改为独立的 `lastEngagementExitWasStall` 字段，**记录保持无交战状态**，`engagedTarget` 保持为空。

### 3. 新增缺陷 (b)：停滞窗口到期后**永久屏蔽**该目标

停滞窗口的水位是 `Long.MAX_VALUE`。到期后 `suppressedTarget` 被清除，但**水位留在原地**，于是 `sequence <= suppressedSequence` 对**任何**事实都成立 —— 该目标在**整个服务器会话内再也不会被选中**，表现为「伙伴再也不打某个敌人」。

**修复**：`isSuppressedAt` 在窗口关闭时**一并清除水位与停滞标记**；水位判定也**先看时钟**。这正是 `StallExitRulesTest` 新断言抓出来的。

### 4. 迟到命中按代次校验（rc.12 未完成项）

新增 `CompanionAttackStampMixin`：在 `LivingEntity#tryAttack` 的 **HEAD** 打上**本次交战的代次**戳，命中时用 `recordHitAtGeneration(...)` 校验。

**为什么必须打在发起时**：`AFTER_DAMAGE` 是**结算时**触发的。近战同刻结算，但投射物要过很多刻才命中，那时代次早就变了 —— 在那里打戳只会读到当时的代次，**等于什么都没验证**。

**范围（明确）**：只覆盖**近战**。投射物由远程目标生成，**不走 `tryAttack`**，所以它的命中**不会**被计为进展。这是偏安全的方向，但**「远程命中不再续期」是当前的真实限制**，不是未验证的说法。

### 5. 薄适配层 + 测试替身（rc.12 第四步）

新增 `RecallFlow`：把「是否该召回」和「传送是否发生」收敛成一个**两方法的适配接口**，生产侧只有一处实现（仍调用 `CompanionRecall`，距离规则**单一来源**）。成功与失败回调经替身验证：**成功恰好一次、失败一次都不执行**。

同时删掉了 `onRecalled` 内重复的 `applyRecallResult` 调用 —— 它与新增的流程**重复执行了成功路径**。

### 6. P2-1：界面可编辑但行为无效的两个半径

- `companionWaitRadius`：**恢复行为**（`driveAnchor` 里原本硬编码 `0.5`，滑块移动的是一个没人读的数字）。
- `companionWanderRadius`：**移除**。WANDER 已完全交给生物自身 AI、不再拉回锚点，**这个半径没有任何行为可挂**。同时在配置界面补上 `long` 类型滑块，让 5 个「刻」长选项（含本次新增项）真正可编辑。

### 7. P2-2：语言审计改为**动态**推导

`tools/lang-audit.mjs` 现在直接读配置界面的 `VISIBLE` 列表，逐项检查选项名与说明在两种语言中都存在；新增 JUnit `ConfigLanguageAuditTest` 用反射读 `AffinityConfig` 字段与界面列表做同样的检查，并**断言没有孤儿键**。顺带清掉了 2 个描述**已删除字段**的孤儿键（`debugFontProbe`）。

### 测试

- 新增：`StallExitRulesTest`、`CompanionStallWiringTest`（接线层）、`RecallFlowTest`（调用链替身）、`CompanionEventStoreFlowTest`（双伙伴共用一份事实）、`AttackStampMixinContractTest`（注入目标契约）、`ConfigLanguageAuditTest`。
- 语言审计：**89 个玩家可见键，0 缺失**；配置界面 **34 项 / 68 键，0 缺失**。
- **仍未做游戏验收**：本批**没有启动客户端**。

---

## 1.0.0-rc.5

修复用户实测反馈的三点。

### 1. 挣脱仍然增加好感（rc.4 的修复没生效）

**原因**：我把挣脱标记放在了 `AFTER_DAMAGE` 回调里，**但在 `taken <= 0` 的提前返回之后**：

```java
if (taken <= 0 || ...) return;   // ← 被护甲/无敌帧吸收的伤害在这里就返回了
...
NonBridge.markEscaped(...)        // ← 永远到不了
```

所以玩家攻击被护甲挡住时，**挣脱标记根本没写上**，动画停止后仍然照常结算。

**修复**：标记移到 `ALLOW_DAMAGE`（**伤害被允许时即标记**，与最终伤害值无关）。

### 2. 界面显示原始键名 `tier.noncreatureaffinity.stranger`

**原因**：这些键是**动态拼接**的（`"tier.noncreatureaffinity." + word`），
而我的语言审计只扫描字面量，**从未检测到它们** —— 加上语言文件里**根本没有这 6 个键**，
界面就直接显示了键名。

**修复**：补齐全部 6 个档位键

| 键 | 中文 |
|---|---|
| `tier.noncreatureaffinity.new` | 初次见面 |
| `tier.noncreatureaffinity.stranger` | 陌生 |
| `tier.noncreatureaffinity.familiar` | 熟悉 |
| `tier.noncreatureaffinity.friendly` | 亲近 |
| `tier.noncreatureaffinity.intimate` | 亲密 |
| `tier.noncreatureaffinity.full` | 挚友 |

**并且修补审计脚本**：现在会显式检查这批动态拼接的键，
不会再有同类键「审计通过但实际缺失」。

### 3. 无法触发时不增减好感

按要求明确：**因无法触发（如入水）导致交配没能开始，既不增加也不扣除好感**。

核对现有路径，符合要求：

| 路径 | 好感 |
|---|---|
| 完成整场交配 | 增加 |
| 中途被挣脱／打断 | **不增加**（rc.4 + 本次修复） |
| 距离太远无法开始 | 不增减 |
| 没有动画／条件不允许 | 不增减 |
| 求欢超时未回应 | 扣（唯一因「没完成」而扣的路径） |
| 主动拒绝 | 扣 |

### 验证状态

| 状态 | 内容 |
|---|---|
| 源码已改 | 是 |
| 自动化测试通过 | **53 个 JUnit 用例** + **89 键语言审计 0 缺失** |
| 游戏行为验证通过 | **尚无** —— 仅客户端启动冒烟 |
---
---

## 1.0.0-rc.4

修复用户报告的问题：**强行交配被挣脱后仍会增加好感**。

### 原因

强制交配被玩家攻击挣脱时会触发动画的 STOPPED 事件，
而**正常完成**与**被中断**走的是**同一个事件** —— 二者在这一层无法区分。
原实现在 STOPPED 里无条件结算，所以挣脱也会发放好感。

### 修复

从**伤害路径**记录「挣脱」，在结算时跳过：

1. `NonIntegration` 新增 `ESCAPED` 集合与 `markEscaped(player, creature)`
2. 动画 STOPPED 时，若该实例被标记为挣脱 → **跳过 `recordMating`**，不增加好感
3. 伤害事件（`CompanionDamageEvents`）中，玩家攻击正在交配的生物即标记挣脱
4. 经 `NonBridge` 门面转发（`NonIntegration` 是包私有类）

**按用户要求的通则实现**：标记的是**任何被中断的交配**，不只是强制交配 ——
因为规则是「只有在完整进行一次交配之后才增加好感」。

日志会记录：`mating <instance> was escaped; no affinity granted`。

### 说明

- 自愿交配若被伤害打断，同样不结算（与通则一致）
- **没有**改动强行交配本身的触发条件、挣脱方式或冷却
- 配置 `forcedMatingEscapable` 行为不变

### 验证状态

| 状态 | 内容 |
|---|---|
| 源码已改 | 是 |
| 自动化测试通过 | **53 个 JUnit 用例**（构建时运行） |
| 游戏行为验证通过 | **尚无** —— 只做了客户端启动冒烟（加载 rc.4、进世界无异常） |

**本条修复需要实机确认**：挣脱后好感不应增加，且完整完成一次交配后好感仍应正常增加。

---

## 1.0.0-rc.3

**本轮由 Codex 直接修改项目**（用户授权），DSH 未参与该版本的代码改动。

### 主要内容

| 文件 | 改动 |
|---|---|
| `service/ConditionalGoal.java` | 条件包装原版 Goal；禁用后不能启动/继续/tick，恢复后还原 |
| `service/CompanionMovementGoal.java` | 门控原版主人跟随 Goal 与 TrackTargetGoal 类目标选择 |
| `mixin/MobGoalAccess.java` | 增加 targetSelector 字段 accessor |
| `service/CompanionRecall.java` | 超过 10 格尝试安全召回 |
| `CompanionCombatController` / `CompanionDamageEvents` / `NonCreatureAffinity` | 模式命令取消旧目标、主人身份校验、卸载与停服清理 |

### 验证（Codex 记录）

- **53 个 JUnit 用例，0 失败**（新增 ConditionalGoalTest 5 项、CompanionRecallTest 2 项）
- 已解包最终 jar 核对版本、内部 ID、新增类与 mixin 配置
- **未启动 rc.3 游戏或独立服务器**

### DSH 的静态检查发现

`CompanionMovementGoal` **优先级 0 且插入最前**，控制 **MOVE + JUMP**；
原版 `SwimGoal` 同为**优先级 0** 且控制 **JUMP**（已用字节码确认）。
`GoalSelector` 同优先级内按注册顺序占用控制位，因此**跟随/等待模式下原版游泳可能被压掉**，
伙伴入水可能不上浮。**需实机确认**（正是 Codex 要求检查的「水边、击退、狭窄处」场景）。

**DSH 未修改 rc.3 的任何代码**，仅做上述静态检查。
---
## 1.0.0-rc.2

按用户要求调整求欢请求的文案、距离反馈与超时代价。

### 1. 求欢文案

`message.noncreatureaffinity.request` 改为：

```
❤ %s 想和你继续一次深入交流  [同意]  [拒绝]
```

聊天栏消息、HUD 提示、以及「生物在等你回答」的提示**三处同步**，不会一处新一处旧。

### 2. 同意后距离太远 → 明确提示

原来所有失败都走同一条 `cannot_start_now`。现在**在消耗请求之前**先检查距离：

| 情况 | 提示 |
|---|---|
| **超出求欢距离**（默认 8 格） | **「太远了，靠近些吧」** |
| 没有可用动画 / 条件不满足 | **保持原有提示**（`cannot_start_now`） |

**关键点：距离不足时请求不会被消耗** —— 你可以走近后对**同一个请求**再次同意，
而不是被静默吞掉一次已经做出的同意。

### 3. 超时未回应会扣好感

原来超时**只进入冷却，不扣任何好感**（注释写着 "never treated as a rejection"）。
现按新要求改为**超时扣好感**。

**并且只有真正的超时才扣**：

| 路径 | 是否扣好感 |
|---|---|
| **等待时间结束仍未回应** | ✅ **扣**（新配置 `affinityLossOnTimeout`，默认 10） |
| 距离太远无法开始 | ❌ **不扣** |
| 没有动画 / 条件不允许 | ❌ **不扣** |
| 主动拒绝 | 走原有 `affinityLossOnReject`，与本项无关 |

等待时间**保持 30 秒**（`requestTimeoutTicks = 600`，20 tick = 1 秒），
确保有足够时间反应。配置说明已同步更新。

### 新增配置

`affinityLossOnTimeout`（默认 10，范围 0–100）已加入配置界面，紧邻「拒绝求欢扣除好感」。

### 语言文件

新增 `too_far`、`affinityLossOnTimeout` 两个键并同步中英，**审计通过（83 键，0 缺失）**。
---
## 1.0.0-rc.1

按《1.0.0 首版目标与验收要求》接入交接的四模式核心。**候选版，未改正式 1.0.0。**

### 三处定义变更（非沿用旧实现）

| 项 | 旧 | 新 |
|---|---|---|
| 模式循环 | 跟随→等待→漫游→主动 | **跟随→漫游→等待→主动** |
| 漫游 | 锚点周围 12 格 | **保留生物原有自然移动，不绑定下令点** |
| 等待 | 可自卫逃生 | **不参战、不追击、仅原地自卫** |

模式循环**委托给交接策略**，两处定义不可能再漂移。

### 接入的交接策略

`CompanionCombatPolicy`（纯决策、无游戏依赖）原样接入，**70 条断言全部通过**。
按 README 要求**独立计数**（包装为 1 个 JUnit 用例，不与项目用例相加）。

### 新增核心类

| 类 | 职责 |
|---|---|
| `CompanionEventLog` | 服务端战斗事实记录；**按玩家存储**，多个伙伴各自消费；序号会话内单调、重启归零 |
| `CompanionCombatController` | **唯一的目标决策与写入入口**；三种动作执行器；敌意扫描 |

### 三种动作的限制

- `DEFEND_IN_PLACE`（等待）：设置目标但**抑制移动**，离开射程即停；**回位导航永不指向攻击者坐标**
- `IGNORE`：**不是清空一切目标**，只释放本模组拥有的
- `ENGAGE`：只表示规则允许

伤害回调**只记录事实、不写目标**。

### 能力门控

`hasCombatCapability` 为**白名单**：狼、`HostileEntity`、猫允许；
**马等无攻击 AI 生物返回 `UNSUPPORTED_COMBAT`**，不假装战斗。

### 未实测

四模式实际行为、狼的闭环、马的真实位移、攻击是否命中**全部未实测**。
交付 `MANUAL-TEST-1.0.0-rc.1.md` 逐项手测清单（A–K 共 11 组），**rc 状态在通过前不改正式号**。
---
## 0.14.2

按分步方案实施**批次 A：目标责任与外部目标保护**。

### A-1：旧护卫绕过主动模式约束（规则绕过路径）

护卫**先于**伙伴服务执行，且**仍会处理已绑定伙伴**（含 ATTACK 模式）。它把目标登记为
`LEGACY_DEFENDER` 后，主动服务把该目标视为「非本来源的存活目标」而让路 ——
**于是该次追击不再受伙伴自己的 `companionAttackLeashRadius` 与主动退出规则约束**。

**修复**：按职责分流。

| 实体 | 由谁管理 |
|---|---|
| **有有效 companionOwner** | **伙伴控制器**；旧护卫**不再写入**其目标，改为交还自己仍拥有的记录 |
| 无伙伴绑定但符合旧护卫条件 | 旧护卫控制器 |
| 归属冲突 | 不选择主人或新目标，只受控撤销本模组仍拥有的控制 |

分流**不是简单 skip** —— 交接时旧来源先释放自己仍拥有的目标，不会把旧目标永远留下。

### A-2：护卫仍能覆盖外部目标

护卫重新读取 current 后**没有「存活外部目标就让路」的分支**，若 `findThreat` 找到目标仍会
`setTarget` —— **静默抢走其他系统的目标**。

**修复**：存活且**不由本服务拥有**的目标 → 撤销自身的失效登记并**让路结束本轮**，不再往下写入。

### A-3：护卫未遵守占用规则

伙伴服务会让路（骑乘/拴绳/坐下/动画），但**旧护卫可以先写入目标**。
**修复**：护卫路径加上同一组占用检查，让路前释放自己拥有的记录。

### A-4：配置运行中关闭时的清理

护卫**只在 `friendlyMobsDefendPlayer=true` 时被调用**，所以把清理放在
`isDefender=false` 分支**无法处理运行中关掉开关**的情况 —— 该服务根本不再进入。

**修复**：新增 `releaseAllOwned`，在**功能关闭时以同样频率运行轻量 reconcile**，
只释放 `LEGACY_DEFENDER` 自己的目标，**不影响 `COMPANION_ATTACK`**。

### 新增测试（43 项）

补充来源隔离断言：**同一目标 UUID 在不同来源下不可互相清理**，
以及两个来源枚举保持显式区分（防止将来被合并回一个无名值）。

### 仍未处理

- 批次 B：`FollowState`（路径所有权、重算/退避、PROGRESS/STALLED 日志）
- 批次 C：持续威胁时间窗、ownerId 用于所有权变化校验、生命周期回收、HUD 极窄边界、射线遮挡
- `CombatCoordinator` 统一决策入口（本轮用职责分流消除规则绕过，
  两服务的写入仍未收敛到单一入口）
---
---

## 0.14.1

修 0.14.0 **我自己引入的**控制冲突，并修正一处误导性的日志措辞。

### 最高优先级：两个服务共用登记却互相清理目标

0.14.0 我把目标登记抽成共享的 `TargetRegistry`，**但只记「实体 → 目标」，不记来源**。加上实际调度顺序：

```
护卫每 20 tick 先执行  → 设置并登记目标
伙伴服务每 10 tick 随后 → 「非 ATTACK 且有任何登记」就 disengage → 清掉护卫的目标
```

**所以护卫刚设的目标会在同一个世界 tick 被伙伴服务清掉。** 反方向同理。

这是我的设计缺陷，不是原版 AI 的问题。

**修复：登记必须带来源。**

```java
enum TargetSource { COMPANION_ATTACK, LEGACY_DEFENDER }
record Assignment(UUID targetId, UUID ownerId, TargetSource source, long assignedTick)
```

接口现在全部**按来源限定作用域**：

| 操作 | 约束 |
|---|---|
| `claim` | 必须写明来源与主人 |
| `owns` / `releaseIfOwned` / `forget` | 必须**同时匹配来源**与目标 UUID |
| 伙伴服务的非主动模式 | **只释放 `COMPANION_ATTACK`** |
| 未绑定伙伴的分支 | **不得释放 `LEGACY_DEFENDER`** |
| 护卫资格变化 | **只清 `LEGACY_DEFENDER`**，不替主动服务决定 |
| 外部目标不一致或为空 | **立即撤销**旧登记，不留到以后再次匹配 |

### 外部目标让路规则补完

原实现识别外部目标后 `forget + release`，**但继续往下搜索并 `assignTarget`** ——
注释说让路，实际会马上覆盖外部目标。

现在：

- 若存在**存活的外部目标**，本轮**直接让路结束**，不再立即抢回
- `releaseIfOwned` 后**重新读取** `mob.getTarget()`，不假定已被清空
  （护卫侧原来无条件把局部变量设为 null，会丢失真实的上一个目标并可能覆盖外部目标）
- `current == null` 时也进入撤销分支

### 日志措辞修正（这处措辞此前夸大了事实）

`startMovingTo` 返回 true **只代表导航接受了请求**，而日志写的是 **RECOVERED** ——
读起来像「已经恢复跟随」。实测日志里马有 **19 条 RECOVERED**，但同一时期马**并没有接近主人**
（主人移到 80~94 格外）。

现改为如实描述观测到的状态：

| 日志 | 含义 |
|---|---|
| `PATH_ACCEPTED` | 导航接受请求（原 RECOVERED） |
| `PATH_REJECTED` | 本次请求未获接受（原 no path） |
| `ARRIVED` | **实际进入停止半径** —— 这才是行为目标 |

### 仍未处理（工作单第七节的旧项）

- **P2 持续威胁**：仍未引入以服务器 tick 计的伤害有效期
- **P2 射线遮挡**：检测器备用射线仍未用最近方块命中约束终点
- **P2 极窄 HUD**：图标 + 间隔 + 超长数值本身仍可能超过可用宽度，缺最小宽度检查
- **P2 记录回收**：`TargetRegistry` 静态 Map 仍只在停服时整体清空，
  卸载实体的记录可能累积
- 工作单建议的 **CombatCoordinator**（两服务只提交候选、由单一入口决策）尚未实现 ——
  本轮先做来源隔离以消除误清理，双重写入问题留待下一步

### 报告数据修正

工作单指出报告顶部写 36 项、底部写 41 项不一致，且动画定义 368/370 两处不一致。
已在报告中统一为 **41 项测试**，并把动画定义统一为**实测的 368 条**，注明差异来源尚需包清单证据。
---
---

## 0.14.0

按最新工作单实施：显示需求 + P1 三项 + P2 一项。**不覆盖回旧版完整类**，只在 0.13.2 上增量修改。

### 显示：超过六个后显示真实总数

| 累计友情之证 | 显示 |
|---|---|
| 0 | 保留原有零数量显示 |
| 1~6 | 对应数量图标，**不追加数字** |
| 7 / 8 / 12 / 100 | 六个的图标 + 右侧 **7个 / 8个 / 12个 / 100个** |

- 数字是**实际累计总数**，不是总数减六、不是「6+」、不是背包持有量
- 图标索引固定为 `min(6, tokens)`，仅 `tokens > 6` 时生成后缀
- 后缀走语言键 `panel_token_count`（中文 `%s个`），**中英同步并已通过语言审计**（82 键）
- **先测量完整后缀宽度，再分配左侧标签空间**，`tokenRowWidth` 已含后缀与间隔
- 底行顺序：**友情之证 → 图标 → XX个**，间隔 4 GUI 像素，垂直居中，整行右对齐
- **保持已确认正常的图标效果**（16×16 整图采样、位置、风格均未改动）

### P1：主动切回跟随遗漏清理

原来只有 WAIT/WANDER 分支会结束本模块的交战，**ATTACK → FOLLOW 时目标会继续被沿用**
（FOLLOW 遇到存活目标只释放导航便 continue）。

现改为：**除 ATTACK 外的所有模式**，在开始自身逻辑前统一结束本模块的交战，
且只清除**仍匹配登记 UUID** 的目标。

### P1：护卫系统缺少目标生命周期管理

原来护卫**不记录**自己设置的目标：主人不可用时直接 continue，
目标失效时也不会清理自己设过的目标。

现抽出共享的 **`TargetRegistry`**（伙伴模式与护卫系统共用）：

- 记录**伙伴 UUID → 目标 UUID**，只有当前目标仍等于登记目标才算拥有
- `releaseIfOwned` 只清除自己拥有的目标；发现**外部替换**时立刻撤销旧登记并让路
- 护卫在**失去护卫资格 / 主人不可用 / 目标变友军或死亡**时受控清理
- 两个系统使用**同一份登记**，不会互相清理/重设同一实体的目标

### P1 诊断：马的 18 次寻路失败

原来只记录生物**名字**，无法区分墙体、高差、距离、被其他行为占用，或**多匹同名马**。

现在失败与**恢复**分别记录，并携带完整上下文：

```
no path to owner for 马 uuid=<uuid> failed at mode=FOLLOW dim=minecraft:overworld
    mob=(x,y,z) owner=(x,y,z) dist=.. start=6.0 stop=3.0 navIdle=.. occupied=..
path to owner RECOVERED for 马 uuid=<uuid> after failures at ...
```

**恢复单独记录**，使「临时失败」与「持续失败」可区分。
按工作单要求，**没有**靠永久压制日志或加瞬移来掩盖问题。

### P2：外部替换目标后旧登记未及时撤销

`stillOwns` 已避免把 B 当成 A，但匹配失败后**没有立即删除 A 的登记**。
现在一旦检测到外部替换，**立刻撤销旧登记**并对外部目标让路，
避免旧登记在目标重新变回 A 时被误认为仍持续拥有。

### 尚未处理（如实标注）

- **P2 持续威胁规则**：仍未引入「服务端 tick 为单位的伤害有效期」，
  持续保留条件仍是「活着且非受保护对象」，未重新确认「是否仍威胁主人」
- **P2 检测器备用射线遮挡**：仍直接做 32 格实体射线，未以最近方块命中约束终点
- 工作单要求更新的 **368/370 数量说明**已在给 GPT 的报告中修正

### 我在本轮引入并当场发现的问题

写导航诊断时用了 `mob.isNavigating()` —— 该 API 不存在，编译立即报错，已改用已验证的
`getNavigation().isIdle()`。**这是「凭直觉写 API」的又一次实例**，
好在编译探针/编译本身挡住了，没有进入交付。
---
---

## 0.13.2

按复审实施：HUD 图标修正 + 源码 P1 三项 + 一处 P2。

### 界面：图标显示为「零碎像素」的真正原因

我之前用的是**采样区域**重载：

```java
drawTexture(pipeline, texture, x, y, u, v, 9, 9, 32, 32)
//                                          ↑绘制尺寸  ↑贴图尺寸
```

这个重载的第 8/9 个参数是**采样区域**，不是绘制尺寸 —— 所以它从 32×32 贴图上取了
**左上角 9×9 的一小块**，而不是把整图缩小。截图里的碎片就是这一小块放大后的结果。

**修正**：改用含区域尺寸的重载，把「绘制尺寸」与「采样区域」分开：

```java
drawTexture(pipeline, texture, x, y, u, v,
        16, 16,    // 绘制到屏幕的尺寸
        32, 32,    // 从贴图采样的区域
        32, 32);   // 贴图实际尺寸
```

同时按复审的意见调整：

| 项 | 改动 |
|---|---|
| 图标尺寸 | 9 → **16×16**（完整显示 32×32 素材） |
| 内边距 | 8 → **6** |
| 底行高度 | 按 `max(文字行高, 16)` 计算，放大图标后不会与文字重叠 |
| 零数量 | **显示数字 0**，不再强行显示一袋 |
| 窄窗口 | 移除 `max(80, …)` 造成的越界；**极端尺寸下整块面板隐藏**，正常窗口保持显示 |
| 友情之证行文字 | 按剩余宽度截断 |

**未改动**：物品素材、背包图标、其他功能。

### 源码 P1-1：目标所有权记录不够精确

原实现只记录「伙伴 UUID」，未记录目标 UUID。若原版或其他模块把目标从 A 换成 B，
本模块仍把 B 当成自己设的，可能**错误保留或清除**。

现改为记录 **伙伴 UUID → 目标 UUID**，只有**当前目标仍等于登记目标**时才算拥有：

```java
static boolean stillOwns(UUID registered, UUID current)   // 纯函数，已单测
```

### 源码 P1-2：退出主动模式时没有完整结束战斗

原来各分支只调用 `release`（仅处理导航），**不清理本模块设置的攻击目标** ——
离开主动模式后，目标会继续交给原版战斗 AI 追击。

现建立**统一退出函数** `disengage(mob, clearTarget)`，覆盖全部退出路径：

| 退出场景 | 处理 |
|---|---|
| 模式切到等待 / 漫游 | 结束交战（若仍在战斗则让战斗 AI 收尾） |
| 解绑 | 结束并清理 |
| 归属冲突 | 结束并清理 |
| 主人离线 / 死亡 / 跨维度 | 结束并清理 |
| 骑乘 / 拴绳 / 坐下 / 动画占用 | 结束并清理 |
| 生物死亡 | 只清记录，**不触碰死实体** |

**且只清除本模块仍拥有的目标** —— 其他系统设的目标一律不动。

### 源码 P1-3：护卫与主动模式使用不同的威胁规则

原 `FriendlyDefenderSystem.isThreat` 允许 `target instanceof ServerPlayerEntity`，
即**敌人盯着其他玩家也会被当成主人的威胁**；两个模块的友军排除也不一致。

现抽出**唯一判定入口** `CombatEligibility`：

- `isProtected(candidate, owner)` —— 玩家、**同主人的其他伙伴**、**任何已驯服生物**、
  本模块的挚友，全部受保护
- `isThreatTo(candidate, owner)` —— **先查保护，再判定**：只有「把你当目标」或「最近攻击过你」才算威胁

护卫系统与主动模式现在**都调用这两个方法**，不可能再各说各话。

### 源码 P2：等待/漫游与战斗让路的顺序

等待/漫游原本在「有战斗目标」检查**之前**就进入锚点导航，会与原版战斗 AI 争抢移动。
现在这两种模式**先结束本模块的交战**，仍在战斗则让战斗 AI 收尾，之后才执行锚点导航。

### 新增测试

`TargetOwnershipTest`（5 项），覆盖复审点名的场景：
**外部把目标从 A 换成 B 时，本模块必须不再认为自己拥有 B**。
**合计 41 项测试通过。**

### 尚未处理（下一轮）

- P2 持续威胁检查：尚未引入「服务端伤害时间 + tick 有效期」，
  目前仍依赖 `owner.getAttacker()` 的原版语义
- P2 备用实体射线**没有方块遮挡限制**：墙后生物可能被检测器选中
- 复审对 `defaultRequire=1` 的更正（**并非「注入同一方法必崩」**）我尚未据以改变方案，
  后续若要引入 Goal 会先按它的建议核对实际注入点
---
---

## 0.13.1

修复 0.13.0 复审指出的两个问题。

### P1：主动模式的持续校验被提前跳过

**执行顺序缺陷（可由代码顺序确认）**：

```
tick 第 99 行：isOccupied 为真 -> release + continue
isOccupied：存在存活攻击目标就返回真
driveAttack：负责「目标变友军 / 追击超距」的校验
```

后果：**主动模式一旦锁定存活目标，就再也进不到 `driveAttack`** ——
**追击距离校验与友军校验全部失效**，目标可以无限追、变成友军也不会被放弃。

**修复**：把「外部占用」与「持有战斗目标」**拆开**

| 判断 | 内容 | 影响范围 |
|---|---|---|
| `isExternallyOccupied` | 骑乘、被骑乘、拴绳、坐下、NON 动画忙碌 | **所有模式**都让路 |
| `hasCombatTarget` | 存在存活的攻击目标 | 只有**非主动**模式让路 |

主动模式现在**始终执行**，因此能真正做目标合法性、主人有效性与距离校验。

**同时按复审要求处理的细节**：

- **记录本模块设置的攻击目标**（`MODULE_TARGETS`）——
  只清除**自己设的**目标，绝不碰原版 AI 或 NeedsOfNature 设的目标
- **超距时先返回主人附近**，且**本轮不再重新锁定**刚放弃的目标
  （`findThreat` 增加排除参数），避免「清了又立刻锁上」的死循环
- 选新目标前先确认自身在追击范围内
- 有效目标**不每轮 stop 导航**，移动交给战斗 AI
- 死亡 / 解绑 / 服务器停止时一并清理目标记录

### P2：切换提示只有两档

`CompanionInteractions.report` 原本只区分 `FOLLOW` 与「其他」，
导致 **WAIT 和 ATTACK 都显示「漫游」**。

**修复**：在 `CompanionMode` 上新增 `translationKey()` 四档映射，
**操作栏提示与 HUD 面板共用同一个方法**（面板通过 `CompanionMode.parse` 复用），
两者不可能再不一致。

### 我自己在修复过程中引入并当场发现的 bug

改 `tick` 分派时我写了 `return` 而不是 `continue` ——
那会**终止整个 tick 循环**，导致每轮只处理一只伙伴。已在构建前发现并修正，
并在代码处留下注释说明原因。

（这类「循环里写错 return/continue」正是我应当靠**逐处复核**而不是靠构建通过来发现的问题。）
---
---

## 0.13.0

### 新功能：「主动」模式

按你的定义实现：**主动攻击对玩家产生仇恨的目标**。

**判定标准**（刻意收窄，不是无差别索敌）：

- 该生物的**当前目标就是你**；
- 或它**最近攻击过你**（`owner.getAttacker()`）。

**「是敌对生物类型」本身不算理由** —— 一只对你没有敌意的僵尸不会被攻击。

**排除对象**：

- **你自己的伙伴**（避免伙伴互殴）
- **被你驯服/结为挚友的生物**
- **其他玩家** —— 对玩家的攻击与否属于服务器 PvP 规则，本模块不替它决定

**行为**：

| 情况 | 行为 |
|---|---|
| 有有效威胁 | 设为攻击目标并靠近；进入攻击距离后交由生物自身的战斗 AI |
| 威胁死亡 / 变得友好 / **追击超出上限** | 放弃该目标（不会一路追出去把你丢下） |
| **没有任何威胁** | **回到你身边跟随**（符合方案「无目标时跟随」） |

### 模式循环更新为四档

```
跟随 → 等待 → 漫游 → 主动 → 跟随
```

**WAIT（等待）按要求保留**，没有因为这次只提了「主动」而被删掉。

旧存档按**名称**兼容：已存下的 `WAIT` / `WANDER` / `FOLLOW` 不受影响。

### 新增配置（已进配置界面）

| 配置 | 默认 | 含义 |
|---|---|---|
| `companionAttackRadius` | 16.0 | 在你周围这个范围内搜索对你有仇恨的生物 |
| `companionAttackLeashRadius` | 24.0 | 超过这个距离放弃追击并回到你身边 |

`sanitize()` 保证**追击上限不小于索敌半径**，否则在边缘发现的威胁会被立刻放弃。

### 说明：不是所有生物都能战斗

「主动」只是**指定目标**。真正能否近战取决于该生物自身有没有攻击 AI：

- 僵尸、骷髅等有近战/远程 AI 的 → 会打
- 猪、牛、羊等没有攻击 AI 的 → 即使设了目标也不会造成伤害

这符合方案里「不能只 `setTarget` 就宣称所有生物都能攻击」的要求，
本轮**没有**为无攻击能力的生物额外实现战斗行为。
---
---

## 0.12.2

### 修复：界面上显示原始翻译键

截图里的 `message.noncreatureaffinity.panel_mode` 是**键名本身被当成文本显示** ——
0.11.1 那次改布局时，我的脚本 **`delete` 了这个键，而渲染器仍在引用它**。

这次没有只修这一个，而是**做了全量校验**（扫描代码里引用的每个键，逐一对比语言文件），
发现 **5 个键确实缺失**：

| 缺失的键 | 影响 |
|---|---|
| `message.noncreatureaffinity.panel_mode` | 面板第三行显示原始键名 |
| `message.noncreatureaffinity.accept` | 求欢【同意】按钮 |
| `message.noncreatureaffinity.reject` | 求欢【拒绝】按钮 |
| `gui.animation.title` | 动画选择界面标题 |
| `gui.animation.count` | 动画选择界面计数 |

后四个是**我 0.11.1 那次脚本中途失败**（报错后 `exit 1`）导致写入被跳过 ——
脚本失败了，但一部分改动已经落盘，我当时没有察觉。

### 新增工具：语言键审计

`tools/lang-audit.mjs` —— 扫描代码中引用的所有玩家可见键，与 `zh_cn.json` / `en_us.json`
逐一比对，同时检查**中英占位符数量是否一致**。缺键时以非零退出码结束，可接入构建流程。

当前结果：**81 个玩家可见键，0 缺失，0 占位符不一致**。

（原版提供的键如 `gui.done`、`gui.cancel` 已排除，由前缀拼接的键也不再误报。）

### 验证方式

按复核要求，**解包实际 jar 检查**，确认不是只改了工作区：

```
jar 内 zh_cn.json: 204 键，panel_mode = 模式：%s 等关键键全部存在
jar 内 en_us.json: 204 键
```

### 同时包含 0.12.1 的三项跟随修复

（在 0.12.1 中完成，本版一并交付）

1. **解绑未释放控制** —— `ownerUuid == null` 时直接返回，生物解绑后仍会走完旧路径；
   死亡也会残留运行时记录。现在解绑与死亡都会终止本模块的路径并清理记录。
2. **路径结果未检查** —— `startMovingTo` 的返回值被忽略，路径失败时生物站着不动却被记录为跟随中。
   现在只在成功时认定取得导航，失败则**按状态变化记录一次日志**（不刷屏）并等待重试。
3. **中间区不更新路径** —— 只在超过启动距离时刷新，已跟随状态进入「停止~启动」之间不做任何事，
   主人继续走动时生物可能停在旧位置。现在**已跟随状态持续更新直到进入停止距离**。

另加：运行时记录集合有上限，避免卸载的生物长期残留。

### 待你明确的「主动」模式

当前枚举为 **FOLLOW / WAIT / WANDER**（跟随 / 等待 / 漫游），**WAIT 已按要求保留**。

「主动」**尚未实现**，因为它的行为还没定义清楚，可能是两种完全不同的东西：

- **A. 主动索敌**：自行搜索附近的敌人并交战
- **B. 主动响应**：只在你攻击或受击时参战，平时不主动挑衅

在你说清楚之前我不做，也不扩大到「主动攻击所有生物」。
定义后我会同时更新模式枚举、循环顺序、语言键、HUD 与行为逻辑，
并按名称兼容旧存档（不会让已存下的 WAIT 失效）。
---
---

## 0.12.1

修复 `CompanionFollowService` 的三个逻辑缺陷（详见 0.12.2 条目中的完整说明）：

1. **解绑未释放控制** —— 生物解绑后仍会走完旧路径；死亡也残留运行时记录
2. **路径结果未检查** —— 路径失败时生物不动却仍被记为跟随中
3. **中间区不更新路径** —— 已跟随状态在「停止~启动」距离之间不刷新，主人走动后可能停在旧位置

本版未随附语言键修复，语言问题在 0.12.2 一并处理。
---
## 0.12.0

按复核要求推进核心玩法：**等待与漫游模式**，并修正面板布局的三个缺陷。

### 新功能：三模式循环（跟随 → 等待 → 漫游）

潜行 + 空手右键在这三种模式间循环，面板第三行显示当前模式。

| 模式 | 行为 |
|---|---|
| **跟随** | 走向主人；超过开始距离启动，进入停止距离停下 |
| **等待** | 停留在**下令地点**附近（默认 4 格），不主动跟随 |
| **漫游** | 在**下令地点**周围较大范围内（默认 12 格）自由活动 |

**锚点在下令时记录**（维度 + 坐标），而不是之后读取生物位置 ——
「等待」指的是玩家下命令的地方，不是生物后来晃到的地方。

**两种模式都不冻结生物**：

- 范围内**完全交给生物自身的 AI**，只是超出范围才被带回锚点 ——
  这正是漫游看起来像「活动」而不是「拴绳」的原因
- 处于战斗、骑乘、拴绳、坐下状态时**一律让出控制**，允许必要逃生与自卫

**跨维度锚点不会被强行使用**：锚点在其它维度时本阶段不做传送、也不加载远端区块。

### 新增配置（已进配置界面）

`companionWaitRadius`（4.0）、`companionWanderRadius`（12.0）。
`sanitize()` 保证**漫游半径大于等待半径**，否则两种模式看起来一样。

### 面板布局修正（复核指出的三个缺陷）

| 缺陷 | 修正 |
|---|---|
| 高度由预估行数计算，绘制却用另一套游标推进 → **底部出现大块空白** | **测量与绘制共用同一个可见行列表**，底部由最后一行 + 内边距决定，不再为隐藏内容预留行高 |
| 进度条宽度固定 108，而面板宽度会随屏幕缩小 → **进度条可能越出边框** | 条宽限制为**实际可用内容宽度**，测量与绘制使用同一值 |
| 文字按原始宽度居中，未截断 → **长名称越出边框** | 按可用宽度**截断**（名称还会为挚友标识留出位置） |

内边距改为 8，行距为字体高度 + 2，进度条上下留 4。

### 新增测试

`CompanionAnchorTest`（5 项）：锚点与维度经 NBT 往返保留、其它维度锚点会被识别、
旧存档无锚点不会被编造、清除后不会复活、模式循环顺序。
**合计 36 项测试通过。**

### 仍未实现

攻击模式（友军排除、追击上限、战后恢复）、协同战斗、召唤铃。
---
---

## 0.11.3

### 修复：好感满后继续交配不再赠送友情之证

0.10.1 我把赠礼设成了**每个关系只给一次**（`capGiftGiven` 一旦为真就跳过）。
但设计意图是：**好感满之后每次交配都赠送一个** ——
这样继续交配仍有产出，面板上的数量图标（1~6）也才有意义。

现已改为**每次交配都赠送**，并加了两条必要的限制：

- **只认交配来源**（`mating` / `player_initiated`）——
  好感增长也会来自动画峰值，不加这条限制会导致**一次交配给多个**
- **只在物品真正交付后计数**（背包满则掉落在脚下，同样算交付）
- **首次赠礼显示完整文案，后续静默赠送** —— 避免聊天栏被刷屏，且面板已显示累计数量

`capGiftGiven` 保留为「是否已首次赠送」的标记，不再限制赠送次数。

### 修复：右下角图标过大

图标由 12px 改为 **9px**，与字体等高，不再压过文字行。

### 说明

计数含义不变：**该玩家从当前生物累计实际获得的友情之证数量**（不是背包余额）。
因此随着交配次数增加，面板右下角的袋子图标会从 1 个逐步变为 6 个。
---
---

## 0.11.2

### 修复：物品图标被误覆盖

0.11.0 生成贴图时，我把**图 1 写成了 `friendship_token.png`**，
**覆盖了最初那版**（由 `友情之证_无气泡_v5.png` 生成）——
所以物品栏里的友情之证图标变成了新图，而你要的是**最开始那一版**。

现已恢复，贴图分工明确：

| 贴图 | 用途 |
|---|---|
| `friendship_token.png` | **物品图标**（v5 原版，单个袋子） |
| `friendship_token_1.png` ~ `_6.png` | **仅检测器面板使用**（数量 1~6） |

### 修复：面板里的友情之证图标不显示

原因是我用的 `drawTexturedQuad(texture, x0, x1, y0, y1, u0, u1, v0, v1)`
**参数含义不明确**（javap 不显示参数名），传进去后画不出东西。

现改用参数更明确的
`drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, texW, texH)`，
每个值都能对应上：x、y、u、v、宽、高、贴图宽、贴图高。

### 顺带记录一个环境坑

v5 源图路径含中文（`H:/杂事/...`）。把它写进 `.ps1` 再用 `powershell.exe` 执行时，
**Windows PowerShell 5.1 按 ANSI 读取无 BOM 脚本**，中文路径变成乱码，文件读不到。

解决：先用 Node 把源图复制到 **ASCII 路径**，PowerShell 只接触 ASCII 路径。
（Node 能正确处理 UTF-8 路径，PowerShell 5.1 不能。）
---
---

## 0.11.1

按最新要求调整：数量图标**只用于检测器面板**，面板布局按指定样式重做。

### 物品图标恢复为单个袋子

0.11.0 给物品加了 `range_dispatch`（按堆叠数量切换图标）。
现按要求**撤销**：物品定义回到普通的单一模型，
**获得的友情之证物品本身不做任何修改**。

六张数量贴图**保留在资源目录中**，因为面板要用它们。

### 面板布局重做

```
生物名字
好感度：NN
██████████░░░░░░      ← 进度条
交配次数：NN
              友情之证 [图标]
```

- 名称、好感、进度条、交配次数**逐行居中**
- 「友情之证」标签与图标在**右下角**，无独立边框
- **图标按该生物累计赠送的数量变化**（1~6 个袋子），
  通过 `drawTexturedQuad` 直接绘制贴图，不走物品模型
- 好感行只显示数值（进度条已表达比例），信息不重复

### 说明

- 数量为 0 时显示单个袋子图标（1 个的图案），不做空缺
- 数量超过 6 时使用 6 个的图标
- 切换目标时计数与其他缓存一并清空，不会短暂显示上一只生物的数量
---
---

## 0.11.0

按「好感检测器界面与素材接入要求」实施：先交付无小框大面板与可用进度条，并接入数量图标。

### 面板重构：单一大框

- **取消右下角独立小框**，计数并入同一个大框内、右对齐，不再套第二层边框
- 边框由**分层绘制**（底/边/角饰），**不随长名称拉伸变形**
- 内容**居中排版**：名称＋挚友标识 / 好感数字 / 进度条 / 模式 / 操作提示
- **普通与挚友状态布局完全一致**，只有标识与配色变化，切换时不会跳动
- 左右角饰**刻意做得不对称**（一侧偏亮、一侧偏暗），保持平衡而不呆板
- 全部为**运行时绘制**：文字、进度、计数都不烘焙进贴图
- 保持准星右侧锚定、移开隐藏、不刷聊天栏

### 进度条

- **底槽始终完整绘制**，填充层**从左侧按比例裁切**（不压缩整张图）
- 外缘轮廓**最后绘制**，两端保持清晰
- 比例**限制在 0–1**；阈值无效时用安全值，避免除零
- 数字显示实际值，条形填满不会越界

### 右下角计数区（无独立边框）

显示**该玩家从当前生物累计实际获得的友情之证数量**，不是背包余额：

- 新增 `tokens_received` 字段并随存档持久化
- **只在物品确实交付后才 +1**（与「赠礼成功才显示」一致）
- 旧存档没有该字段 → 显示 0，代表**没有记录历史**，不用背包余额冒充
- 切换目标时与其他缓存一并清空，**不会短暂显示上一只生物的计数**

### 接入友情之证数量图标（1~6）

6 张源图均为 `Format32bppArgb`、**自带透明通道**（无需去白底）。
处理方式：裁剪到内容边界 → **保持宽高比**缩放进 32×32 画布（横向多袋图案不做正方形拉伸）。

物品定义改用 `range_dispatch` 按堆叠数量切换模型：

```json
"property": "minecraft:count"
"entries": [ { "threshold": 2, ... }, ... { "threshold": 6, ... } ]
"fallback": 单个袋子
```

### 网络协议

`affinity_data` 新增 `tokensReceived` 字段。**客户端与服务端必须同版本。**

### 未完成

等待 / 漫游 / 攻击模式、召唤铃、协同战斗仍未实现；
面板的最终美术素材（用户另行选择）尚未接入，当前边框为代码绘制版本。
---
---

## 0.10.1

按 0.9.0 界面复核调整文案，并实现「好感上限赠礼」。

### 新增：好感达到上限时赠送友情之证

- 每个「玩家 × 生物」关系**只赠一次**，`cap_gift_given` 随存档持久化，
  反复顶在上限也不会重复领取
- **只有在物品确实交付后才显示赠礼文案**；未赠送时只显示「好感已达上限」
- 交付策略明确：**背包有空间就入包，否则掉落在脚下** —— 二者只发生其一，不会同时入包又掉落

### 文案修正

| 位置 | 修改 |
|---|---|
| 好感上限提示 | **删除**后半段「把……调小些才能看到逐步增长」—— 界面不应要求玩家修改开发参数 |
| 新增赠礼提示 | 「XX 的好感已达上限，为了表示感谢，赠送了你一个友情之证」（**仅在实际发放后显示**） |
| 面板好感行 | 精简为 `好感：100 / 100`，阈值解释留在配置帮助中 |
---
## 0.10.0

### 新功能：伙伴实际跟随移动

此前 FOLLOW/WANDER 只是**记录状态**，生物不会移动。现在真正生效。

**实现方式：完全通过生物自身的寻路，不需要 Mixin，也不注入 Goal。**

```java
mob.getNavigation().startMovingTo(owner, speed)
```

只向导航请求「走向主人」，**从不传送**，也不整体覆盖生物的 AI。

**行为规则**（按方案）：

| 规则 | 实现 |
|---|---|
| 超过 6 格开始、进入 3 格停止 | 用平方距离比较（36 / 9），避免边界抖动 |
| 按服务器 tick 间隔更新 | 默认每 10 tick，可配置 |
| 模式切换停止自身任务 | WANDER 释放控制权，交还原版 AI |
| 主人不可用时不改认他人 | 离线/死亡/旁观/跨维度 → **停止跟随** |
| 冲突归属不驱动移动 | 与交互路径一致，双方都拒绝 |
| 占用时让出控制 | 骑乘、被骑乘、拴绳、坐下、战斗、NON 动画忙碌 |

**只在自己确实接管过移动时才调用 `stop()`**（用 `DRIVING` 集合跟踪），
避免停掉其他系统正在进行的导航。

### 配置新增（已进配置界面）

`companionFollowStartDistance`（6.0）、`companionFollowStopDistance`（3.0）、
`companionFollowSpeed`（1.0）、`companionFollowIntervalTicks`（10）。
`sanitize()` 保证**停止距离 < 开始距离**，否则会在边界反复起步。

### 工作方式改进

本次**先写编译探针验证 API**，再写实现：

```java
mob.getNavigation().startMovingTo(owner, 1.0);   // 已确认存在
mob instanceof Leashable l && l.isLeashed();     // 1.21.11 用 Leashable 接口
world.getPlayerByUuid(uuid)                      // 返回 PlayerEntity，需转换
```

一次编译就确认了全部签名，而不是写完等实机崩溃。这应当成为后续的默认做法。

### 仍未实现

等待 / 漫游 / 攻击模式、召唤铃、协同战斗。
**等待与漫游需要保存锚点**（维度 + 坐标），属于下一步。
---
---

## 0.9.0

按方案的交付顺序，完成第 2 项（挚友文案、友情之证、检测器配方与 HUD）。

### 新增物品：友情之证

- 稳定 ID `noncreatureaffinity:friendship_token`（**显示名可改，ID 不改**）
- 可堆叠（64），已加入原版工具创造标签
- 资源：`items/` 物品定义、`models/item/` 模型、`textures/item/friendship_token.png`
- **图标由定稿源图生成**：从 `友情之证_无气泡_v5.png`（1254×1254，自带透明通道）
  **裁剪到内容边界后缩放为 32×32**（避免留白导致图标过小），保留透明背景。
  源图只用于生成，**不是运行时依赖**。

### 新增配方

无序配方：**1 友情之证 + 1 铁锭 → 1 好感度检测器**

- 沿用现有检测器 ID，不新建重复物品
- **精确引用友情之证**，不使用宽泛标签 —— 避免将来新增材料时被意外替代

> 你原话「一块铁」我按**铁锭**实现。若你指的是**铁块**，告诉我，改一行即可。

### 玩家可见文案：「密友」统一改为「挚友」

覆盖语言文件与源码中的硬编码（`★密友` → `★挚友`）。
**只改可见名称**：`befriended` 等存档键与好感阈值均未改动，旧存档不受影响。

### 检测器面板改为方案指定的四行布局

```
马  ★挚友
好感：87 / 100（挚友所需）  ████████░░
模式：跟随（移动尚未实现）
你的伙伴 · 潜行 + 空手右键 切换模式
```

- **第二行的分母改为「挚友阈值」**（原来用的是好感上限）——
  玩家真正关心的是「离成为挚友还差多少」
- 第三行**明确标注移动尚未实现**，不让人误判成跟随失灵
- 第四行的操作提示**只在自己的伙伴上显示**，其他实体不显示无效提示
- 仍保持「仅显示当前目标数据、切换目标立即撤销旧名称与标志」

> 为此网络包新增了 `friendThreshold` 字段。**客户端与服务端必须同版本**，
> 混用会出现字段错位。

### 未完成（如实标注）

- **友情之证的获取来源未确定**，因此**没有实现任何获取途径** ——
  方案明确指出「获取来源确定前不要宣称生存获取闭环已完成」。目前只能通过创造模式取得。
- 新生实体未自动成为伙伴（你的反馈）：**尚未复现原因**，本轮未修，也未用宽松规则掩盖。
- 跟随/等待/漫游/攻击的实际移动：未实现。
---
---

## 0.8.2

### 验收通过：同 tick 双触发防护实测生效

实机日志（0.8.1）：**9 组**同玩家、同目标、同 tick 交互，
每组 **1 次 applied + 1 次 SUPPRESSED**，共 9 次状态改变、9 次重复拦截；
最终模式按操作交替，**没有再出现立即翻回**。

### P2：单组静态变量会被穿插交互覆盖

0.8.1 的去重只用一组 `lastSwitchTick / lastSwitchPlayer / lastSwitchTarget`，
**只保留最后一次成功操作**。可推导的失效序列：

```
同 tick：A/目标X 成功   -> 记录 (tick, A, X)
同 tick：B/目标Y 成功   -> 记录被覆盖为 (tick, B, Y)
同 tick：A/目标X 重复回调 -> 与 (tick, B, Y) 不匹配 -> X 再次翻转
```

现改为按 **(世界, tick, 玩家, 目标)** 建立独立记录：

- 键含**世界标识**，跨维度不会互相干扰
- 穿插交互**不会覆盖**彼此
- 每条记录**按 tick 清理**（保留 1 tick），集合不会无限增长
- **服务器停止时清空**（`CompanionInteractions.clearState()`）

### 修正源码注释中过度承诺的表述

原注释写「两次真正点击不受影响」，这**不准确**。已改为精确列出三条边界：

1. 两个入口若落在**不同 tick**，仍然不会被合并；
2. **长按**持续产生新 tick 的操作，本规则无法阻止；
3. **同一 tick 内的两次真实独立点击无法区分**，会被合并。

即：这是针对已实测缺陷的**短期防护**，不等于「一次物理点击一次操作」的根治。

### 诊断日志改为受 `debugLogging` 控制

双触发已验证修复，逐回调的 INFO 日志不再默认输出。
需要再次排查时在配置界面打开 `debugLogging` 即可。
**抑制日志仍然保留**（每次拦截都会记录并累加 `totalSuppressed`），
以便验证防护是否持续有效。
---
---

## 0.8.1

### 实测确认：一次点击触发两次（同一 tick）

0.8.0 的诊断日志给出了**决定性证据**（你的实机日志，同一 tick=13466，
同一玩家、同一目标、MAIN_HAND、sneaking=true）：

```
hitResult=true   FOLLOW -> WANDER  applied
hitResult=false  WANDER -> FOLLOW  applied    （同一 tick）
```

**两个交互入口在同一次点击中各触发一次**：
带命中位置的那个与不带位置的那个。13469、13473 等 tick 重复同样组合。
这解释了实际游玩中看到的密集模式交替。

### 修复：同 tick、同玩家、同目标的重复回调去重

在**成功切换后**记录 `(tick, 玩家, 目标)`，同一组合的第二次回调**直接消费但不切换**，
并记录被抑制的次数与 `hitResult` 值，便于验证效果：

```
[NoN Affinity] companion interact SUPPRESSED duplicate tick=... target=... hitResult=... totalSuppressed=...
```

**防护范围刻意收窄**（按方案要求）：

- 只比较 **tick + 玩家 + 目标** —— 跨 tick 的两次真实点击、不同生物、不同玩家**都不会被合并**
- **仅在成功修改后**才置位，被拒绝的交互不会挡住后续合法操作
- 这是**短期防护**，不等于「一次物理点击一次操作」的根治；
  方案的根治方向（客户端与服务端归并为一次操作）留待后续，且需先核实发包路径

> **没有**采用「长冷却」或「`hitResult=null` 一律无效」这类做法 ——
> 前者会把确定性 bug 变成随机不响应，后者会误伤合法使用无位置入口的交互。

### 下一步（按方案顺序）

1. 先由你实测确认双触发已消除（10 次独立短按 = 恰好 10 次切换）
2. 再做**四种模式**：跟随 → 等待 → 漫游 → 攻击（方案已给出各模式的移动与战斗后行为）
3. 然后主人受击与协助攻击
4. 最后召唤铃

**四种模式暂不叠加** —— 方案明确指出，叠加到「每次触发切换两次」的入口上会表现为「跳过模式」。
---
---

## 0.8.0

按方案实施三项：交互方式变更、双触发诊断、归属规则统一。

### 交互方式改为「潜行 + 主手空手右键」

原「普通空手右键」会占用骑乘/坐下，方案已确认这与原版交互**无法共存**。
现改为：

| 操作 | 行为 |
|---|---|
| **潜行 + 空手右键** | 切换伙伴的跟随 / 漫游 |
| **普通空手右键** | 完全走原版（骑乘、坐下、其它模组菜单） |

用**玩家潜行状态**判断而非直接读 Shift 键，以适配改键。
物品提示与新增提示语已同步说明该操作方式。

### 新增交互诊断日志（用于定位「一次点击多次切换」）

日志显示实际游玩中出现**密集模式交替**（如劫掠兽、僵尸多次 FOLLOW/WANDER 连续翻转），
但秒级日志无法把「一次物理点击」与「一次回调」对应起来。

现每次进入切换逻辑都会记录：

```
[NoN Affinity] companion interact tick=<服务器tick> player=<UUID> target=<UUID>
    hand=<手> sneaking=<是否潜行> hitResult=<是否为空> modeBefore=<切换前模式>
[NoN Affinity] companion interact applied tick=<tick> target=<UUID> modeAfter=<切换后模式>
```

若**一次点击出现两条 interact 记录**，即证实双触发，并可据 tick 判断来源。

> 按方案要求，**没有**用「未经验证的长冷却」掩盖该问题 —— 那只会把双触发变成随机不响应。

### 归属规则统一：冲突时双方都拒绝

此前 `mayControl` 在「原版主人 ≠ 伙伴绑定」时**让原版主人优先**，
而 `FriendlyDefenderSystem.findOwner` 只读伙伴绑定 —— **两处规则不一致**。

现统一为：**两种归属均非空且不同 ⇒ 双方都拒绝**，
护卫系统遇到该状态 **本轮不护卫任何人**，
由管理员用 `/nonaffinity companion info` 查看、`companion clear` 修复。

判定逻辑收敛到 `CompanionService.mayControl` 与 `hasOwnershipConflict`，
跟随与护卫共用同一答案。新增/更新单元测试覆盖：
一致、冲突（双方皆拒）、仅原版、仅伙伴、无绑定五种组合。
---
---

## 0.7.5

### P2：护卫系统用的是另一套归属规则

`FriendlyDefenderSystem.findOwner` 的旧逻辑：

1. 先解析在线原版主人；
2. 失败后**扫描 24 格内的友好玩家，取好感最高者** —— **完全不读取伙伴绑定**。

后果（复核指出）：已绑定 A 的非原版可驯服生物，可能去保护**好感更高的 B**；
A 一离开就可能**改保护附近的 B**。绑定本身没被转移，
但**护卫服务按另一套规则决定归属** —— 又是「同一概念、多套规则」。

现改为**统一归属优先级**：

| 优先级 | 依据 | 主人不可用时的行为 |
|---|---|---|
| 1 | **伙伴绑定** `companionOwner` | **本轮不护卫任何人**，绝不改选其他玩家 |
| 2 | 原版主人 | 同上 |
| 3 | 旧数据（无任何绑定记录） | 保留原有的「附近好感最高」兼容逻辑 |

「不可用」= 离线、死亡、旁观或不在当前维度，统一由 `availablePlayer` 判定。
第 3 条是唯一还按好感挑选的路径，且仅适用于**伙伴系统出现之前的旧存档**。

### 文案修正：不再承诺未实现的移动

`companion_follow` 由「跟随你」改为「**跟随模式（移动尚未实现）**」。
当前版本只记录模式，生物**不会移动** —— 原措辞会让使用者把功能缺失误判成跟随失灵。

### 说明：交互取舍待使用者决定

复核确认「空手切换」与「空手骑乘」**不可能共存**（服务端除 `PASS` 外必然终止后续处理）。
备选方案（潜行切换 / 特定物品）已列入给 GPT 的报告，等使用者选定后再改，本轮不动交互方式。
---
---

## 0.7.4

### P1：实体射线检测是**错的**，我上一轮的修复等于没生效

复核给出字节码级证据：`Entity#raycast` 最终调用
`World.raycast(RaycastContext): BlockHitResult` —— 它检测的是**方块**，
因此 `instanceof EntityHitResult` **永远为 false**。

后果：

- 我 0.7.3 新增的严格解析 `resolveAimedMob`，其准星分支是**死代码**
  → `companion info` / `companion clear` 的无参形式**始终报告「没有瞄准生物」**
- `set` / `friend` 仍在用带评分回退的宽松解析 → 可能修改**未瞄准的附近生物**

**修复**：

1. 改用**真正的实体射线** `ProjectileUtil.raycast`，并对结果做**方块遮挡检查**
   （被墙挡住的目标不计）；
2. `set` / `friend` 改用严格解析，并新增**显式实体参数**：
   `/nonaffinity set <值> [实体]`、`/nonaffinity friend [实体]`；
3. 未瞄准时**明确报错且不修改任何数据**，绝不回退到附近评分选取；
4. 成功信息**回显生物名与实体 UUID**，便于核对改的是哪一只；
5. 删除已废弃的宽松解析 `resolveMob`，避免被再次误用。

> `accept` / `reject` 的目标改为**由挂起请求决定**（无参时传 `null` 让 RequestSystem 自己查），
> 不再从准星或附近生物猜测。

### P2：空手切换必然占用骑乘/坐下（已确认，非 bug）

复核查证 Fabric 源码后确认：**服务端除 `PASS` 外的返回值会停止后续处理**。
因此「空手右键既切换伙伴状态、又继续执行原版骑乘/坐下」**不可能同时成立** ——
这是空手切换这一交互设计本身带来的取舍，不是实现缺陷。

处理：`NOT_OWNER`（点到别人的宠物）改为返回 **`PASS`** ——
目的只是禁止改归属，不应连带禁止原版交互。

**待你决定**：是否改为「**潜行时空手右键**」才切换伙伴？这样普通空手右键可保留骑乘。
我没有擅自改动交互方式，因为空手切换是你明确要求的。

### 修复：注释与实现矛盾

`CompanionService` 的类注释与 `toggle` 注释仍写「仅通过显式交互绑定」，
与 `bindOnBefriend` 的自动绑定实现矛盾，已更正，避免后续按错误注释改坏规则。

### 我的操作失误（第三次同类）

删除废弃的 `resolveMob` 时，我的脚本区间回溯把 **`companionClear` 方法一起删掉**了。
构建立即报错并已恢复。

**这已是「大范围文本删除」造成的第三次事故**（前两次：误删 `AffinityService` 8 个方法、
误删 `tickDetector`）。我已改为对该文件**只用精确的 edit 逐处修改**，不再用脚本批量删除。
---
---

## 0.7.3

### P2：破坏性命令可能清除错误的生物

`companion clear` 复用了通用目标解析 `resolveMob`，而它含有**按评分挑选附近生物的后备逻辑**：

```java
HitResult hit = player.raycast(8.0, 0.0F, false);
if (hit instanceof EntityHitResult ...) return mob;
// 后备：不要求正对，直接选附近评分最高者
LivingEntity best = AffinityService.findBestPartner(player, config, 8.0);
```

这套后备本是为「按 F 触发交配」设计的便利行为，但被**清除命令**继承后：

- 没有正确瞄准时，`clear` 会清除**身旁另一只**伙伴
- `info` 与 `clear` **各自重新解析目标**，期间生物移动或评分变化
  → 可能出现「**检查的是 A，清除的是 B**」

修复：新增**严格解析** `resolveAimedMob` —— 只接受显式指定的实体或准星真实命中，
**没有任何后备回退**；两个命令都支持显式实体参数：

```
/nonaffinity companion info [实体]
/nonaffinity companion clear [实体]     需要 OP
```

未瞄准时 `clear` **什么都不做**并提示如何显式指定 —— 宁可拒绝，也不清错。
清除成功后会**回显生物名、实体 UUID 与原伙伴主人 UUID**，便于操作者核对。

### 修复：两个源码文件的注释编码损坏

排查过程中发现 **`AffinityHolder.java` 与 `EntityAffinityMixin.java` 的注释区已完全乱码**
（如 `桸。`、`ѯ"。`、`øС。`，以及 59 行孤立的中文句号）。

- 起因：早期用 PowerShell 文本管道写入 Java 文件，破坏了 UTF-8 编码
- 影响：**仅注释**，代码与字符串完好，因此一直未被发现，编译与运行都正常
- 修复：以英文纯 ASCII 注释重写这两个文件的注释，代码逻辑一字未改

现已全项目扫描确认**无编码损坏文件**。

### 规范化：用户可见文本全部移入语言文件

代码中残留的显示文本已迁移（`AffinityBars.tier` 改为返回**翻译键** `tierKey`，
服务端提示与客户端面板共用同一份本地化文本）：

- 好感等级词：已满 / 亲密 / 友好 / 熟悉 / 陌生 / 初次见面
- 动画选择界面：标题、条目、不可用标记、数量
- 伙伴信息与清除命令的全部提示

语言文件现为 **179 键**（中英各一份）。
Java 源码中的用户可见中文现已为零，仅保留中文**注释**（编码正常，不影响构建与显示）。

### 教训

我一度误判为「乱码」，实际是 PowerShell 控制台的**显示**编码问题 ——
文件本身正确。真正的损坏是靠 **Node 逐行检测字节范围**才定位到的。
判断编码问题应看文件字节，而不是看终端输出。
---
---

## 0.7.2

采纳 0.7.1 伙伴系统复核。

### P2：后代成为友好生物时遗漏伙伴绑定

**这正是我在上一轮报告里主动提出的担心，复核确认成立。**

已知有**三处**「生物成为友好」的入口，但 0.7.1 只在其中一处调用了绑定：

| 入口 | 0.7.1 状态 |
|---|---|
| `AffinityService.checkFriendThreshold`（好感达标） | ✅ 有绑定 |
| `AffinityService.bindOffspring`（后代） | ❌ **缺绑定** |
| `InheritanceService.tryInheritFromParents`（后代继承） | ❌ **缺绑定** |

后果：**后代成为「友好但未绑定」的生物**。而 `checkFriendThreshold` 对已经是密友的关系
会直接返回，所以这个缺失**之后再涨好感也无法补齐**。

修复：两处后代入口都改为调用**同一个带归属校验的入口** `CompanionService.bindOnBefriend`，
不复制规则、不绕过 `mayControl`。

> 这与 0.6.5 的 P1（`requestNow` 绕过冷却）是**同一类缺陷**：
> 同一业务动作存在多个入口，规则只写在其中一个。
> 复核这次直接在报告里点出了它，因此本轮采用「共用入口」而非「逐个补规则」。

### 旧冲突绑定：新增管理员修复入口

若旧版本已把 A 的原版宠物绑定给 B，则现在：
B 被原版主人校验拒绝、A 被伙伴绑定校验拒绝 —— **双方都无法切换模式**。
拒绝是正确的安全行为，但缺少恢复手段。

新增两个命令：

```
/nonaffinity companion info    显示准星生物的生物名、实体 UUID、原版主人 UUID、伙伴绑定 UUID、模式
/nonaffinity companion clear   清除该生物的伙伴绑定（需要 OP 权限）
```

- `info` 会**同时打印两个主人 UUID**：不一致时给出明确提示 ——
  这是唯一能诊断此类冲突的方式
- `clear` **只清除伙伴记录**，不改动原版主人、好感度、物品栏与生命值
- **不做任何自动纠正**，也不会无提示抢回；清除后由合法玩家正常右键绑定

### 报告疑问的核对结果（复核答复）

- `/nonaffinity set` 与 `/nonaffinity friend` **都已**经过 `checkFriendThreshold`，
  不存在遗漏入口 —— 我上一轮的担心在这条命令路径上不成立
- 不自动迁移「多主人旧档」的策略可以保留
- 「已驯服但主人引用为空」的实体仍需明确认领政策（留待后续）
---
---

## 0.7.1

采纳 0.7.0 伙伴系统复核。

### P1：离线原主人被当作「无主」，他人可绑定

**这是我 0.7.0 的逻辑漏洞。** 归属判断依赖「把原版主人解析成在线实体」：

```java
LivingEntity vanillaOwner = resolveVanillaOwner(world, mob);
if (vanillaOwner instanceof PlayerEntity owner && !owner.getUuid().equals(player.getUuid())) {
    return NOT_OWNER;
}
data.setCompanionOwner(player.getUuid());   // 主人离线时 vanillaOwner == null，直接绑走了
```

原主人**离线、跨维度或未加载**时解析结果为空，被当成「无主」，
于是 B 可以绑定 A 的宠物 —— 原版主人与伙伴控制者就此不一致，后续跟随/召唤会放大问题。

**修复**：改为读取引用中的**持久 UUID**（`LazyEntityReference.getUuid()`），
完全不依赖实体是否加载：

```java
if (!mayControl(current, vanillaOwnerUuid(mob), player.getUuid())) return Result.NOT_OWNER;
```

`mayControl` 是**纯函数**，因此该规则可被单元测试直接覆盖，而不只是测一个 UUID 比较：

| 场景 | 结果 |
|---|---|
| 原版主人离线，他人请求 | **拒绝** |
| 原版主人本人请求 | 允许 |
| 已有他人伙伴绑定 | 拒绝 |
| 无主且未绑定 | 允许 |
| 旧伙伴绑定 与 原版主人 冲突 | **原版主人优先** |

并且无论是否已有伙伴绑定，**都重新核对原版归属**（应对其它模组或命令改动过主人的情况）。

### 功能缺口：新晋密友现在会自动成为伙伴

复核指出：不能拿「旧存档不自动迁移」当借口，而**永不实现**「成为密友后跟随」。
用户最初的需求就是密友默认跟随。

现已在**跨越密友阈值时自动绑定**（`bindOnBefriend`）：
新晋密友若**无原版主人或原版主人就是该玩家**，且尚未被任何人绑定，则自动建立 FOLLOW 绑定。

- 新晋密友 → 自动绑定（符合原始需求）
- 旧存档已有密友 → **仍不自动迁移**（避免批量猜测归属；玩家右键一次即可）

### 旁观者

右键回调现在**显式拒绝旁观模式**修改伙伴状态。

### 措辞修正

日志中 `is now following` 改为中性的 `companion bound ... (mode ...)` ——
本版只记录模式，**实际移动尚未实现**，原措辞会让玩家以为生物已在跟随。

### 测试

新增 `CompanionOwnershipTest`（6 项），针对上述归属规则。
**合计 31 项测试通过。**
---
### 仍未实现（同 0.7.0，勿视为完成）

| 部分 | 状态 |
|---|---|
| 伙伴身份 + 空手切换 + 新晋密友自动绑定 | ✅ 已完成 |
| **实际跟随移动**（寻路、多物种、距离阈值） | ❌ **未实现** —— 模式只是记录，生物不会移动 |
| 协同战斗（伤害事件、PvP/队伍规则） | ❌ 未实现 |
| 召唤铃（物品、界面、跨维度传送、安全落点） | ❌ 未实现 |
---
## 0.7.0 —— 伙伴系统（第一阶段：身份与切换）

按功能方案的分阶段顺序实施。**本版只完成第一阶段**，详见下方「尚未支持」。

### 新功能

**1. 空手右键友好生物，切换「跟随 / 自由漫游」**

- 首次交互会把该生物**绑定为你的伙伴**，模式默认为 **跟随**
- 再次交互在跟随 / 自由漫游之间切换，动作栏提示
  `僵尸：跟随你` / `僵尸：自由漫游`
- 检测器面板同步显示当前模式

**2. 伙伴身份与移动模式持久化**

- 存在**实体级**字段（`companion_owner` / `companion_mode`），
  而不是每个 PlayerAffinity 一个跟随布尔值 ——
  一只生物可以对多名玩家有关系，但只能跟随一个人，分开存会互相矛盾
- 随区块卸载、重启、跨维度保留
- **旧存档兼容**：缺字段即视为「未绑定 + 跟随」，且未绑定时**不写入任何伙伴键**，
  旧存档保持原样

### 设计约束（刻意保守，避免抢宠物）

| 规则 | 说明 |
|---|---|
| 绑定只经**显式交互**产生 | 好感达标**不会**自动指定主人 —— 数值变化不该静默决定归属 |
| 已有绑定**从不转移** | 别人已绑定的生物返回「已经属于别人了」 |
| **原版主人优先** | 已被原版驯服且主人是别人的生物，不接受指挥 |
| 资格用**实际友好标志** | 不是 affinity 数值，也不是界面上的等级文字 |

### 交互安全性

- **仅主手**：避免副手回调重复切换一次
- **仅服务器端**执行并校验，客户端结果不被信任
- **仅空手**：手持物品时放行原版逻辑
- 成功切换后返回 SUCCESS **消费该次点击**，避免同一次右键又触发骑乘/坐下/其它模组菜单
- 非伙伴目标一律放行（PASS），原版行为完全不变

### 修复

延续 0.6.6 的判空约定：主人引用解析统一走 `resolveOwner`，
伙伴服务复用同一方法，不新增裸链式调用。

### 测试

新增 `CompanionPersistenceTest`（4 项），直接覆盖方案的告警点
「不能只新增字段却遗漏 toNbt/fromNbt」：

- 绑定与模式经 NBT 往返后保留
- 旧存档（无这些字段）加载为「未绑定 + 跟随」
- 未绑定生物**不写**伙伴键
- 存储的模式值非法时回退为跟随，而不是抛异常

**合计 25 项测试通过。**
---
### 尚未支持（本版明确未实现，不要视为已完成）

方案共三大部分，本版只交付了第一部分：

| 部分 | 状态 |
|---|---|
| 伙伴身份 + 空手切换 | ✅ **本版完成** |
| 跟随移动（寻路、多物种适配、距离阈值） | ❌ **未实现** —— 切换只改变记录的意图，生物**不会真的跟着你走** |
| 协同战斗（伤害事件、PvP/队伍规则） | ❌ 未实现 —— 护卫仍沿用 0.6.6 的威胁识别 |
| 召唤铃（物品、选择界面、跨维度传送、安全落点） | ❌ 未实现 |

**关于物种支持范围**：本版的绑定与切换适用于任何有友好关系的 `MobEntity`，
但**不保证所有物种都能战斗** —— 仅 `setTarget` 不会让没有攻击 Goal 的生物具备近战能力。
战斗相关承诺留待后续阶段实测后给出。
---
## 0.6.6 —— 紧急崩溃修复

### 崩溃：进入世界后服务端世界 tick 抛 NPE

```
FriendlyDefenderSystem.resolveVanillaOwner:110
FriendlyDefenderSystem.findOwner:77
FriendlyDefenderSystem.tick:43
NonCreatureAffinity.lambda$registerTick$4:160
```

异常为 `AbstractHorseEntity.getOwnerReference()` **返回 null**，而代码直接链式调用
`getEntityByClass(...)`。

### 根因：我基于一个**错误的 API 假设**写代码

0.5.0 加「防止抢走他人宠物」时我写了：

```java
if (horse.isTame()) {
    LivingEntity existing = horse.getOwnerReference().getEntityByClass(world, LivingEntity.class);
}
```

**我假设「`isTame()` 为真 ⇒ 主人引用必然存在」—— 这个假设是错的。**
实机证明已驯服的马类可以没有主人引用，于是链式调用直接 NPE，
**整个服务端世界 tick 崩溃**。

### 修复：四处同类风险一并处理

查找全部 `getOwnerReference().` 链式调用，共 **4 处**：

| 位置 | 分支 |
|---|---|
| `FriendlyDefenderSystem.resolveVanillaOwner` | TameableEntity |
| `FriendlyDefenderSystem.resolveVanillaOwner` | **AbstractHorseEntity（崩溃点）** |
| `AffinityService.tameCreature` | TameableEntity |
| `AffinityService.tameCreature` | AbstractHorseEntity |

统一改为经 `resolveOwner(reference, world)` 解析：

```java
return reference == null ? null : reference.getEntityByClass(world, LivingEntity.class);
```

**语义约定**（保持保守，不把防崩溃补丁变成自动转移所有权）：

- 引用为 null 或解析不到（主人离线/不在同一世界）⇒ 视为「无可用主人」
- 护卫扫描：`findOwner` 返回 null 后回退到关系查找，仍无有效保护对象则**跳过该生物**，
  绝不让单只生物的问题拖垮整个世界 tick
- `tameCreature`：已驯服但引用缺失时**安全返回 false，保留实体状态，不指定新主人**
- 未使用「捕获所有异常并静默吞掉」，而是明确判空

### 新增回归测试

`src/test/java/com/nonid/affinity/service/OwnerResolutionTest.java`

直接调用**生产代码**的 `resolveOwner(null, null)`，覆盖空引用路径（两项均通过）。
复核指出得对：纯数值裁剪与 NBT 往返测试无法捕获此类缺陷。

### 教训

我把「类型系统允许」当成了「运行时保证」：`getOwnerReference()` 的返回类型带可空语义，
我按非空使用。**此类"看起来必然成立"的前提假设，必须查 API 文档或做防御性判空，
不能凭直觉推断。**
---
## 0.6.5

### 重要：0.6.4 的修复**实际没有生效**

复核指出后我立即核实，确认属实：

```
AffinityCommand.java:105:  if (!RequestSystem.accept(player, mob))     <- token 没传下去
RequestSystem.java:137:    requestNow 仍是旧实现，canRequest 根本不存在
```

**原因**：我的批量修改脚本用 `text.split(old).join(new)` 做替换，
当 `old` 与实际代码不完全一致时，**替换会静默失败**，而我没有做任何校验，
于是「改完了、构建通过了」却什么都没改。

**流程修复**：此后所有批量替换都使用带**严格断言**的辅助函数 ——
匹配次数不为 1 就立即报错并中止，绝不允许静默跳过。
本轮 14 项替换全部输出 `ok [label]`，并在写入后**再次用 grep 独立验证**结果。

### P1：请求入口规则统一（本次真正落地）

`requestNow`（动画事件入口）现在调用共享的 `canRequest(mob, player, config)`：

- 实体存活、与玩家同维度
- 该生物未在等待回答
- 玩家**没有**其它挂起请求
- 关系**不在冷却中**

并且**不再重置冷却**。冷却期间仍然阻止动画事件，但不会重新弹出请求 —— 两件事已分离。

### P1：按钮请求标识（本次真正落地）

- 命令层现在**真的把 token 传下去**：`RequestSystem.accept(player, mob, token)`
- 请求标识改用**会话内自增的唯一 ID**，不再用过期 tick
  （复核指出：同一 tick 创建的两个请求会有相同的到期时间，因此不唯一）
- 命令改为无歧义子命令形式：
  - `/nonaffinity accept` —— 处理当前请求（手动）
  - `/nonaffinity accept id <编号>` —— 按钮使用
  - `/nonaffinity accept mob <实体>` —— 按生物手动指定
- **过期检查移入回复处理时执行**（不再只依赖每 tick 的清理）
- 请求结束时（接受/拒绝/超时）清除其 ID；服务器停止时清空全部 ID

### 教训

复核的核心提醒值得记录：

> 不要继续仅以「已有 token 字段」作为修复成功的依据，
> **须验证命令到业务处理的完整调用链。**

我此前确实把「写了代码」当成「修好了」。这次除了断言，我还对**每一处修复单独 grep 验证**，
而不是只看构建是否通过 —— 构建通过只证明语法正确，不能证明逻辑被改到。
---
## 0.6.4

采纳 0.6.3 复核的四项。复核同时确认：字形问题无需再按旧假设修改，护卫行为已有用户实测通过。

### P1：即时请求入口绕过冷却与单请求限制

`requestNow`（动画事件回调入口）**只检查该生物自身有无挂起请求**，
既不检查玩家是否已有请求，也不检查关系冷却 —— 反而**主动重置冷却**以便发出去。后果：

- 拒绝后本应处于冷却的关系，在生物再次尝试交配时会**立刻重新请求**
- 第二只生物可绕过「每玩家一条请求」限制，服务器堆积多条而客户端只显示一条

修复：抽出 `canRequest(mob, player, config)` 作为**所有自动请求入口共用的唯一规则**
（存活、同维度、该生物未在等待、玩家无挂起请求、关系不在冷却），
周期扫描与事件入口都走它，不再各自判断。
**冷却期间仍然阻止动画事件，但不再重新弹出请求** —— 这两件事已分开。

### P2：历史聊天按钮仍能操作新请求

按钮此前只有 `/nonaffinity accept`，不含请求标识。
A 请求结束后 B 请求出现时，点击 **A 的旧按钮会操作 B**。

修复：以**该请求的过期 tick 作为唯一标识（token）**，按钮变为
`/nonaffinity accept <token>`；处理时校验 token 是否与当前挂起请求一致，
不一致则提示「这个求欢请求已经结束了」而不执行任何操作。
不带参数的裸命令仍表示「处理当前请求」，便于手动使用。

> 说明：使用过期 tick 而非新增计数器，是因为它天然每轮唯一，
> 且无需改动请求表的存储结构。

### P2：刷新期限仍约 2 秒

`STALE_AFTER_TICKS` 原为 40 tick（约 2 秒），与「半天内刷新」的设计目标不符。
现改为 **10 tick**，判定改用 `>=`，限频同为 10 tick（高帧率不会增加每秒查询数）。

### P2：长名字会把面板挤过准星

原实现只用 `Math.min(x, screenWidth - boxWidth - 4)` 左移面板，
名字过长时会**覆盖准星**；文本宽于屏幕时 x 甚至为负。

修复：**锚点固定**在准星右侧，改为按右侧剩余宽度**截断文本**；
只对垂直位置做夹紧，水平方向不再移动。

### 备注

修复过程中我的批量替换又一次误删了方法（`tickDetector`），已恢复并重新构建通过。
这已是同类失误的第二次，后续对渲染器这类文件将改为逐处精确定位后再改。
---
## 0.6.3

### 数字字形问题：**已确认不存在**

面板实机截图显示数字、斜杠、等号全部正常：

```
马
好感：100 / 100
██████████ 已满
交配次数：3 █░░░░
0123456789 / - =
```

探针完成了它的使命 —— **HUD 面板路径下数字渲染完全正常，不存在缺字形问题**。
此前观察到的方框只出现在旧的行动栏/聊天输出路径上。
因此**移除探针及其配置项 `debugFontProbe`**（按复核建议，诊断不应进入正式 UI）。

### 修复：顶到上限的生物永远不会成为密友

`addAffinity` 在变化被上限截断（`applied == 0`）时**提前 return**，
跳过了 `checkFriendThreshold`：

```java
int applied = relation.addAffinity(...);
if (applied == 0) {
    return 0;                       // ← 好友阈值检查被跳过
}
checkFriendThreshold(...);          // 永远不执行
```

后果：**凡是「靠顶到上限」达到好感阈值的生物，永远不会被标记为密友**，
面板上显示 `好感：100/100` 却没有 `★密友` ——
这正是实机截图里那匹马的情况。

现已改为：即使变化为 0，也**仍然执行好友阈值检查**。

> 该缺陷只在数值恰好被上限截断时触发，因此很难通过普通测试碰到，
> 是从「满好感却没有密友标记」这一现象反推发现的。

### 面板显示（实机确认正常）

```
猪
好感：45 / 100
█████░░░░░ 熟悉
交配次数：3 █░░░░
```

数值随交配实时更新（截图观测 38 → 45），等级词与色块条均正确。
---
## 0.6.2

采纳 0.6.1 检测器复核的两项 P1。

### P1：切换目标时残留上一只生物的密友标记

`setRequestedTarget()` 在目标变化时只清空了 `hasData` 与 `dataFound`，
**没有清空** `serverFriend` / `serverAffinity` / `serverMatings` / `serverMaxAffinity`。
面板第一行直接读取 `serverFriend()`，因此从 A 切到 B 时，
**B 会短暂显示 A 的 `★密友`**。

两处修复：

1. 真正切换目标时**清空全部服务器缓存字段**（`clearDetector()` 一并补上 `serverMaxAffinity`）。
2. 密友标记的显示条件收紧为
   `hasDetectorData() && dataFound() && serverFriend()` ——
   即使将来缓存清理再有遗漏，也不会在「读取中」状态显示旧关系。

### P1：诊断数字探针改为配置开关

探针行 `0123456789 / - =` 此前**每次渲染都会绘制**，会在正式界面永久显示。
现已改为配置项 `debugFontProbe`（配置界面可见，带说明）。

> **关于是否直接删除的说明**：复核建议直接删除，理由是「字体诊断应使用临时测试分支，
> 不应进入正式 UI」——这个原则我认同。但**数字字形能否渲染**目前仍是本项目唯一未定位的问题，
> 而这条探针是唯一能在最终 HUD 绘制路径上做干净对照的手段。
> 直接删除会使诊断能力归零，重新回到靠间接现象猜测。
> 因此本轮保留但受开关控制；**确认字形后我会把默认值改为 false 并最终移除**。
---
## 0.6.1

### 检测器改为「准星右侧常驻面板」

**不再向聊天栏发送任何检测读数**（包括「读取中」「无关系」）。开启检测器后瞄准生物，
准星右侧出现固定位置的信息面板，移开或关闭即隐藏：

```
马  ★密友
好感：87 / 100
███████░░░ 亲密
交配次数：12 ███░░
```

实现要点：

- 面板**每帧绘制已缓存数据**；`dirty` 只用于标记「文本需重建」，
  **不作为是否绘制的条件** —— 否则消费一次 dirty 后面板就会消失或闪烁。
- 这也是此前「读取中」刷屏的另一半原因：每收到一次回复都置 dirty，
  而消费 dirty 就发一条聊天消息，周期查询于是反复输出。现已彻底移除聊天输出。
- `tickDetector()` 只管目标识别、查询限频与缓存生命周期；绘制在 `render()` 中。
- 面板通过 `currentTargetUuid` 解析实体，**保证显示的名字与数值属于同一只生物**。
- 无目标 / 检测器关闭 / F1 隐藏 HUD / 打开任意界面 / 无玩家或世界时**不绘制**。
- 底板按文本宽度自适应，并夹紧到屏幕边缘，避免长名字或小窗口越界遮住准星。

### 修复：检测距离前后端不一致

客户端探测 **32 格**，而服务端此前在 `interactRange × 2`（默认约 8 格）外就拒绝查询。
于是 8～32 格之间的生物会被显示成「无关系」——
**明明有好感却显示无记录**。现已统一为 **32 格**，两侧代码互相注释引用。

### 诊断：数字字形探针

面板末行临时绘制固定字符串 `0123456789 / - =`。
这是为了在**最终面板坐标**上直接验证数字字形是否正常 ——
此前多次出现「数字显示为方框」，但从未在这条最终绘制路径上单独验证过。
若该行显示为方框，则问题在字体/字形而非数值，将据此改用独立字体资源。
---
## 0.6.0

采纳功能改进方案中的三项。均为功能改进，不改存档格式、不新增 Mixin。

### 一、检测器：刷新更及时，并恢复数字显示

原先读数挂在一个 **2 秒定时器**上：即使服务器回复早就到了，也可能最多 2 秒后才显示。
现在读数**每 tick 运行**，收到回复的**下一次 tick 立即显示**；
查询本身仍有限频（最短 10 tick），不会每帧发包。

显示格式改为「数字 + 色块条」，两者都走聊天栏（数字在聊天栏渲染正常）：

```
马　好感 87/100　███████░░░ 亲密　交配 12 次 ███░░ ★
```

文案全部走语言文件，不再硬编码中文。

### 二、拒绝后按性欲决定是否强制交配

原先只要 `forceMatingOnReject=true`，**任何性欲下拒绝都会立刻强制交配**。

新增两个配置（已加入配置界面）：

| 配置 | 默认 | 含义 |
|---|---|---|
| `forcedMatingEnergyRatio` | 0.75 | 性欲需达到**该生物自身上限**的这个比例才强制交配 |
| `rejectionArousalCooldownTicks` | 1200 | 低于阈值时改为进入这个冷却 |

采用**比例**而非固定数值，因为不同生物的最大性欲不同。
低于阈值时提示「它的性欲还不够高，暂时退开了」，并使用**独立冷却**，
不与普通求欢冷却混用。

### 三、护卫单位：修正威胁识别与目标保持

1. **原先任何存活目标都会让护卫跳过扫描** —— 即使它正在追一只无关的生物，
   也不会去管正在攻击玩家的敌人。现在只有**当前目标仍是真正威胁**时才保持，
   否则重新扫描并切换。
2. **威胁识别扩展**：除「敌对生物且目标是玩家」外，
   新增「**最近攻击过玩家的实体**」也算威胁 ——
   投射物、召唤物、模组生物此前都会被忽略。
   同时排除其他玩家、主人自己与其坐骑、已死亡实体。
3. 调试日志补充「无威胁可打」与「切换目标」两种情况，
   便于区分「没找到威胁」和「设置了目标但被原版 AI 清掉」。

> 关于第 3 点：**是否需要为护卫增加 Goal 注入，要等实机日志确认**。
> NeedsOfNature 的 mixin 配置是 `defaultRequire=1`，贸然新增注入会导致启动崩溃，
> 因此本轮只做目标筛选与日志，不动 AI 结构。
---
## 0.5.3

采纳第三份复核（针对 0.5.2）的建议。

### P1：检测器数据年龄的**单位错误**

`sendChatReadout()` 每约 2000 毫秒调用一次，而 `ageData()` 每次调用只 +1，
却用 `STALE_AFTER_TICKS = 40` 判断过期 —— **把「调用次数」当成了「tick 数」**，
实际约 **80 秒**才判定过期，而非预期的 40 tick（约 2 秒）。

现改为：`ClientTickEvents.END_CLIENT_TICK` **每 tick** 推进年龄，读数只读取年龄。
「显示频率」与「数据年龄」不再混用同一个计时器。

### P1：查询未携带目标 UUID

`affinity_query` 原为空包，服务端收到后**重新用准星解析目标**。
网络延迟或快速转视角时，服务端可能解析出与客户端不同的生物，
回复被客户端 UUID 校验丢弃 → 表现为额外的「读取中」。

现改为 **payload 携带目标 UUID**，服务端据此校验：
实体存在且存活、处于同一维度、距离在交互范围（`interactRange` 的 2 倍内，避免泄漏远处实体数据）。
校验失败返回带该 UUID 的 `found=false`，客户端只接受当前请求目标的回复。

### P2

- 交配次数进度条的满值改为命名常量 `MATINGS_BAR_MAX = 20` / `MATINGS_BAR_BLOCKS = 5`
  （仅设计取值，次数本身无上限）。
- 动画菜单 payload 增加大小保护：最多 128 条、总字符数 4096；
  客户端解析时跳过空行、非法 ID 与超长条目。

### 说明

复核同时确认：无需重做配置界面、Attachment 架构或 NeedsOfNature Mixin。
护卫系统（每 20 tick `setTarget`）暂不改为 Goal 注入 ——
NON 的 mixin 配置是 `defaultRequire=1`，新增注入冲突会导致启动崩溃，风险高于收益。
---
## 0.5.2

### 修复

**检测器读数反复显示「读取中…」，且同一只生物的等级自相矛盾。**

0.5.1 里我把「目标是否改变」的判断放在了每一帧执行，又用 UUID 对象比较身份，
导致看同一只生物时也被判定为「换了目标」→ **每帧清空缓存** →
于是永远退回「读取中…」，并可能短暂显示上一只生物的数据（截图里同一只马先「已满」后「陌生」）。

修正：

- 目标身份改为按 UUID **字符串**比较，语义明确；
- **只有目标真的变化时才清空**缓存；
- 等待同一目标的刷新期间**保留已有数据**，不再退回「读取中」；
- 查询仅在「目标变化 / 数据过期 / 无数据」时发送（过期阈值 40 tick）；
- 「读取中…」只在目标变化时播报一次，不再刷屏；

顺带清理了渲染器里重复定义的 `sendChatReadout` 与遗留的旧签名去重逻辑
（那段逻辑正是 0.5.0 把服务器数据堵死的元凶）。
---
## 0.5.1

采纳第二份审查（针对 0.5.0）中的建议。

### P0：检测器卡在「读取中…」

**这是我 0.5.0 自己引入的 bug。** 读数用「客户端本地值」算了一个签名用于防刷屏，
而客户端本地根本没有关系数据（恒为 0），所以服务器回复到达后签名依然「没变化」，
被提前 return —— 服务器数据**永远显示不出来**。

改为**显式的 dirty 标志**：收到回复即置位，显示一次后清除。不再用值比较判断「有没有新数据」。

### P1

- **区分「无关系」与「有关系但为 0」**：`found` 之前被忽略，现分别显示
  `尚无好感记录` 与真实进度条。
- **回复携带目标 UUID**：迟到的回复不再覆盖新目标的数据。
- **进度条统一**：抽出 `AffinityBars` 共享实现，客户端与服务端共用同一公式
  （此前两边分别按 /100 与 /10 计算）。上限、等级词均改为按配置上限计算，不再硬编码 100。
- **`peekAffinityData` 真正只读**：改用无创建访问，批量扫描不再给无关实体生成空数据。

### P2

- 动画菜单会话**加 200 tick（10 秒）过期**，选择时校验目标是否仍存活；
  玩家断开连接、服务器停止时清理 `MENU_TARGETS` / `LAST_QUERY_TICK`。
- 硬编码中文（「读取中…」「★密友」等）迁入语言文件，服务端不再提前 `getString()`。
- `sendActionBarReadout` 更名为 `sendChatReadout`（它实际发的是聊天消息，名字此前误导）。

### 备注

修复过程中我一次性删多了 `AffinityService` 的方法（8 个），已按原实现完整重建，
构建与 19 项单元测试均通过。这也说明大范围文本替换式改动的风险，后续会改为逐处精确修改。
---
## 0.5.0

本轮采纳了一份第三方源码审查（ChatGPT）中的建议。逐条核实后，确认以下缺陷并修复。

### P1 级

1. **冷却永不递减（逻辑死锁）** —— `PlayerAffinity.tickCooldown()` 有定义但**全项目无人调用**。
   一旦设置冷却，扫描就会永久跳过该关系，生物**只会求欢一次**。
   现已在服务端每 tick 递减（`RequestSystem.tickCooldowns`）。

2. **检测器读的是客户端数据，永远为 0** —— 好感数据存在**服务端**实体 Attachment 上，
   客户端那份实体根本没有关系数据。这才是「好感显示不出来」的**根本原因**，
   与字体、色块都无关。现新增服务器权威查询通道：
   `affinity_query`（客户端请求） / `affinity_data`（服务端回复，含好感、上限、交配次数、密友标记），
   带按玩家的限频；未收到回复时显示「读取中…」，不再把「未知」当成 0。

3. **自动驯服会抢别人的宠物** —— `tameCreature` 无条件 `setOwner`，
   玩家 B 好感满了就能把玩家 A 的狼/马据为己有。现在**已有主人时不再转移**，
   只有无主实体才会被驯服。

4. **进度条两端算法不一致** —— 客户端按 `值/100`、服务端按 `值/10`，
   上限 100 时好感 100 只填 1 格。现统一为 `round(值/上限×格数)`，并使用配置里的实际上限。
   （审查还指出：0/50/100 应分别为 0/5/10 格。）

5. **多个求欢请求会让按钮点错对象** —— 客户端只显示一条提示，
   但服务端允许多只生物挂起请求，点击按钮时可能操作到另一只。
   现限制**每个玩家同时最多一个待答复请求**。

### P2 级

6. **动画菜单未绑定打开时的目标** —— 选择时重新解析目标，原目标走开后会作用到别的生物。
   现在菜单会记住目标 UUID，选择时校验；目标失效则提示重新对准，**不会自动改选**。

7. **后代驯服绕过所有权保护且漏掉马类** —— 改为复用统一的驯服方法（含马类、含所有权保护）。

8. **所谓只读查询其实会创建 Attachment** —— `peekAffinityData` 实际调用了
   `getAttachedOrCreate`。现改为无创建访问，批量扫描不再给无关实体生成空对象。

9. **语言键 `affinity_capped` 丢失** —— 上一版把提示改成硬编码后键被删，
   代码仍在查它，会显示原始键名。已恢复。

### 说明

- 审查指出「`sendMessage(..., true)` 是聊天栏」这个判断不成立：
  该布尔参数是 **overlay**，`true` 为屏幕叠加、`false` 才是聊天栏。已据此更正理解。
- 审查关于「数字方框根因未确认、中文数字只能算临时诊断、应改画真实矩形条」的判断是**对**的。
  本版把数值来源修正为服务器权威后，若方框仍存在，下一步就按建议在 HUD 上绘制真实矩形条。
---
## 0.4.3

### 变更

**好感显示改用「色块条 + 等级」，不再使用阿拉伯数字。**

阿拉伯数字在该渲染路径上会画成缺字形方框。色块用的是与**原版 Boss 血条相同的方块字形**，
本身就一定能渲染，因此不会重蹈覆辙。

- 检测器读数：`马   ███████░░░   亲密 ★密友   ███░░`
  （左侧十格为好感，右侧五格为交配次数）
- 好感变化提示：`♥ 马  ▲ 好感上升  ██████████  已满  (主动交配)`
- 达到上限时：`♥ 马  好感已满  ██████████`

等级文字对应对照：初次见面 / 陌生 / 熟悉 / 友好 / 亲密 / 已满。

> 精确数值仍可通过 `/nonaffinity info` 查看（聊天栏数字渲染正常）。
---
## 0.4.2

### 修复 / 澄清

**「交配不再增加好感」其实早已到上限，不是没增加。**

从实机日志读到：

```
mating recorded for yuan_shao + 马: count=7 affinity=100
addAffinity request: yuan_shao -> 马 delta=65 reason=player_initiated
```

好感**已经是 100（上限）**，而每次交配仍请求 +65，被上限吃掉后实际变化为 0，
代码在变化为 0 时直接返回、不给任何提示 —— 于是看起来就像「加了第一次之后就不加了」。

处理：

1. 变化为 0 且已达上限时，明确提示：
   `<生物> 的好感已达上限（100）。把「每次交配增加好感」调小些（当前 65）才能看到逐步增长。`
2. `sanitize()` 把 `affinityPerMating` / `affinityPerPeak` 限制在 `maxAffinity` 以内，
   避免一次交配就顶满上限。

### 数值显示

阿拉伯数字在该渲染路径上会画成缺字形方框。新增**中文数字**显示作为对照
（`好感 五十二`），中文数字是不同字形；同时仍发一条含阿拉伯数字的聊天行以便比对。
---
## 0.4.1

### 修复

**好感变化提示看不见** —— 被自己的读数覆盖了。

好感变化提示与检测器读数**都发往行动栏**，而检测器每 0.5 秒刷新一次，
于是刚出现的「好感 +13」几乎立刻被读数盖掉。

现已把两类信息都改到**聊天栏**（该通道已多次验证能正确显示数字与中文）：
- 好感变化：`马 好感 +13（当前 52，主动交配）`
- 检测器读数：只在**目标或数值变化时**播报一次，间隔至少 2 秒，避免刷屏
---
## 0.4.0

### 新功能

**按 F 直接弹出动画选择界面**（不再固定播放同一个动画）。

之前只是加了 `/nonaffinity list` + `play` 命令，按键本身仍然会自动挑一个，
所以体验上还是「固定动画」。现在：

1. 对准生物按 **F** → 服务端枚举**所有匹配的动画** → 客户端弹出选择列表
2. 列表显示动画显示名；**条件不满足的条目会置灰并标注 `[条件不满足]`**
3. 点选即开始播放，支持滚轮翻页
4. 保留命令方式作为备用：`/nonaffinity list` + `/nonaffinity play <编号>`

新增 3 个网络包：`animation_menu_request`（请求列表）、`animation_menu`（下发列表）、
`animation_choice`（回传选择）。

这样即使某个内容包缺少某种动画，你也能在列表里直接看出**有哪些可用、哪些因条件不满足而暂时不能播**，
而不是按键之后毫无反应。
---
## 0.3.2

### 修复

**「第一次交配加好感，之后再交配就不加了」** —— 上一版引入的严重 bug。

0.1.1 为避免同一次交配被重复计数，加了按「玩家+生物」的时间去重窗口，
窗口长度是 **100 tick（5 秒）**：

```java
if (now - last < SETTLE_DEDUPE_TICKS) return;   // 5 秒内直接丢弃
```

而一次交配**通常不到 5 秒就结束了**，于是第二次交配的结果被当成
「同一次交配的重复结算」直接丢掉——表现就是只有第一次加好感。

现已改为**按动画实例 id 去重**（精确）：

```java
if (!SETTLED_INSTANCES.add(instanceId)) return;   // 同一实例只结算一次
```

同一实例的多次结束事件仍然只计一次，但**每次新交配都是新实例，都会正常加好感**。
---
## 0.3.0

### 新功能

1. **动画可主动选择**（不再固定播放同一个）。
   原先按键会遍历候选并启动**第一个能启动的**，所以永远只播同一个动画。
   新增命令：

   ```
   /nonaffinity list          列出你与准星生物所有匹配的动画（带编号，标注条件是否满足）
   /nonaffinity play <编号>   播放你选中的那一个
   ```

   列表里灰色的代表「当前条件不满足」（缺空间/方块/水面等），绿色代表现在就能播。
   若某个生物一个动画都没有，会明确提示「可能缺少对应内容包」。

2. **好感度数值改在行动栏显示**（准星上方），并带交配次数。

### 修复

**准星旁的数值一直显示为方框（缺字形方块）。**
经截图比对，同一份数据在聊天栏与配置界面都能正常显示数字，只有那条 HUD 绘制路径会画成方框。
既然数据本身没问题，就把数值改到**行动栏**（与聊天栏同一渲染管线）。
实测截图确认数字正常：`马 affinity 0 matings 0`。
准星旁现在只留一个 `[检测中]` 提示。

> 顺带说明：之前反馈的「主动交配不加好感」经日志核实是**正常的**——
> `mating recorded for yuan_shao + 马: count=1 affinity=100`，马与蜘蛛都正确加了 100 好感。
> 看不到只是因为读数显示不出来。
---
## 0.2.3

### 修复

1. **配置界面滚动后滑块拖不动。**
   原先滚动会 `clearChildren()` 后重建控件，重建出来的控件不再响应鼠标（屏幕早已初始化完毕）。
   现在**控件只创建一次**，滚动仅用 `setY` 移动它们，交互状态因此始终有效。

2. **准星读数里的数字仍然不显示**（显示成 `马 好感  交配 次`）。
   同一份数据在聊天栏能正常显示（`马 好感 +13（当前 52）`），说明数据无恙，问题只在这一行。
   现已改为**每个值各调用一次绘制**（名称 / `[好感]` / `x次数`），不再拼接；
   并加了一行 `raw a=<好感> m=<次数>` 自检，直接从数据模型取值，
   用于区分「数值是空的」还是「数值画不出来」。
---
## 0.2.2

### 修复

**准星读数的文案改走语言文件**，并把交配次数一并显示。
---

---
## 0.2.1

### 修复

**准星读数里数值不显示**（显示成 `好感 马 · 交配 0 次`）。

日志证明结算本身是好的：

```
[NoN Affinity] settling affinity for autonomous mating needsofnature:horsemplayer (yuan_shao + 马)
```

所以问题只在显示：读数原本用 `Text.translatable(...)` 配 `%s` 占位符，
而把 `Text` 对象作为参数传进翻译格式串时，**名称占了位置、数值被吞掉**。
已改为用字面量逐段拼接，数值一定渲染。

同时给结算补了一条可核对日志：

```
[NoN Affinity] mating recorded for <玩家> + <生物>: count=<次数> affinity=<好感>
```

若怀疑次数没涨，看这行的 `count=` 即可判断是「没结算」还是「结算了但显示不对」。
---
## 0.2.0

### 新功能

**好感度检测改为准星显示**（按使用者建议调整）。

原先尝试把好感数值画在**生物头顶**（世界坐标 → 屏幕投影）。这条路反复失败：
手工构造的视图矩阵始终把「相机前方的点」算到相机背后（投影后 `w` 为负），
所有标签都被判定为不可见 —— 这也是之前 `SCAN 0/9` 却看不到任何数字的原因。

现在改为：**开启扫描后，把准星对准生物，就在准星旁显示好感度**。
用射线检测（`ProjectileUtil.raycast`）判断你在看谁，显示位置固定在准星右下角，
**完全不依赖投影矩阵**，因此不会再出现「算了但看不见」的情况。

显示内容：`好感 <数值> · 交配 <次数>`，密友会额外标一个 `*`。
没瞄准生物时提示「好感检测中：请把准星对准生物」，便于区分状态。

### 变更

- 移除了世界空间投影的整套代码（连同诊断探针），改为准星方案。
---
## 0.1.1

本轮以修复为主。

### 修复

1. **生物「强上」不再增加好感** —— 严重 bug。
   原先只有本模组发起的交配才会在结束时结算好感，而 NeedsOfNature 自己发起的交配
   （生物主动走上来、或强行交互）没有登记，因此**完全不加好感**。
   现在监听动画结束时识别「一名玩家 + 一只生物」的组合并结算，
   同时做了 5 秒去重，避免同一次交配被重复计入。

2. **画面上什么都看不到** —— 颜色缺少 alpha 通道。
   所有颜色都写成了 6 位十六进制（如 `0xFFFF55`），而在 ARGB 中这等于 **alpha = 0，完全透明**。
   于是好感度检测器的数字、读数、以及配置界面的项目名全都「画了但看不见」。
   （说明框之所以正常，是因为它用 Minecraft 自带的 tooltip 样式，不经过自定义颜色。）
   已全部改为 8 位 `0xFFxxxxxx`。

3. **好感度检测器状态不稳** —— 状态归属错误。
   `Item#use` 在客户端与服务端**都会被调用**，两侧各有一份独立状态，互相翻转导致状态不稳。
   现在扫描状态**只由客户端线程决定**（用线程判定而非环境判定，因为开发环境下服务端线程也报告 CLIENT），
   服务端只负责播放音效。

4. **求欢等待时间过短**：200 tick（10 秒）→ **600 tick（30 秒）**，并在配置界面开放可调。

5. **多只生物同时求欢**：现在玩家若已在互动中，不再向其堆叠新的求欢请求。

6. 检测器物品图标缺失（补上 1.21 的 `assets/<ns>/items/` 模型定义）；
   配置界面项目名不显示（改两行布局）。

### 变更

- 模组显示名称改为 **自然关系 / Natural Relationship**。
  （内部 id 仍为 `noncreatureaffinity`：id 一旦变更，已有存档中挂在实体上的好感数据会全部失联。）
- 配置界面精简为 20 项关键配置，并支持逐项恢复默认值。
---
## 0.1.0

首个可用版本。

### 新功能

1. **好感度系统**：每对「玩家 × 生物」独立好感，数据随实体持久化，死亡保留。
2. **主动求欢按键**（默认 F，可自由改键）：看向生物按下即可触发交配。
3. **交配增加好感**：主动、被动、生物发起都会结算，可配置增益。
4. **好感达标**：可驯服生物（含马类 `AbstractHorseEntity`）自动驯服；不可驯服者转为会保护玩家的友方单位。
5. **友好生物主动求欢**：性欲与好感双达标后发出请求，玩家可同意/拒绝。
6. **拒绝惩罚**：降低好感并触发一次强制交配（保留攻击脱身手段）。
7. **后代继承**：玩家生育的后代满好感并归属。
8. **好感度检测器**：右键开关扫描，开启后看向生物即可在头顶看到好感数值（不依赖手持）。
9. **图形化配置界面**：ModMenu 入口 + 按键 K，滑块调节全部阈值与增减量。
10. 全部阈值与增减量可通过 `config/noncreatureaffinity.json` 配置。

### 已知限制

- `friendlyAttackDamageMultiplier` 目前**未生效**：1.21.11 的 Fabric API 没有修改伤害数值的事件，
  实现它需要注入 `LivingEntity#damage`，会与 NeedsOfNature 的同名 Mixin 争用宿主，风险高于收益。
- 「等待玩家答复」期间，该生物对玩家的其它 NON 交互也会一并被挡住（这是「必须先问」的必然代价）。


