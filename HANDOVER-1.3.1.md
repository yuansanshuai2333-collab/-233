# 1.3.1 handover

User required a whole-mod check, preserving all findings before fixing. Reviewed gameplay/flight/damage, affinity stages/rewards, request/HUD/network lifecycle, saves/ownership/inheritance, trades, config/client state and optional NoN startup. Preserved baseline, initial suspected finding, finalized pre-fix list and failure log under H:\杂事\自然关系-1.3.0整体审查 before production changes.

One confirmed defect: AerialRecall always preferred at least 2.5 blocks behind, but companionRecallDistance accepts 2.0, leaving no eligible landing even for a bee in clear air. Baseline extended probes: 162 pass / 3 fail (same root cause: no landing, hence no overlap/repeat postconditions). Existing 544 unit tests passed.

Fix only in AerialRecall.java: preferred rear offset now min(2.5, radius - 0.05), still maxed against body-width clearance. Normal default radius retains the old offset. Tight radius supports small flyers and diagonal rounding; oversized flyers still refuse safely. No forced chunk loading, new item/recipe or remote recall; request 1 remains deferred. Friend-damage protection and other gameplay unchanged.

Final validation: 544 tests / 78 suites, 167/167 server probes (5 new assertions), 5/5 no-NoN startup checks, language check (39 options / 78 option keys / 106 visible keys) passed. Source/build comparison matches; JAR diff from 1.3.0 changes only AerialRecall.class and fabric.mod.json. New probe assertions preserved with report, not bundled in the JAR.

Delivery: H:\杂事\自然关系-1.3.0整体审查\交付-1.3.1\naturalrelationship-1.3.1+1.21.11.jar
SHA256: 5D71920F72849F9279717C0387C19CA23102B00461AF0AFFB95ACCAB2F425240
Historical build directory: I:\DSH\work\audit-1.2.39-recheck. No real game instance changed. No complete GUI client/full-modpack or real multi-client playtest. No confirmed unresolved issue from this audit.

## Subsequent whole-mod audit
No new confirmed defect; no production changes. Expanded server probes 167 -> 176 with 9 assertions: fractional-coordinate rear recall across eight yaw headings, straight-up/down pitch, unrelated damage protection/data noncreation, exact self-defence expiry, other-player targeting isolation, and player exclusion. All 544 unit tests / 78 suites, 176 server assertions, 5 standalone startup assertions, language checks pass. 256 source/resource/version files match baseline and isolated build; rebuilt JAR remains byte-identical. Keep 1.3.1. Evidence and zero-item findings: H:\杂事\自然关系-1.3.1整体审查. No full graphical-client/modpack/real multi-client verification.

## Second subsequent whole-mod audit
No new confirmed defects; no production changes or new version. 9 new server assertions cover flying follow demotion/restoration, unbinding/rebinding, NBT-restored friend protection and player isolation, unrelated replay actor exclusion, overlapping marked-session isolation and restoration after both end. 544 tests / 78 suites, 185 server checks, 5 no-NoN startup checks and language audit pass. 256 source/resource/version files match baseline and build. Rebuilt JAR remains byte-identical. Full evidence and zero-item findings: H:\杂事\自然关系-1.3.1第二轮整体审查. NBT test is not a full restart; replay tests use synthetic registry sessions, not actual concurrent Recall Room animations. No real multi-client or complete client/modpack playtest.

## Third subsequent whole-mod audit
No new confirmed defects, no production changes. Added 6 server assertions: second flyer safe distinct rear landing, first flyer unchanged, real vehicle setup precondition, no recall while riding, recall resumes after dismount. 544 unit tests / 78 suites, 191 server assertions, 5 no-NoN startup assertions and language checks passed. 256 source/resource/version files unchanged and match build; rebuilt JAR identical. Keep 1.3.1. Evidence and zero-item findings: H:\杂事\自然关系-1.3.1第三轮整体审查. Riding relationship was constructed in the isolated server, not new gameplay. No real multi-client/full GUI/modpack playtest.

## Fourth subsequent whole-mod audit: three new coverage categories
User required at least three new categories before aggregate fixes. Added BoundaryProbe (audit only): negative fractional/chunk-boundary air recall (3 assertions across 3 coordinates), malformed saved identity/mode/cooldown/quota handling (4), two-player trade registry syncId/requestId collision and independent cleanup (3). All 544 unit tests / 78 suites, 201 server assertions, 5 no-NoN startup checks and language audit pass. No new confirmed defect, no production changes, keep 1.3.1. 256 source/resource/version files unchanged and match build; rebuilt JAR identical. Evidence, baseline and zero-item findings: H:\杂事\自然关系-1.3.1第四轮整体审查. Trade registry tests are not real simultaneous client trading; malformed NBT coverage is limited to named fields; no full GUI/modpack/real multi-client playtest.

## 2026-09-25 十类新增覆盖整体复核
新增十类服务端场景、23 项断言，服务端总计 224/224；单元测试 544/544（78 套件），无自然之需独立启动 5/5，语言核对通过。未确认新的运行 bug，生产源码及版本维持 1.3.1，构建 JAR SHA256 仍为 5D71920F72849F9279717C0387C19CA23102B00461AF0AFFB95ACCAB2F425240。
证据与范围限制：H:\杂事\自然关系-1.3.1十类覆盖审查\整体检查报告.md。先完成整体检查后确认修复清单为空；无生产修复。未进行完整图形客户端及用户整合包长时间联机验证。
