# One source, six builds

## Normal development

Edit gameplay, skill timings, damage/configuration, models, menus, effects and assets in `common/src/main/`. Add a new common Java class or asset there: all six builds receive it automatically. The canonical release version is only `gradle.properties` at the repository root.

Run from the root:

```powershell
.\gradlew.bat releaseJars
```

This generates version adapters, runs tests and builds Fabric/NeoForge 1.21.1, Fabric/Forge 1.20.1 and Fabric/NeoForge 26.3. Install JDK 17, 21 and 25. The checked-in daemon criteria select Java 25 to run Gradle, even when the wrapper starts from Java 17 or 21. Compiled game targets remain Java 17 for 1.20.1, Java 21 for 1.21.1 and Java 25 for 26.3. Add `--offline` when dependencies are cached. No Minecraft instance is launched. No JAR is published to GitHub.

The source of truth is not a generated copy. `build/shared/<profile>/src/main/` is disposable build output. Do not commit or maintain that output. The old duplicated common files have been removed from each port's `src/main/`.

## What stays version-specific

| Location | Responsibility |
| --- | --- |
| `common/src/main/` | Gameplay and ordinary skill/UI/VFX edits, once for every target |
| `compat/profiles/fabric-1.20.1/` | Java 17/1.20.1 API and Fabric-specific adapter hunks |
| `compat/profiles/forge-1.20.1/` | Java 17/1.20.1 API and Forge-specific adapter hunks |
| `compat/profiles/mc-26.3/` | Java 25 native rendering, item/screen/input, registry and damage API hunks |
| `fabric/`, `neoforge/`, `fabric-1.20.1/src/main/`, `forge-1.20.1/src/main/` | Loader registration/network entry points and metadata |
| `ports/mc-26.3/common/src/main/` | Native-only bridges and mixins that have no canonical 1.21.1 counterpart |
| `ports/mc-26.3/fabric/`, `ports/mc-26.3/neoforge/` | Native loader lifecycle, render events, networking, access rules and metadata |
| Per-version `src/test/` | Tests against that Minecraft API; root common tests remain the primary behavior suite |

Adapters are explicit JSON before/after hunks. Builds reject a missing/ambiguous anchor, shared Java exclusions, or a local file shadowing canonical source. They never silently fall back to old port code. An error gives the profile and file that needs attention. Common behavior changes should not be recorded as adapter differences.

Most routine changes need only the canonical file. A new Minecraft API call or render lifecycle change can require a small adapter edit. Adding a skill still requires registering any genuinely new payload/entity/render type in each loader's existing registration hook; do not implement the skill algorithm separately per version. A single download across Minecraft versions is a separate packaging choice and is not required for this shared-source build.

Minecraft 26.3 rendering has two native contracts: Fabric procedural GPU uploads/fences run at `LevelRenderEvents.END_MAIN`, after vanilla closes its main render pass; NeoForge uses `AfterLevel`. Native item layers still apply vanilla's `-0.5` origin shift, so the canonical renderer's `+0.5` compensation must remain. The native model-origin regression test exercises the complete extracted vertex path for both hands and the inventory view.

## Updating an API adapter

The 26.3 GPU bridge uses RenderPearl's indexed draw order: index count, instance count, first index, vertex offset, first instance. Its command-submission test checks a real extracted quad through `flush`; geometry-only tests cannot detect an empty GPU draw.

When a conflict is real, inspect the named `compat/profiles/<profile>/<java-or-resources>/<path>.json`. Update its small `before`/`after` hunks for the current canonical code. Preserve the shared algorithm and limit the difference to the API boundary.

For a larger adapter edit, create an updated desired file at its generated path, then explicitly capture that one file (Python 3 is optional maintainer tooling, not needed for normal builds):

```powershell
python tools/capture-compat.py mc-26.3 java/net/godwand/skill/AtomicCrater.java
.\gradlew.bat releaseJars
```

Inspect the JSON diff before accepting it. Do not regenerate every adapter to hide a gameplay conflict. `manifest.json` documents native resource exclusions and the legacy `recipe`/`advancement` directory renames.

## Verification limits

A successful aggregate build proves generation, Java/API compilation, automated tests and packaging for six targets. It does not prove visual equality under shaderpacks, FPS, all boss-mod internals or multiplayer behavior. Minecraft 1.21.11 remains pending and is excluded from this matrix.
