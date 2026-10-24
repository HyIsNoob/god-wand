# Shooting Star Demo 1.4.4: local visual reference analysis

Inspected the supplied JAR, manifest, shader resources and targeted Java bytecode from its nested Minecraft 1.21.1 module. Reference-mod analysis is static; no Shooting Star gameplay or performance comparison was performed. Java names are largely obfuscated, so this does not claim a complete reconstruction of every spell or its runtime scheduling.

## What the archive contains

The outer Fabric JAR is a multi-version container. The relevant module is `META-INF/jars/the-shooting-star-demo-1.4.4-demo+mc1.21.1.jar`; the other modules target 1.20.1, 1.21.11 and 26.3. Its manifest identifies the mod as `1.4.4-demo` and declares All Rights Reserved.

## Why its effects have more depth

- `assets/shooting_star_demo/shaders/fx/stars_world.fsh` reconstructs a world ray and scene position, combines beams, glints, ground radiation and expanding pressure fronts. Independent timing parameters control each layer. Bright foreground lines and a broader colored wake produce a readable shockwave.
- `star_space.fsh` and `star.glsl` handle the space/sky layer, sphere intersections, atmosphere and star fields. This is substantially more than increasing particle count.
- `bloom_down.fsh`, `bloom_up.fsh` and `composite.fsh` separate bloom from final compositing. Composite also exposes shake, aberration, desaturation, zoom blur, flash, vignette, exposure and impact parameters. The resources establish the available layers; they do not establish which are active in every spell.
- `tess_world.fsh` includes scene-depth-aware geometry, local deformation and timed shockwaves. Layered silhouettes and coordinated phase transitions matter as much as brightness.

## What was wrong in God Wand

Supernova explicitly set its screen shockwave radius and distortion to zero. Its replacement sphere compressed most of the fire detail into a smooth red body, leaving little structure. Scene-only bloom and the blue vignette affected the camera view rather than illuminating the new procedural fireball. Surface texture derived from the camera ray's closest point could slide as the view moved. Ground decoration also created lava and many falling entities, adding updates and placing terrain after the blast.

## Applied here

Implemented original plasma detail and three staggered arcs on the actual world-space sphere surface, local corona/glow and two expanding world-space pressure fronts. Increased orbital rotation. Retained the swallowed-ring, shrinking-core, flare, impact and immediate explosion sequence. Removed Supernova's scene-only bloom/vignette. The final implementation now adds a dedicated emission/bloom/composite pipeline for Black Hole and Supernova; the other skills retain their existing rendering path.

Excavation remains a single permanent radius-96 crater. It no longer paints its floor or creates landing debris; the flying display blocks remain. Chunk access is cached per column. No Shooting Star source, texture, sound or shader was copied into the distributed mod.

## Evidence and limits

`gradlew.bat test build --console=plain` and the standalone LWJGL shader checker validate compilation, GL linkage, depth/animation checks, camera translation and pressure fronts. GPU previews are written to `build/previews/aura-nova-*.png`. No Minecraft gameplay, server-tick timing, or FPS measurement was performed. A same-scene before/after gameplay comparison is still needed to judge final visual quality.


## Deeper inspection: confirmed renderer behavior

- `cyou/rimuru/shootingstardemo/client/render/d.class`: the bloom method explicitly uses `bloom_down` in a five-iteration reduction loop, then `bloom_up` over indices 3 down to 0. The bytecode also binds `ExposureSampler`, `AutoExposure`, `SceneLowSampler` and final composition controls. This confirms that the separate rendering resources participate in a renderer, rather than merely sitting unused in the archive. Exact activation of every control still depends on spell state.
- `bloom_down.fsh`: its first-stage prefilter compares the rendered effect scene with the original scene to isolate added light, with alternate handling for synthetic content/alpha masks. This explains why reproducing the color palette alone is insufficient.
- `star_rays.fsh`: light shafts sample toward a projected source in a 40-step loop, with decay, threshold and strength controlled separately for the light source/muzzle.
- `star_gun.fsh`: charge point, corona, arcs, inflow and flash are distinct contributions. Its lightning uses short segments, world/ray distance, depth limits and a width bounded by pixel size. Its inflow uses separate particle lifetimes rather than a single uniformly shrinking ring.
- `star_beam.fsh`: the impact/aftermath sections combine a moving ground front, local surface illumination, cooling and crater-rim heat. The surface-dependent effects help connect the sky weapon to the terrain.
- `stars_world.fsh`: seven node slots each have their own impact age, radius and other state. Some spectacle comes from several coordinated events, not one shader parameter turned up.

