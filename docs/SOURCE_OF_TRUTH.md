# Source of truth

This repository is the consolidated God Wand 1.3.0 development source as of 2026-10-27. Continue feature work here. Current development version: 1.3.2, following release 1.3.1; the repository folder name remains god-wand-1.3.0.

## Included

- The integrated V skill wheel, K graphics/model settings and server modpack options.
- Eclipse, enlarged Aqua Heartia and the latest reference-based Shadow Blade.
- Accepted Atomic choreography and interactive hold/release, blade convergence/trail, and the thin continuous outward aura from revision 12.
- All four supported Minecraft/loader variants, including the 1.20.1 vertex-format fix.
- Audit fixes for malformed server config, restoration conflicts, dimension blacklist enforcement and display-outline scope.
- MIT licensing selected by the owner; all current loader metadata and the LICENSE file agree.
- Latest feedback: convergence streams now descend from above and rise from the foot sigil; they detach into a sword trail before the sideways windup. Gathering has a subtle shake. Wide-camera range is capped at the eight-chunk framing across profiles, and Atomic's maximum crater depth is 28 blocks instead of 14, still controlled by the terrain-damage option.

## Older folders

| Folder | Role |
| --- | --- |
| `god-wand` | Retained only because an older protected chat checkout still uses its shared Git directory; not the current development source. |
| `god-wand-atomic` | Retired worktree; source, worlds and selected QA evidence are archived locally. |
| `god-wand-1.2.0` | Retired historical release worktree; source is archived locally. |
| `godwand-130-integration/god-wand` | Previous protected chat checkout; generated data has been archived and its guidance points to this repository. |

The original multiloader and Atomic inputs matched their integration snapshots byte-for-byte when checked. All old sources, five local worlds, configuration, older jars and selected QA screenshots were archived before retiring worktrees. A complete Git history bundle was verified. Existing history is also retained in this independent repository.

The obsolete root `src/`, debug screenshots, anime/reference images and historical chat/handoff plans are absent from the new working tree. They remain in the original folders and backup/history. Current production source is only in the five directories listed in DEVELOPMENT.md. No personal worlds, launcher installations, caches or development QA jars were copied.

## Validation limits

Build logs belong to this checkout's build directories. Latest gameplay visuals are still awaiting owner testing. A previous automated run was interrupted with Escape; its optional development probe then asserted that the skipped waiting camera had frozen. That assertion is not part of the production mod and is not evidence of a release-jar crash.

Dedicated development-server boot checks from the older integration do not establish multiplayer behavior for every later change. Atomic visuals have user approval. Authored SFX is integrated; in-game audio testing and the remaining audit work are pending. Publishing source does not mark the mod as a tested final release.

The consolidated releaseJars build passed for all four variants. The updated common JUnit suite passed 199 tests with zero failures/errors. Packaged jars contain the MIT license and no QA probe or ModMenu compile stubs. Build logs and checksums are kept locally under build/.

## 1.3.2 / Fabric 26.3 follow-up (2026-10-28)

The four existing variants build as 1.3.2 with per-charge balance settings and 5 normal attack damage. Fabric 26.3 is migrated under ports/mc-26.3 with its own Java 25 build, source/resources/tests and wrapper. Its automated checks and packaging pass; owner gameplay testing remains pending. Root releaseJars does not build that port. NeoForge 26.3 and Minecraft 1.21.11 are pending by owner request; see ports/README.md. This section supersedes older counts and the five-directory source-layout statement above.
