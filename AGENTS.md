# God Wand development

Canonical repository: this checkout, D:/fileluu/Tools/god-wand-1.3.0. Old God Wand folders/worktrees are backups.

- Author gameplay, config, skill timing, models, UI, VFX and assets in common/src/main. All six supported builds consume this source.
- Never hand-maintain copied implementations or edit build/shared as the source of truth. Version/loader API differences live in compat/profiles; native-only bridges and loader entry points stay in their platform src/main trees.
- Read docs/SHARED_SOURCES.md before changing compatibility/build ownership. Keep adapters limited to API differences; common behavior fixes must reach every supported target.
- Run the root wrapper with Java 21. `./gradlew releaseJars` (Windows: `.\gradlew.bat releaseJars`) builds/checks all six targets, selecting the installed Java 17/25 toolchains as needed.
- Supported matrix: Fabric/NeoForge 1.21.1, Fabric/Forge 1.20.1, Fabric/NeoForge 26.3. 1.21.11 remains pending and is not part of the release.
- Terrain planning may use background workers only for world-independent data. Read/change the live world on the server thread; keep tick budgets, chunk-load limits and protected-block checks. See docs/TERRAIN_ARCHITECTURE.md.
- Preserve user edits. Keep version 1.3.2 unless the user requests otherwise. Source may go to GitHub; JARs stay local for the owner's release workflow.
- Build/mock checks are not gameplay, FPS/TPS, shaderpack or multiplayer validation. Do not launch or automate Minecraft or native Computer Use unless the user explicitly requests it. The owner performs manual gameplay tests.
- Do not delegate to Luna.
