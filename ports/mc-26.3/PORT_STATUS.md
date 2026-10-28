# Minecraft 26.3 port status: feasibility spike only

**Incomplete. No successful Fabric or NeoForge build, no installable jars, no gameplay validation.** The canonical release build must not include this directory.

Full source/resources/tests were copied intact from canonical God Wand 1.3.1 commit `3bc4b2f30eb5c36730c4147e20c7847ecee66a5e` on 2026-10-28. All six skills, procedural geometry, shaders, cameras and sounds remain present. A Git blob comparison verified all 134 copied Java files match the baseline commit exactly. No features were blanked or stubbed. Changes are confined to `ports/mc-26.3`; supported targets remain separate.

## Published toolchain and local setup

- Minecraft 26.3; installed Java 25; Gradle wrapper 9.8.2.
- Fabric Loom 1.17.21, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3.
- NeoForge 26.3.0.99, ModDevGradle 2.0.148.
- Removed the old mappings dependency and `modImplementation`; changed access-widener namespace `named` to `official` for the unobfuscated game.

Official references: [Fabric 26.3 announcement](https://www.fabricmc.net/2026/09/15/263.html), [Fabric 26.3 example](https://github.com/FabricMC/fabric-example-mod/tree/26.3), [NeoForge 26.3 MDK](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle), [26.3 migration primer](https://docs.neoforged.net/primer/docs/26.3/), [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml), [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml).

## Actual compilation attempt

Run from `D:/fileluu/Tools/god-wand-1.3.0/ports/mc-26.3`:

```powershell
$env:JAVA_HOME = 'C:/Users/khang/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
.\gradlew.bat :fabric:compileJava :neoforge:compileJava --continue --console=plain
```

After correcting the initial access-widener namespace failure, `:common:compileJava` reached javac and failed at its first **100 errors**, with 82 warnings. This is the diagnostic cap, not the total number of breaks. The shared failure blocks both requested loaders. NeoForge's artifact task separately resolved 26.3.0.99 and downloaded/preprocessed the 26.3 game jars. Full local output is `build/compile-initial.log` (ignored build evidence).

Concrete compiler failures include missing `MultiBufferSource`, `GlStateManager`, `BufferUploader`, `Tesselator`, `VertexFormat`, `GuiGraphics`, `org.lwjgl.glfw.GLFW`, `ResourceLocation`, `InteractionResultHolder`, `UseAnim`, `Input` and `ToastComponent`.

## API evidence from the actual 26.3 game jar

Using installed JDK `javap -classpath C:/Users/khang/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar <class>`:

- `PostChain` now exposes `load(PostChainConfig, TextureManager, Set<Identifier>, Identifier, Projection, ProjectionMatrixBuffer)`, `addToFrame(FrameGraphBuilder, int, int, TargetBundle)` and `process(RenderTarget, GraphicsResourceAllocator)`. The old constructor/resize/temp-target/setUniform/process(float) path used by Atomic and Supernova is absent. `PostPass` now accepts a Renderpearl pipeline, uniform map and input definitions.
- `GameRenderer.renderLevel()` has no arguments; `getFov()` returns `float` with no arguments. The copied camera/FOV mixin targets use the older signatures and must be revalidated against bytecode.
- `Item.use(Level, Player, InteractionHand)` returns `InteractionResult`; `releaseUsing(ItemStack, Level, LivingEntity, int)` returns `boolean`; tooltips use `TooltipDisplay` and `Consumer<Component>`. The copied item uses older signatures.

This requires a real geometry/render-submission and post-processing/uniform-buffer migration, plus SDL input, GUI, item/entity/persistence/network changes and mixin validation. Metadata changes or no-op compatibility shims cannot preserve the requested effects.

## Follow-up boundaries

1. Complete both loader integrations, rendering, independent Atomic/Supernova post chains, camera/input restoration and every skill before publishing support.
2. Synchronize the parent task's final balance/melee changes: `GodWandConfig`, `GodWandItem`, affected skill managers/entities, loader item registrations and corresponding tests. This snapshot predates those edits.
3. Require both loader builds, relevant migrated tests, packaged resource/service checks and dedicated-server verification before producing release jars.
4. User-run gameplay must then verify visual parity, runtime mixins, shader/GPU compatibility and camera restoration. No game, Computer Use, native automation, commit or push was run.

