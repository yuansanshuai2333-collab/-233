# 1.2.40 handover

Baseline 1.2.39. Findings frozen before production edits in H:\杂事\自然关系-1.2.39整体审查\本轮整体复查\问题清单-修复前冻结.md.
Fixed: CompanionFollowService evicted all active DRIVING records above 512, including other dimensions; now unload/stop lifecycle owns cleanup. PATH_FAILED diagnostic cap retained. Config numeric slider handles clamped to 0..1 while labels read real values, preserving legal file-only values until editing. Updated automatic-defense descriptions in both languages; no combat-rule change.
Version 1.2.40. Production repo I:\DSH\work\noncreatureaffinity; isolated build I:\DSH\work\audit-1.2.39-recheck; delivery H:\杂事\自然关系-1.2.39整体审查\本轮整体复查\交付-1.2.40.
537 unit tests / 77 suites passed; 47/47 server probe checks with NoN; 5/5 without NoN. Production/build src hashes match. Release and language gates passed. Jar diff: only 5 class files (config screen + 3 inner sliders + follow service), 2 language resources and fabric.mod.json changed.
Baseline server probe 46/47: >512 record retention across dimensions failed; after fix 47/47. Population threshold simulated with 512 UUID records plus a live Nether entity; not a 513-entity performance benchmark. Probe saved outside production and never packaged.
Initial GUI-constructor test failed due to absent MinecraftClient in plain JVM; retained separately, replaced with pure numerical tests. No client visual test and no full content-pack/Recall Room gameplay validation. Existing animation-related probe callbacks remain synthetic.
Outstanding from previous discussion: affinityPerPeak event is still not wired. This release does not implement it. Do not claim all prior tasks complete. No pregnancy mechanics changed.
SHA256: 2894CBB1CA094856C87D001C2E05BA41214CC25E42E1C3681BD53A3359D37D8E
