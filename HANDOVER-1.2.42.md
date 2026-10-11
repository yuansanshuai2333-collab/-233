# 1.2.42 handover

User explicitly canceled the previously pending extra-gain feature. Removed affinityPerPeak config/sanitize/UI/range/lang entries and unused AffinityService.recordPeak method. Historical serialized peak_count retained to avoid destructive save migration. No active reward entry remains. This task is canceled/completed, not pending integration.
Confirmed normalized AffinityService source equals 1.2.41 source with ONLY that method deleted; all other settlement code unchanged. Added legacy-config load test preserving existing gains/losses while dropping unknown retired setting on save; adjusted compiled-entry guard test for removed method.
538 tests / 77 suites pass; 51/51 with NoN, 5/5 without. Build/release/lang checks pass. Source/build hashes match. Baseline and logs H:\杂事\自然关系-1.2.42取消额外好感; delivery subdirectory 交付-1.2.42. Build remains I:\DSH\work\audit-1.2.39-recheck. No actual user instance altered. No full content-pack/client/third-party integration playtest.
SHA256: F97C9F0FF75DDE970A4F0DA0687A7D18577F288B66F14A3C8E8B4EB267654351

## Subsequent whole-mod audit
No new confirmed reproducible defects. Production source unchanged; forced rebuild byte-identical to delivered 1.2.42. 538 tests / 77 suites; expanded NoN server checks 60/60; standalone 5/5. Added native successful-taming entry probes for cat, parrot, nautilus (not full feeding/client playtests). Evidence and final report: H:\杂事\自然关系-1.2.42整体审查. No version bump or new release needed. Extra-gain feature remains canceled.


## Targeted old-issue audit (supersedes blanket confidence)
Special mob gaps confirmed: Warden isValidTarget accepts friend and updateAttackTarget writes friend into attack memory; Warden/Wither still accept uninvolved cow. Ordinary canTarget(player) is blocked. No full damage playtest. Requests: online-index-valid control and removal/range/upstream-cancel cleanup pass, no real client HUD test. 71 assertions match expected observations, including 4 OBSERVED bug-presence checks (not a clean bill of health). Production unchanged; user asked to check. Evidence: H:\杂事\自然关系-1.2.42旧问题专项复查\专项复查结论.md.

