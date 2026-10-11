# 1.2.39 handover

Baseline: 1.2.38. Findings frozen after whole-module audit, before production edits.
One confirmed defect: allowVanillaTamedCreatures=false was ignored at both new vanilla-taming hooks. Added config guards to VanillaTameRelationshipMixin and VanillaHorseTameRelationshipMixin. Existing data, native taming/ownership/control and addon-created relationship paths are unchanged.
Production: I:\DSH\work\noncreatureaffinity
Verified isolated build: I:\DSH\work\audit-1.2.38-recheck
Evidence and delivery: H:\杂事\自然关系-1.2.38整体审查
533 unit tests / 76 suites passed. Before: 39/41 server checks; failures at disabled wolf/horse automatic relations. After: 41/41 with NoN; 5/5 without NoN. Build/release/language checks passed.
Probe target fixture explicitly sets headYaw, because LivingEntity.getYaw(float) reads it, not the yaw set by setYaw. Original failure/diagnostic logs retained. No targeting production change this round.
WholeProbe animation STARTED is synthetic; no full animation content pack or Recall Room co-install gameplay validation. Standalone init removes only modLocalRuntime dependency in isolated run; production build.gradle unchanged.
Production src matches isolated build src. Release changes only two tame hook classes and version metadata. Probe sources kept outside production and not bundled. No game instance updated.
Release SHA256: 690DC51198A777053BB7FA2D47F9A127BA8A5948949F1241C67ACC564694CBDB
