# 1.2.38 handover

Baseline: 1.2.37. Full audit was completed and findings frozen before production changes.
Fixed: AffinityNetworking.resolveTarget entity targeting/occlusion; NonIntegration STARTED cleanup of own pending request after voluntary success. No gameplay redesign.
Production: I:\DSH\work\noncreatureaffinity
Independent verified build: I:\DSH\work\audit-1.2.38
Audit, frozen findings, delivery: H:\杂事\自然关系-1.2.37整体审查
Validation: 533 tests, 76 suites, zero failures/errors; 14 isolated server checks passed; language and release checks passed. Production src matches build src.
Server animation checks invoke synthetic STARTED events against real listeners, not complete animation playback. Recall Room co-install and actual modpack gameplay not tested this round.
Release SHA256: A0F9EDB8E94034032285FF2E769D467DB0F1A356088DCB979149BDCCA63E76CC
Probe sources and runtime are outside production. No probe is bundled in release. No game instance was updated.
