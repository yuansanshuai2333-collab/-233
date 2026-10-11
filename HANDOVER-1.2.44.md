# 1.2.44 handover
User requested whole audit first, findings preserved, then fixes. Completed review and baseline tests before production edits; preserved finding list and failing logs under H:\杂事\自然关系-1.2.43第二轮整体审查.
One confirmed bug: a registered attack target befriended/tamed between controller scans was protected by CombatEligibility but still allowed by RelationshipBehavior's recorded UUID exemption; actual sonic damage reduced health. Baseline extended probes 98 pass / 3 fail, same root cause; hostile damage positive controls passed.
Fix: TargetRegistry.protectsAssignedTarget resolves recorded assignment owner UUID and reuses CombatEligibility.isProtectedForOwner for matching target only, with non-null owner. RelationshipBehavior checks it before retaliation or any recorded-target exemption. Works below friend stage for familiar defenders too; does not expand unregistered native combat or grant unrelated players' friendships protection. Two production Java files only, version bumped.
542 unit tests / 78 suites, 105/105 server probes, 5/5 without NoN. Source matches isolated build, no probe in JAR. Build path remains I:\DSH\work\audit-1.2.39-recheck. No real game instance changed. No complete client/full-modpack playtest.
Delivery: H:\杂事\自然关系-1.2.43第二轮整体审查\交付-1.2.44\naturalrelationship-1.2.44+1.21.11.jar
SHA256: FE94E4636E54DCE087F8FF8195E9EC3F3C1ECBAA6A2AE324397B8EF29C0DFFA3
Uses 1.2.43 HUD protocol unchanged. Extra-gain feature still canceled. Original findings retained alongside final report and probe sources.

## Subsequent whole-mod audit
No new confirmed reproducible defects. Source unchanged and forced rebuilt artifact byte-identical. 542 tests / 78 suites; 112/112 server probes; 5/5 without NoN; language audit passed (37 options / 74 option keys / 106 visible keys, zero missing or placeholder mismatch). Seven new probes cover native wolf targeting, NBT-restored friend protection, live threshold up/down, assignment target identity and large HUD request ID codec roundtrip. No actual full client playtest. Evidence/report/empty findings: H:\杂事\自然关系-1.2.44整体审查. Keep 1.2.44; no new release.


## Second whole-mod recheck of 1.2.44
Repeated existing full suite without new scenarios: 542 unit tests / 78 suites, 112 server checks, 5 no-NoN startup checks, language audit all pass. No new confirmed defects, no source changes, rebuilt JAR identical. Evidence and empty findings: H:\杂事\自然关系-1.2.44第二轮整体审查. No new client or modpack playtest, no new release.

## Multidimensional audit of 1.2.44
No new confirmed bugs or production changes. Expanded isolated server probes from 112 to 123 with 11 assertions covering offline-owner UUID-index absence, failed claim isolation, load backfill preserving reduced affinity, 1,000-entry NBT roundtrip with owner/mode/anchor, nonfinite and misordered config repair, full large-ID/Unicode trade packet roundtrip and unknown-reason fail-closed behavior. 542 tests / 78 suites, 123 server probes, 5 no-NoN startup probes, language checks all pass. 253 source/resource/version files unchanged against audit baseline; rebuilt JAR retains existing SHA256. Keep 1.2.44. Offline probe is index absence with player entity still present, not a full disconnect; NBT test is not a restart/performance benchmark. No real multi-client or full modpack playtest. Report, empty findings, sources and logs: H:\杂事\自然关系-1.2.44多方面审查.
