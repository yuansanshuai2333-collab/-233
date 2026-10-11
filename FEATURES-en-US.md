# Natural Relationships 1.0.42

Natural Relationships is a creature relationship and companion addon for Minecraft 1.21.11 on Fabric. It stores affinity and ownership, adds four companion modes, and provides an affinity detector, status feedback, and configurable settings.

## Implemented features

### Relationships and ownership

- Per-creature, per-player affinity records, friendship status, and related statistics.
- Configurable affinity bounds and friendship threshold; defaults are 0–100 and 100 respectively.
- Companion binding at the friendship threshold, relationship synchronization after vanilla taming, and a backfill path for existing pets.
- Ownership checks prevent taking another player's companion or pet. Conflicting records are not silently overwritten.
- Persistent companion ownership, movement mode, and waiting location.

### Four companion modes

| Mode | Implemented behavior |
|---|---|
| Follow | Follows the owner and assists with owner attack/damage events when the creature supports combat. Attempts safe same-dimension recall beyond 10 blocks by default. |
| Wander | Retains natural movement without following or long-distance recall; can still respond to damage and actual threats. |
| Wait | Holds an assigned area, does not follow the owner, and permits nearby self-defense without sustained pursuit. |
| Active | Combines following with responses to actual threats against the owner; it does not indiscriminately attack every hostile mob type. |

Sneak and right-click with an empty main hand to cycle modes. Mobile orders clear a tameable creature's sitting state. Duplicate interaction callbacks are suppressed. Low air gives the creature's own breathing movement priority.

The combat whitelist currently covers hostile mob classes, wolves, and cats, using their native attack AI. Other creatures, including horses, currently receive movement control only. Automatic combat excludes players, tamed pets, and friendly targets. Whitelist membership does not mean every species has been individually tested.

### Detector, items, and settings

- Craftable Affinity Detector with a right-click scan toggle and client-side relationship labels.
- Affinity change messages, request-status HUD, a Friendship Token item, and related tracking.
- English and Simplified Chinese resources; a configuration screen opened with K by default, plus optional Mod Menu integration.
- Separate `config/noncreatureaffinity.json` configuration for affinity, companion distances, display options, and other rules.
- Information, administration, and diagnostic commands, including `/nonaffinity info`, `set`, `friend`, `reload`, `companion info`, and `companion clear`. Modification commands require appropriate permissions.
- The `friend` command checks ownership and configuration before modifying relations, then verifies the actual binding before reporting success.

### Other existing systems

Existing code also includes NeedsOfNature event integration, an interaction-selection interface, request handling, completion/interruption tracking, and reward/relationship records. These belong to the upstream adult-content integration; this overview lists their technical scope without describing content or interaction procedures. Available upstream content depends on the installed base mod and content packs.

An offspring relationship system infers a parent from nearby adult creatures of the same type when a baby loads, then applies configured affinity, ownership, and generation inheritance. This is proximity-based inference, not an exact genealogy system, and not every spawning scenario has been validated.

## Environment and dependencies

| Component | Requirement / checked combination | Role |
|---|---|---|
| Minecraft | 1.21.11 | Required; Fabric edition |
| Java | 21 or newer; checked on 21 | Required |
| Fabric Loader | Declared minimum 0.18.2; checked on 0.19.5 | Required |
| Fabric API | Declared minimum 0.141.6; checked on 0.141.6+1.21.11 | Required; use the matching game build |
| NeedsOfNature | Checked version: 1.5.1+1.21.11 | Recommended in addon metadata; required for its integration features |
| GeckoLib | At least 5.4.5 and below 6; checked on 5.4.5 | Required by NeedsOfNature |
| Mod Menu | Installed test instance: 17.0.1-beta.1 | Optional settings entry point |
| Trinkets | Installed test instance: 3.11.3 | Optional; not required for companions |

These versions were checked against project metadata and locally installed JARs, not a latest-version catalog. Core companion server checks ran without NeedsOfNature in an isolated environment; upstream integration requires the base mod and its dependencies. Development probe JARs are not release dependencies.

## Release and validation status

1.0.42 continues rc.42 with updated display branding, settings titles, and version metadata. Gameplay code remains that of rc.42. The internal ID `noncreatureaffinity`, item identifiers, and configuration path are retained for compatibility. Renaming does not establish additional compatibility guarantees.

Previous rc.42 validation passed 57 relevant automated tests and 32 isolated server checks, covering real movement, melee, sustained skeleton hits, breathing, companion commands, and saved mode data. The user's rc.41 log showed normal operation and a completed save. See the accompanying inspection report for the renamed build's results.

Full client reload behavior, riding transitions, complex terrain and dimension changes, and extended multi-companion play still need practical validation. Serialization tests do not validate every real-world save scenario. Compatibility with every creature, content pack, or modpack is not guaranteed.

## Installation and upgrade

Close the game, back up important saves, and place `naturalrelationship-1.0.42+1.21.11.jar` in the instance's `mods` folder. Remove the previous version of this addon while retaining the upstream base mod and dependencies. Do not install rc.42 and 1.0.42 together. Multiplayer clients and servers should use matching addon versions. Building this release does not automatically modify the player's instance.

The renamed 1.0.42 build passed all 249 existing automated tests across 33 test classes, with zero failures, errors or skipped tests. See the [inspection report](INSPECTION-1.0.42.md).
