# 1.3.0 handover

User authorized only suggestion 2 (flying follower air teleport) and 3 (optional player-to-friend damage protection). Suggestion 1 remains deferred for future friendship token recipe/new item design. No new item, recipe, forced chunk loading, cross-dimension or unloaded-entity recall in production.

Added AerialRecall: BirdNavigation/FlightMoveControl or noncreatureaffinity:aerial_companions entity-type tag identifies flight capability. No-gravity alone is not flight capability. Existing CompanionRecall dispatches to air search when flyingCompanionRecall (default true). Horizontal owner yaw determines rear candidates. Actual body requires air, empty entity space, loaded chunks, border/build-height validity and landing within recall radius. No safe spot means no teleport. Existing follow driver controls eligibility/modes/occupancy; native tameable/horse control remains untouched.

Added FriendDamageProtection at ALLOW_DAMAGE before escape/event bookkeeping. preventFriendDamage defaults false. Requires attacker's own saved friend-stage relationship, respects live threshold, permits defence against a current targeter or recent attacker (100 ticks), bypasses marked replay pairs. Direct/sweeping/player-attributable projectile and other damage enter same server event. Unattributed environment damage is unchanged. This is server configuration, not a per-client multiplayer override.

Both switches are in the config screen with zh/en labels. Singleplayer save applies immediately; dedicated server uses its config/noncreatureaffinity.json. Flight tag can be extended by datapacks; arbitrary custom flight systems are not universally detectable.

Validation: 544 tests / 78 suites; 162 isolated server assertions (123 previous + 39 new); 5 no-NoN startup assertions; 39 option / 78 option key language audit passes. Full release build/gate passes. Actual melee and attributed arrow damage, air recall and follow driver covered. Test fixtures preload/force chunks in isolated world only; hostile-target fixture sets target before friendship so the existing target setter does not cancel setup. Earlier non-supported fire block fixture replaced with persistent cobweb for noncolliding-block safety. No production fixes needed for fixture failures. No full graphical-client, real multi-client or complete modpack playtest.

Delivery/evidence: H:\杂事\自然关系-1.3.0飞行跟随与误伤保护
JAR SHA256: 15F60FDD356ACBAAB99F2C06B4E65BA61A0BCBAAA988FEAAEF10823B245C3BBE
Built in I:\DSH\work\audit-1.2.39-recheck (directory name historical). Production src and build src matched. New JAR contains exactly two new classes (AerialRecall, FriendDamageProtection), one entity-type tag; four modified top-level classes plus config-screen nested classes line metadata, two languages and version metadata. No test probe bundled. No real game instance changed.
