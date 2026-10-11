# 1.2.41 handover

Baseline 1.2.40; whole review and findings frozen before edits. Evidence: H:\杂事\自然关系-1.2.40整体审查. Delivery: same directory\交付-1.2.41.
One new confirmed bug in CompanionRecall.tryRecall: fixed search ring could land outside a small configured recall radius and immediately recall again. Added one candidate-distance check using existing needed(owner.squaredDistanceTo(x,y,z), recallDistance); no valid in-range landing means no teleport. Existing collision/chunk/hazard checks unchanged.
Real server probe extended from 47 to 51 checks, using real block platforms and simulated server player. Before 48/51 (3 failures, one root cause); after 51/51. 537 JUnit tests / 77 suites rerun, all pass. Without NoN server: 5/5. Release gate and language audit pass. Source/build hashes match. Jar delta only CompanionRecall.class and fabric.mod.json.
Build location remains I:\DSH\work\audit-1.2.39-recheck (directory name does not indicate current version). Production I:\DSH\work\noncreatureaffinity. Probe copies saved outside production, not bundled. No real user instance altered.
Limits: no GUI client playtest, full animation content pack, or actual Recall Room integration playtest. Prior affinityPerPeak event integration remains unimplemented and explicitly disclosed in report. Do not claim all previous requests complete. Pregnancy and native taming rules unchanged.
SHA256: 59EE0D22B1DD04F5AA3AE7897A09222CA10FCCEA3F5645CA1C7A65317C8FE6CF
