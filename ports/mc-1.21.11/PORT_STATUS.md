# Current status — paused for the 1.3.2 boss fix (2026-10-29)

The owner requested this port, then explicitly prioritized the four existing 1.20.1/1.21.1 releases after reporting boss-damage failures. No 1.21.11 JAR is ready. Do not release or claim support.

The local prototype was backed up under the canonical .local-backups/ before replacing its old source with the latest 1.3.2 / 26.3 source. Java 21 target metadata, the current license, shared native rendering and all skills are retained. Native 1.21.11 geometry/pipeline, post-chain, camera and GUI migration has started but common compilation still fails. It requires API fixes, native forward-depth shader conversion, both loader integrations and meaningful migrated tests. The source remains excluded from Git except for this report until buildable; no current release source was replaced.

Before resuming: finish and owner-test the four release variants, then bring their new SpellDamage helper, damage-type/tag resources, all five damage managers and boss tests into both the 26.3 and 1.21.11 ports. The current port prototypes predate that boss patch. Keep one JAR per Minecraft version/loader for now; no universal packaging was implemented.

---

# Minecraft 1.21.11 port investigation

Status: PENDING BY OWNER REQUEST (2026-10-28). Finish Fabric 26.3 first. NOT SUPPORTED / NOT RELEASEABLE. This is an isolated full-source port prototype, not an installable port. No gameplay was launched.

The complete 1.21.1 common, Fabric and NeoForge source/resources/tests were copied here before upstream per-tier balance changes on 2026-10-28. Do not treat this snapshot as the latest balance implementation.

Dependencies verified against official Maven metadata: Minecraft 1.21.11, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Loom 1.14.10, NeoForge 21.11.48; wrapper Gradle 9.2.1, Java 21. Target mod version 1.3.2.

## Concrete preservation blockers

- `AtomicPostFx` and `GodWandPostFxManager` depend on the removed PostChain constructor, `resize`, `setUniform`, per-pass `getEffect`, `setOrthoMatrix`, and `process(float)`. The real mapped 1.21.11 class exposes `PostChain.load`, `addToFrame(FrameGraphBuilder,...)` and `process(RenderTarget,GraphicsResourceAllocator)` instead. The effect shaders and their dynamic uniform data must migrate to GPU buffers/render pipelines/framegraph targets. `api-rendering.txt` records actual `javap` output from the mapped Minecraft JAR.
- Atomic and the eight other world effect renderers use immediate RenderSystem shader/state calls. Those APIs have been removed, and render work now separates extraction from drawing. All geometry and blend/depth behavior need porting while preserving effects.
- The item and entity renderers, GUI/HUD extraction, and Fabric/NeoForge event adapters must migrate together. Simply replacing old callback names cannot establish correct rendering behavior.
- Old GameRenderer mixins target a LevelRenderer.renderLevel descriptor that is no longer present; camera and hand suppression mixins must be checked against actual target bytecode after the renderer rewrite.
- Save-data/NBT and interaction APIs also changed. Spatial terrain restoration data must migrate with an explicit compatibility test; silently discarding saved displaced blocks is unacceptable.

There are no blank renderer stubs, suppressed mixin requirements, reduced spell implementations, or placeholder shaders here. Existing four release variants outside this folder remain untouched by this investigation.

## Primary references

- https://fabricmc.net/2025/12/05/12111.html
- https://docs.fabricmc.net/1.21.11/develop/rendering/world
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- https://meta.fabricmc.net/v2/versions/loader/1.21.11
- https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml

## Reproduce

Run `./gradlew.bat :common:compileJava --console=plain` in this folder. Full loader build command after completing the actual source migration is `./gradlew.bat build --console=plain`.

## Verification on 2026-10-28

`./gradlew.bat :common:compileJava --console=plain` fails with **551 compiler errors and 90 warnings**, with compiler maximum raised to 1000 so this is not the original default-100 truncation. First dependency/remap setup took 3m58s; cached full-diagnostic rerun took 6s. See `compile-full.log` and `compile-affected-files.txt`.

Examples include ResourceLocation renamed to Identifier, removed BufferUploader and GlStateManager, EntityRenderer requiring a render-state type, missing old Input/ToastComponent, and Optional-returning NBT accessors. These errors include related/cascading diagnostics, not 551 independent design changes.

No loader JAR, successful build, automated gameplay proof or native visual verification is claimed. Completing this port while retaining its six skills requires a dedicated renderer/persistence migration and subsequent manual gameplay validation.

A separate diagnostic loader command `./gradlew.bat :fabric:compileJava :neoforge:compileJava -x :common:compileJava --continue --console=plain` compiles the copied common sources directly in each loader; the exclusion bypasses the already-failing standalone common compile only and is not a release build command. Fabric reaches javac and fails with **569 errors**, including old BuiltinItemRendererRegistry and old WorldRenderEvents imports, plus the shared source failures. See `compile-loaders.log`.

The shared compile errors touch **53 distinct Java source files**. Block-entity restoration specifically fails because `BlockEntity.loadWithComponents` now takes `ValueInput`, not `(CompoundTag, HolderLookup.Provider)`. The port must preserve saved displaced terrain and block-entity inventories rather than bypassing that compile failure.

NeoForge also completes its real NeoForm/Minecraft setup, reaches `:neoforge:compileJava`, and fails with **579 errors**. Combined diagnostic loader run fails in 4m42s. Fabric reports 569; NeoForge reports 579; these counts include each loader compiling the common sources and must not be added as independent problems. No supported 1.21.11 loader variant was produced.