## Final God Wand implementation

The transferable techniques are independent phase timing, effect-only emission, reduced-resolution bloom, depth-tested geometry, local optical streaks and ground fronts. They are implemented here with original code and existing God Wand block displays.

Pipeline (`godwand_nova.json`):
1. Render the scene/effect at full resolution.
2. Re-evaluate only the effect's emitted light at one quarter of each image dimension (1/16 pixel count), excluding scene brightness.
3. Blur horizontally at reduced resolution.
4. Blur vertically and add an eight-sample opening light-shaft contribution at reduced resolution.
5. Composite the glow back over the scene using a bounded screen blend.

The buffers are ordinary RGBA8 Minecraft framebuffers, not a full HDR/exposure adaptation system. Shader emission is extracted before the existing scene tone mapping. This avoids camera-wide exposure pumping and limits the extra workload. Reduced targets and their projection matrices are rebuilt correctly for window changes; all processors are released on disconnect and client shutdown.

Phase progression:
- Black hole grows to a larger visual scale, keeps its gold/white/prismatic rim, and pulls its disk fully inside before its core contracts.
- Flare survives after the core reaches zero radius. The blast already exists during the four anime frames.
- The initial white/pink flash has optical streaks, finite world-space radial rays, prismatic fringes and two pressure fronts.
- Radius reaches 68 at tick 24 and 98 at tick 96. The rim and bright arcs cool first; the body breaks into purple/red afterglow rather than staying a bright solid sphere for twelve seconds.
- Subtle local refraction appears in aftermath only. Shader shell radius never oscillates.
- Corona and orbit filaments respect actual scene depth. The ground test caught and removed an earlier ghost ring through foreground terrain.
- Optical centers use the actual view/projection matrices, including dynamic FOV. Supernova camera falloff now reaches 250 blocks; ordinary events retain their 64-block falloff.

## Verification of the final pipeline

- Full Gradle test/build, existing aura/orb GPU regression checks, and new tests for reduced dimensions and Supernova camera range.
- `build/NovaPipelineCheck.java` loads the shipped JSON pass graph, compiles the shaders against the actual vanilla blit vertex shader, and renders all five passes. It checks emission bloom, full wall occlusion, foreground-ground occlusion, camera strafe, odd/tiny/resized windows, black-hole contraction and the post-collapse flare.
- `build/MinecraftPostLoadCheck.java` uses Minecraft 1.21.1's actual `PostEffectProcessor`/`JsonEffectShaderProgram` under a hidden GL context to load every stage and resize its targets. The final run reported no missing-uniform warnings.
- Synthetic GPU timings on RX 5500M, median of three warmed samples at the early blast: about 2.34 ms without the new bloom stages vs 2.59 ms with them at 1280x720; 5.21 ms vs 5.69 ms at 1920x1080. These are effect-render timings only, exclude game simulation, and are not Minecraft FPS or a comparison to Shooting Star.
- Representative previews: `build/previews/nova-bloom.png`, `nova-phase-48.png`, `nova-phase-96.png`, `nova-phase-160.png`, and `nova-black-hole-300.png`. Grid/depth previews are synthetic and omit real terrain detail and flying block entities.

No real-world gameplay/FPS test or final user visual acceptance has been performed. No Shooting Star source or assets are bundled in God Wand.
