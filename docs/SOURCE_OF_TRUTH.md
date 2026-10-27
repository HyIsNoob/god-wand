# Source of truth

This repository is the consolidated God Wand 1.3.0 development source as of 2026-10-27. Continue feature work here.

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
| `god-wand` | Frozen pre-integration multiloader/Antigravity input. |
| `god-wand-atomic` | Frozen standalone Atomic input. |
| `god-wand-1.2.0` | Historical release checkout; the folder name alone does not prove its internal version. |
| `godwand-130-integration/god-wand` | Previous integration working tree, used as the source of this consolidation. |

The original multiloader and Atomic inputs matched their integration snapshots byte-for-byte when checked. Their files were not overwritten or moved. The old integration working tree was backed up before copying its tracked and untracked source into this independent repository. Existing Git history is retained.

The obsolete root `src/`, debug screenshots, anime/reference images and historical chat/handoff plans are absent from the new working tree. They remain in the original folders and backup/history. Current production source is only in the five directories listed in DEVELOPMENT.md. No personal worlds, launcher installations, caches or development QA jars were copied.

## Validation limits

Build logs belong to this checkout's build directories. Latest gameplay visuals are still awaiting owner testing. A previous automated run was interrupted with Escape; its optional development probe then asserted that the skipped waiting camera had frozen. That assertion is not part of the production mod and is not evidence of a release-jar crash.

Dedicated development-server boot checks from the older integration do not establish multiplayer behavior for every later change. Custom Atomic SFX and the remaining audit work are still pending. Publishing source does not mark the mod as a tested final release.

The consolidated releaseJars build passed for all four variants. The updated common JUnit suite passed 199 tests with zero failures/errors. Packaged jars contain the MIT license and no QA probe or ModMenu compile stubs. Build logs and checksums are kept locally under build/.
